package com.atlasglobe.app.reseller

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.atlasglobe.app.connect.AtlasConfig
import com.atlasglobe.app.connect.TokenCodec
import com.atlasglobe.app.data.ChatRepo
import com.atlasglobe.app.data.ProfileStore
import com.atlasglobe.app.mesh.BleMesh
import com.atlasglobe.app.mesh.MeshPolicy
import com.atlasglobe.app.mesh.MeshRouter
import com.atlasglobe.app.mesh.ResellerState
import com.atlasglobe.app.notify.NotificationHelper
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/**
 * Côté REVENDEUR (compte approuvé par le panel admin).
 *  - Ouvre un point d'accès Wi-Fi LOCAL (sans Internet pour qui connaît le mot de passe).
 *  - Un petit serveur accepte seulement les clients qui présentent une session obtenue avec un jeton signé valide,
 *    et fait passer leur trafic par les données mobiles du revendeur.
 *  - Suit le temps et le volume PAR client, et coupe uniquement le client dont le temps est fini.
 */
object ResellerServer {
    const val PORT = 18080
    private lateinit var app: Context
    private val main = Handler(Looper.getMainLooper())
    var running by mutableStateOf(false); private set
    var status by mutableStateOf(""); private set
    var clients by mutableStateOf<List<ClientRow>>(emptyList()); private set
    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null
    private var ssid = ""; private var pass = ""
    private var server: ServerSocket? = null
    private val rnd = SecureRandom()
    private val sessions = ConcurrentHashMap<String, String>()          // session secrète -> tid
    private val usages = ConcurrentHashMap<String, Usage>()             // tid -> consommation
    private var lastReport = 0L; private var warnedLow = false
    private val relayedBytesAcc = java.util.concurrent.atomic.AtomicLong(0)

    data class ClientRow(val tid: String, val user: String, val plan: String, val remainingMs: Long, val usedBytes: Long)

    private class Usage(val tid: String, val user: String, val plan: String, val durMs: Long, val mbLimit: Int, var firstUse: Long) {
        val bytes = AtomicLong(0); @Volatile var lastSeen = 0L
        val sockets: MutableSet<Socket> = ConcurrentHashMap.newKeySet()
        fun expired(): Boolean = (SystemClock.elapsedRealtime() - firstUse >= durMs) || (mbLimit > 0 && bytes.get() >= mbLimit * 1_000_000L)
        fun active(): Boolean = sockets.isNotEmpty() || SystemClock.elapsedRealtime() - lastSeen < 60_000
    }

    // ---------------- ouverture / fermeture ----------------
    @SuppressLint("MissingPermission")
    fun start(c: Context) {
        if (running) return
        app = c.applicationContext
        if (!ProfileStore(app).resellerApproved) { status = "Compte revendeur non approuvé"; return }
        status = "Ouverture du point d'accès…"
        ssid = "Atlas-" + (ProfileStore(app).load()?.prenom ?: "Revendeur")
        pass = java.security.MessageDigest.getInstance("SHA-256").digest(("atlas-ag-v1|" + ssid).toByteArray()).joinToString("") { "%02x".format(it) }.take(10)
        try { app.applicationContext.startActivity(android.content.Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {}
        openServer()
        status = "Activez le partage de connexion Android. Nom : $ssid. Mot de passe : $pass"
        return
        val wm = app.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        try {
            wm.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                @Suppress("DEPRECATION")
                override fun onStarted(r: WifiManager.LocalOnlyHotspotReservation) {
                    reservation = r
                    if (Build.VERSION.SDK_INT >= 33) { ssid = r.softApConfiguration.wifiSsid?.toString()?.trim('"') ?: ""; pass = r.softApConfiguration.passphrase ?: "" }
                    else if (Build.VERSION.SDK_INT >= 30) { ssid = r.softApConfiguration.ssid?.trim('"') ?: ""; pass = r.softApConfiguration.passphrase ?: "" }
                    else { ssid = r.wifiConfiguration?.SSID?.trim('"') ?: ""; pass = r.wifiConfiguration?.preSharedKey?.trim('"') ?: "" }
                    if (ssid.isEmpty() || pass.isEmpty()) { status = "Point d'accès invalide"; stop(); return }
                    openServer()
                }
                override fun onFailed(reason: Int) { status = "Impossible d'ouvrir le point d'accès. (code $reason) Activez la localisation et coupez le partage de connexion Android."; running = false }
                override fun onStopped() { if (running) stop() }
            }, main)
        } catch (e: Exception) { status = "Autorisations Wi-Fi manquantes ou point d'accès indisponible" }
    }

    private fun openServer() {
        val ss = try { ServerSocket(PORT, 50) } catch (e: Exception) { status = "Port occupé"; stop(); return }
        server = ss; running = true; status = "Connexion ouverte"
        ResellerState.name = "Atlas-" + (ProfileStore(app).load()?.prenom ?: "Revendeur"); ResellerState.slots = 5; ResellerState.active = true
        BleMesh.refreshAdvertising()
        thread(isDaemon = true, name = "atlas-reseller") { while (running) { val s = try { ss.accept() } catch (e: Exception) { break }; thread(isDaemon = true) { client(s) } } }
        main.post(tick)
    }

    fun stop() {
        running = false; ResellerState.active = false; status = ""
        runCatching { server?.close() }; server = null
        usages.values.forEach { u -> u.sockets.forEach { runCatching { it.close() } } }
        sessions.clear(); runCatching { reservation?.close() }; reservation = null; clients = emptyList()
        main.removeCallbacks(tick); BleMesh.refreshAdvertising()
    }

    // ---------------- demande "join" reçue par le mesh ----------------
    fun onJoin(src: String, o: JSONObject) {
        fun err(code: Int) = MeshRouter.sendControlTo(src, JSONObject().put("type", "wifi_err").put("code", code))
        if (!running) return err(1)
        val t = TokenCodec.parse(o.optString("token")) ?: return err(2)
        if (t.user != src) return err(2)                                   // le jeton appartient à celui qui le présente
        val dur = if (t.durMs > 0) t.durMs else 30L * 24 * 3600 * 1000
        val u = usages.getOrPut(t.tid) {
            val claimed = o.optLong("usedMs", 0).coerceIn(0, dur)
            Usage(t.tid, t.user, t.plan, dur, t.mb, SystemClock.elapsedRealtime() - claimed) }
        if (u.expired()) return err(4)
        if (!u.active() && usages.values.count { it.active() } >= ResellerState.slots) return err(3)
        u.lastSeen = SystemClock.elapsedRealtime()
        val secret = ByteArray(16).also { rnd.nextBytes(it) }.joinToString("") { "%02x".format(it) }
        sessions.entries.removeAll { it.value == t.tid }; sessions[secret] = t.tid
        MeshRouter.sendControlTo(src, JSONObject().put("type", "wifi").put("ssid", ssid).put("pass", pass)
            .put("port", PORT).put("session", secret).put("rname", ResellerState.name))
    }

    // ---------------- flux des clients ----------------
    private fun readLine(i: InputStream): String? {
        val sb = StringBuilder()
        while (sb.length < 2048) { val b = i.read(); if (b < 0) return null; if (b == '\n'.code) return sb.toString(); sb.append(b.toChar()) }
        return null
    }

    private fun allowed(host: String, port: Int): Boolean = try {
        val a = InetAddress.getByName(host)
        port != 25 && !(a.isAnyLocalAddress || a.isLoopbackAddress || a.isLinkLocalAddress || a.isSiteLocalAddress || a.isMulticastAddress)
    } catch (e: Exception) { false }

    private fun client(c: Socket) {
        var out: Socket? = null; var u: Usage? = null
        try {
            c.soTimeout = 8000; c.tcpNoDelay = true; c.receiveBufferSize = 262144; c.sendBufferSize = 262144
            val line = readLine(c.getInputStream()) ?: return c.close()
            if (!line.startsWith("ATL1 ")) return c.close()
            val h = JSONObject(String(android.util.Base64.decode(line.substring(5), android.util.Base64.NO_WRAP)))
            val tid = sessions[h.getString("s")]; u = tid?.let { usages[it] }
            if (u == null) { c.getOutputStream().write(1); return c.close() }
            if (u.expired()) { c.getOutputStream().write(2); return c.close() }
            val host = h.getString("h"); val port = h.getInt("p")
            if (!allowed(host, port)) { c.getOutputStream().write(3); return c.close() }
            out = if (com.atlasglobe.app.connect.ConnectStore.relayAccepted && com.atlasglobe.app.connect.ConnectionManager.state == com.atlasglobe.app.connect.ConnectionManager.State.CONNECTED)
                com.atlasglobe.app.connect.ConnectionManager.dial(host, port) { s2 -> runCatching { app.getSystemService(Context.CONNECTIVITY_SERVICE) } }
                    ?: run { c.getOutputStream().write(5); return c.close() }
            else Socket().also { it.receiveBufferSize = 262144; it.sendBufferSize = 262144; it.connect(InetSocketAddress(host, port), 8000); it.tcpNoDelay = true }
            c.getOutputStream().write(0); c.getOutputStream().flush(); c.soTimeout = 0
            u.sockets.add(c); u.sockets.add(out); u.lastSeen = SystemClock.elapsedRealtime()
            val a = out; val usage = u
            val t = thread(isDaemon = true) { pipe(a.getInputStream(), c.getOutputStream(), usage, if (viaRelay) relayCounter else null) }
            pipe(c.getInputStream(), a.getOutputStream(), usage, if (viaRelay) relayCounter else null)
            t.join(2000)
            if (viaRelay) relayedBytesAcc.addAndGet(relayCounter.get())
        } catch (e: Exception) { runCatching { c.getOutputStream().write(4) } }
        finally { runCatching { c.close() }; runCatching { out?.close() }; u?.sockets?.remove(c); out?.let { u?.sockets?.remove(it) } }
    }

    private fun pipe(i: InputStream, o: java.io.OutputStream, u: Usage, extra: java.util.concurrent.atomic.AtomicLong? = null) {
        val buf = ByteArray(65536)
        try { while (true) { val n = i.read(buf); if (n < 0) break
            if (u.expired()) break                                           // temps fini : seul ce client est coupé
            o.write(buf, 0, n); o.flush(); u.bytes.addAndGet(n.toLong()); u.lastSeen = SystemClock.elapsedRealtime()
            extra?.addAndGet(n.toLong()) } } catch (e: Exception) { }
        runCatching { o.flush() }
    }

    // ---------------- surveillance : places, chaleur, batterie, rapports ----------------
    private val tick: Runnable = object : Runnable { override fun run() {
        if (!running) return
        MeshPolicy.refresh(app)
        val now = SystemClock.elapsedRealtime()
        usages.values.forEach { u -> if (u.expired()) u.sockets.toList().forEach { runCatching { it.close() } } }
        clients = usages.values.filter { it.active() }.map { ClientRow(it.tid, it.user, it.plan,
            (it.durMs - (now - it.firstUse)).coerceAtLeast(0), it.bytes.get()) }

        val t = MeshPolicy.tempC; val low = MeshPolicy.batteryPct < 10 && !MeshPolicy.charging
        val slots = when { t >= 43f || low -> 0; t >= 40f || ResellerState.mobile -> 3; else -> 5 }          // chaleur : moins de clients
        if (slots != ResellerState.slots) { ResellerState.slots = slots; BleMesh.refreshAdvertising() }
        if (MeshPolicy.batteryPct < 20 && !MeshPolicy.charging && !warnedLow) { warnedLow = true
            NotificationHelper.connectivity(app, "Batterie faible", "Branchez votre power bank pour garder vos clients connectés.") }
        if (MeshPolicy.batteryPct >= 30 || MeshPolicy.charging) warnedLow = false
        if (t >= 45f) { status = "Téléphone trop chaud : partage en pause"; stop(); return }

        if (System.currentTimeMillis() - lastReport > 300_000 && usages.isNotEmpty()) { lastReport = System.currentTimeMillis()
            val a = JSONArray(); usages.values.forEach { a.put(JSONObject().put("tid", it.tid).put("bytes", it.bytes.get()).put("usedMs", now - it.firstUse)) }
            MeshRouter.sendControlTo(AtlasConfig.ASSISTANT_ID, JSONObject().put("type", "usage").put("list", a)) }
        val THRESHOLD = 50_000_000L; val MINUTES_PER_THRESHOLD = 10
        while (relayedBytesAcc.get() >= THRESHOLD) {
            relayedBytesAcc.addAndGet(-THRESHOLD)
            com.atlasglobe.app.connect.ConnectStore.creditRelayMinutes(MINUTES_PER_THRESHOLD)
        }
        main.postDelayed(this, 5000)
    } }
}
