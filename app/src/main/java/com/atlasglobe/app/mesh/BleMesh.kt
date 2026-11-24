package com.atlasglobe.app.mesh

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import com.atlasglobe.app.data.ChatRepo
import com.atlasglobe.app.data.Crypto
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * Réseau Bluetooth Low Energy d'Atlas.
 *  - Chaque téléphone ANNONCE le service Atlas et SCRUTE à intervalles (pulsations) pour économiser la batterie.
 *  - Quand un voisin est trouvé, le téléphone au plus petit identifiant ouvre la connexion (évite les doublons).
 *  - Les deux échangent : hello (clé publique) -> inv (paquets connus) -> want (paquets manquants) -> pkt.
 *  - La connexion est courte : elle se ferme après la synchronisation pour libérer la batterie.
 */
@SuppressLint("MissingPermission")
object BleMesh {
    private val SERVICE: UUID = UUID.fromString("a71a5000-6c0b-4f6e-9a0e-0a71a5000001")
    private val RX: UUID = UUID.fromString("a71a5000-6c0b-4f6e-9a0e-0a71a5000002")      // le client écrit ici
    private val TX: UUID = UUID.fromString("a71a5000-6c0b-4f6e-9a0e-0a71a5000003")      // le serveur notifie ici
    private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    private const val MAX_LINKS = 5
    private const val MAX_FRAME = 400_000

    private class Link(val addr: String, val client: Boolean) {
        var device: BluetoothDevice? = null; var gatt: BluetoothGatt? = null
        var rx: BluetoothGattCharacteristic? = null
        var mtu = 23; var ready = false; var busy = false
        var peerId: String? = null
        val queue = ArrayDeque<ByteArray>(); val buf = ByteArrayOutputStream()
    }

    private lateinit var ctx: Context
    private val main = Handler(Looper.getMainLooper())
    private var adapter: BluetoothAdapter? = null
    private var server: BluetoothGattServer? = null
    private var txChar: BluetoothGattCharacteristic? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null
    private val links = HashMap<String, Link>()
    private val lastTried = HashMap<String, Long>()
    private val seenAt = HashMap<String, Long>()
    private var running = false
    private var scanning = false
    private var advMode = -1
    private var myShort = ""
    private var receiverOn = false
    @Volatile private var searchUntil = 0L
    private fun registerBt() { if (!receiverOn) { runCatching { ctx.registerReceiver(btReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)) }; receiverOn = true } }

    private fun shortOf(id: String) = MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).take(4).joinToString("") { "%02x".format(it) }

    // ---------------- démarrage / arrêt ----------------
    fun start(c: Context) {
        if (running) return
        ctx = c.applicationContext
        MeshRouter.init(ctx); MeshPolicy.load(ctx); MeshPolicy.refresh(ctx)
        val mgr = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        adapter = mgr.adapter
        registerBt()
        if (adapter?.isEnabled != true) return
        myShort = shortOf(ChatRepo.myId())
        running = true; MeshStats.running = true
        MeshRouter.announce = { id -> announce(id) }
        openServer(mgr); restartAdvertising(); main.post(scanOn); main.post(tick)
    }

    fun stop() {
        running = false; MeshStats.running = false
        main.removeCallbacksAndMessages(null)
        runCatching { scanner?.stopScan(scanCb) }; runCatching { advertiser?.stopAdvertising(advCb) }
        links.values.toList().forEach { close(it) }; links.clear()
        runCatching { server?.close() }; server = null; scanning = false; advMode = -1
        MeshStats.links = 0; MeshStats.nearby = 0
    }

    /** Recherche rapide de revendeurs : scan continu pendant `ms`, connexion directe aux revendeurs trouvés. */
    fun searchResellers(c: Context, ms: Long) {
        if (!running) start(c)
        if (!running) return
        ResellerDirectory.clear()
        searchUntil = System.currentTimeMillis() + ms
        stopScan(); startScan(MeshMode.ACTIVE)
        main.postDelayed({ if (System.currentTimeMillis() >= searchUntil) stopScan() }, ms + 300)
    }

    /** À appeler quand l'état revendeur change (places libres, activation). */
    fun refreshAdvertising() { if (running) restartAdvertising() }

    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
                BluetoothAdapter.STATE_OFF -> stop()
                BluetoothAdapter.STATE_ON -> start(c)
            }
        }
    }

    // ---------------- rythme (batterie, chaleur) ----------------
    private val tick: Runnable = object : Runnable { override fun run() {
        if (!running) return
        MeshPolicy.refresh(ctx)
        val m = MeshPolicy.effective.ordinal
        if (m != advMode) restartAdvertising()
        val now = System.currentTimeMillis()
        seenAt.values.removeAll { now - it > 120_000 }
        MeshStats.nearby = seenAt.size; MeshStats.links = links.values.count { it.ready }
        if (MeshPolicy.paused && System.currentTimeMillis() >= searchUntil) { stopScan() }
        main.postDelayed(this, 30_000)
    } }

    private val scanOn: Runnable = object : Runnable { override fun run() {
        if (!running) return
        val mode = MeshPolicy.effective
        if (!MeshPolicy.paused) startScan(mode)
        main.postDelayed({ if (System.currentTimeMillis() >= searchUntil) stopScan() }, mode.scanOnMs)
        main.postDelayed(this, mode.scanOnMs + mode.scanOffMs)
    } }

    private fun startScan(mode: MeshMode) {
        if (scanning) return
        scanner = adapter?.bluetoothLeScanner ?: return
        val f = emptyList<ScanFilter>()
        val s = ScanSettings.Builder().setScanMode(when (mode) {
            MeshMode.ACTIVE -> ScanSettings.SCAN_MODE_LOW_LATENCY; MeshMode.BALANCED -> ScanSettings.SCAN_MODE_BALANCED
            MeshMode.ECONOMY -> ScanSettings.SCAN_MODE_LOW_POWER }).build()
        runCatching { scanner?.startScan(f, s, scanCb); scanning = true }
    }
    private fun stopScan() { if (scanning) { runCatching { scanner?.stopScan(scanCb) }; scanning = false } }

    private fun restartAdvertising() {
        runCatching { advertiser?.stopAdvertising(advCb) }
        advertiser = adapter?.bluetoothLeAdvertiser ?: return
        val mode = MeshPolicy.effective; advMode = mode.ordinal
        val s = AdvertiseSettings.Builder().setConnectable(true).setTimeout(0)
            .setAdvertiseMode(when (mode) { MeshMode.ACTIVE -> AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY
                MeshMode.BALANCED -> AdvertiseSettings.ADVERTISE_MODE_BALANCED; MeshMode.ECONOMY -> AdvertiseSettings.ADVERTISE_MODE_LOW_POWER })
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM).build()
        val data = AdvertiseData.Builder().setIncludeDeviceName(false).addServiceUuid(ParcelUuid(SERVICE)).build()
        val resp = AdvertiseData.Builder().setIncludeDeviceName(false)
            .addServiceData(ParcelUuid(SERVICE), myShort.chunked(2).map { it.toInt(16).toByte() }.toByteArray() +
                byteArrayOf(if (ResellerState.active) 1 else 0, ResellerState.slots.coerceIn(0, 100).toByte())).build()
        runCatching { advertiser?.startAdvertising(s, resp, data, advCb) }
    }
    private val advCb = object : AdvertiseCallback() { override fun onStartFailure(errorCode: Int) { main.post { runCatching { android.widget.Toast.makeText(ctx, "Bluetooth emission refusee (code $errorCode)", android.widget.Toast.LENGTH_LONG).show() } } } }

    // ---------------- découverte : le plus petit identifiant se connecte ----------------
    private val scanCb = object : ScanCallback() {
        override fun onScanFailed(errorCode: Int) { main.post { runCatching { android.widget.Toast.makeText(ctx, "Bluetooth recherche refusee (code $errorCode)", android.widget.Toast.LENGTH_LONG).show() } } }
        override fun onScanResult(type: Int, r: ScanResult) {
            val sd = r.scanRecord?.getServiceData(ParcelUuid(SERVICE)) ?: return
            if (sd.size < 4) return
            val peer = sd.take(4).joinToString("") { "%02x".format(it) }
            if (peer == myShort) return
            val isReseller = sd.size >= 6 && sd[4].toInt() == 1
            val addr = r.device.address; val now = System.currentTimeMillis()
            seenAt[peer] = now
            if (isReseller) ResellerDirectory.seen(peer, r.rssi, sd[5].toInt())
            val searching = now < searchUntil && isReseller
            if (!searching && myShort > peer) return                      // c'est l'autre qui se connecte
            if (links.size >= MAX_LINKS || links.containsKey(addr) || now - (lastTried[addr] ?: 0) < (if (searching) 20_000 else 60_000)) return
            lastTried[addr] = now
            connect(r.device)
        }
    }

    private fun connect(d: BluetoothDevice) {
        val l = Link(d.address, true); l.device = d; links[d.address] = l
        l.gatt = d.connectGatt(ctx, false, clientCb, BluetoothDevice.TRANSPORT_LE)
        main.postDelayed({ if (!l.ready) close(l) }, 15_000)               // connexion trop lente : abandon
    }

    private fun close(l: Link) {
        links.remove(l.addr)
        runCatching { l.gatt?.disconnect() }; runCatching { l.gatt?.close() }
        runCatching { if (!l.client) server?.cancelConnection(l.device) }
        MeshStats.links = links.values.count { it.ready }
    }

    // ---------------- client GATT ----------------
    private val clientCb = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, state: Int) {
            main.post {
                if (state == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) { if (!g.requestMtu(247)) g.discoverServices() }
                else { links.remove(g.device.address); runCatching { g.close() }; MeshStats.links = links.values.count { it.ready } }
            }
        }
        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) { main.post { links[g.device.address]?.mtu = mtu; g.discoverServices() } }
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) { main.post {
            val l = links[g.device.address] ?: return@post
            val svc = g.getService(SERVICE) ?: return@post close(l)
            l.rx = svc.getCharacteristic(RX)
            val tx = svc.getCharacteristic(TX) ?: return@post close(l)
            g.setCharacteristicNotification(tx, true)
            val d = tx.getDescriptor(CCCD) ?: return@post close(l)
            @Suppress("DEPRECATION") run { d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE; g.writeDescriptor(d) }
        } }
        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) { main.post {
            val l = links[g.device.address] ?: return@post
            if (status == BluetoothGatt.GATT_SUCCESS) linkReady(l) else close(l) } }
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            val v = c.value ?: return; main.post { links[g.device.address]?.let { onChunk(it, v) } } }
        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            main.post { links[g.device.address]?.let { it.busy = false; it.queue.removeFirstOrNull(); pump(it) } } }
    }

    // ---------------- serveur GATT ----------------
    private fun openServer(mgr: BluetoothManager) {
        server = mgr.openGattServer(ctx, serverCb)
        val svc = BluetoothGattService(SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        svc.addCharacteristic(BluetoothGattCharacteristic(RX, BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_WRITE))
        val tx = BluetoothGattCharacteristic(TX, BluetoothGattCharacteristic.PROPERTY_NOTIFY, BluetoothGattCharacteristic.PERMISSION_READ)
        tx.addDescriptor(BluetoothGattDescriptor(CCCD, BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE))
        svc.addCharacteristic(tx); txChar = tx
        server?.addService(svc)
    }

    private val serverCb = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(d: BluetoothDevice, status: Int, state: Int) { main.post {
            if (state == BluetoothProfile.STATE_CONNECTED) {
                if (!links.containsKey(d.address) && links.size < MAX_LINKS) links[d.address] = Link(d.address, false).also { it.device = d }
                else if (!links.containsKey(d.address)) runCatching { server?.cancelConnection(d) }
            } else links.remove(d.address)?.let { MeshStats.links = links.values.count { it.ready } }
        } }
        override fun onMtuChanged(d: BluetoothDevice, mtu: Int) { main.post { links[d.address]?.mtu = mtu } }
        override fun onDescriptorWriteRequest(d: BluetoothDevice, id: Int, desc: BluetoothGattDescriptor, prepared: Boolean, resp: Boolean, off: Int, value: ByteArray) {
            if (resp) server?.sendResponse(d, id, BluetoothGatt.GATT_SUCCESS, 0, null)
            main.post { links[d.address]?.let { if (!it.ready) linkReady(it) } }
        }
        override fun onCharacteristicWriteRequest(d: BluetoothDevice, id: Int, c: BluetoothGattCharacteristic, prepared: Boolean, resp: Boolean, off: Int, value: ByteArray) {
            if (resp) server?.sendResponse(d, id, BluetoothGatt.GATT_SUCCESS, 0, null)
            main.post { links[d.address]?.let { onChunk(it, value) } }
        }
        override fun onNotificationSent(d: BluetoothDevice, status: Int) {
            main.post { links[d.address]?.let { it.busy = false; it.queue.removeFirstOrNull(); pump(it) } } }
    }

    // ---------------- envoi par morceaux ----------------
    private fun send(l: Link, o: JSONObject) {
        val bytes = o.toString().toByteArray()
        val size = (l.mtu - 4).coerceAtLeast(16)
        var i = 0
        while (i < bytes.size) {
            val end = minOf(i + size, bytes.size)
            l.queue.addLast(byteArrayOf(if (end == bytes.size) 1 else 0) + bytes.copyOfRange(i, end)); i = end
        }
        pump(l)
    }

    @Suppress("DEPRECATION")
    private fun pump(l: Link) {
        if (l.busy || l.queue.isEmpty()) return
        val frame = l.queue.first(); l.busy = true
        val ok = if (l.client) { val rx = l.rx; if (rx == null) false else { rx.value = frame; rx.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT; l.gatt?.writeCharacteristic(rx) == true } }
                 else { txChar?.value = frame; server?.notifyCharacteristicChanged(l.device, txChar, false) == true }
        if (!ok) { l.busy = false; l.queue.clear(); close(l) }
    }

    private fun onChunk(l: Link, v: ByteArray) {
        if (v.isEmpty()) return
        l.buf.write(v, 1, v.size - 1)
        if (l.buf.size() > MAX_FRAME) { l.buf.reset(); close(l); return }
        if (v[0].toInt() == 1) { val s = l.buf.toString(Charsets.UTF_8.name()); l.buf.reset(); runCatching { handle(l, JSONObject(s)) } }
    }

    // ---------------- protocole ----------------
    private fun linkReady(l: Link) {
        l.ready = true; MeshStats.links = links.values.count { it.ready }
        send(l, JSONObject().put("t", "hello").put("id", ChatRepo.myId()).put("pub", Crypto.publicB64()).put("n", com.atlasglobe.app.data.ProfileStore(ctx).load()?.prenom ?: "")
            .apply { if (ResellerState.active) put("rs", JSONObject().put("n", ResellerState.name).put("s", ResellerState.slots)
                .put("r", ResellerState.rating.toDouble()).put("v", ResellerState.speed.toDouble())) })
        send(l, JSONObject().put("t", "inv").put("ids", JSONArray(MeshStore.inventory(ChatRepo.myId(), MeshPolicy.relay))))
        main.postDelayed({ close(l) }, MeshPolicy.effective.sessionMs)    // session courte
    }

    private fun handle(l: Link, o: JSONObject) {
        when (o.getString("t")) {
            "hello" -> { l.peerId = o.getString("id"); seenAt[l.peerId!!] = System.currentTimeMillis()
                MeshRouter.onHello(o.getString("id"), o.getString("pub"), o.optString("n", ""))
                o.optJSONObject("rs")?.let { rs -> ResellerDirectory.profile(shortOf(o.getString("id")), o.getString("id"), rs.optString("n", ""),
                    rs.optInt("s", 0), rs.optDouble("r", 0.0).toFloat(), rs.optDouble("v", 0.0).toFloat()) } }
            "inv" -> { val a = o.getJSONArray("ids")
                val want = (0 until a.length()).map { a.getString(it) }.filter { !MeshStore.has(it) }.take(100)
                if (want.isNotEmpty()) send(l, JSONObject().put("t", "want").put("ids", JSONArray(want))) }
            "want" -> { val a = o.getJSONArray("ids")
                for (i in 0 until minOf(a.length(), 60)) { val p = MeshStore.get(a.getString(i)) ?: continue
                    if (p.ttl <= 0 || (!MeshPolicy.relay && p.src != ChatRepo.myId())) continue
                    send(l, JSONObject().put("t", "pkt").put("p", p.copy(ttl = p.ttl - 1).toJson()))
                    MeshRouter.handedOff(p) } }
            "pkt" -> { val p = Packet.from(o.getJSONObject("p")); MeshRouter.onPacket(p)
                if (MeshPolicy.relay && p.ttl > 0 && p.dst != ChatRepo.myId()) announce(p.id, except = l) }
        }
    }

    /** Un nouveau paquet existe : on le propose tout de suite aux voisins connectés. */
    private fun announce(id: String, except: Link? = null) {
        links.values.filter { it.ready && it !== except }.forEach { send(it, JSONObject().put("t", "inv").put("ids", JSONArray(listOf(id)))) }
    }
}
