package com.atlasglobe.app.mesh

import android.content.Context
import android.util.Base64
import com.atlasglobe.app.data.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Logique du réseau, indépendante du Bluetooth : fabrique les paquets chiffrés, lit ceux qui nous sont destinés,
 * et laisse les autres circuler (stockage et retransmission).
 */
object MeshRouter {
    private lateinit var app: Context
    private const val TTL = 8
    private const val MAX_MEDIA = 220_000L          // au-delà, l'image attend une connexion Internet
    var announce: ((String) -> Unit)? = null        // branché par le Bluetooth : propose un nouveau paquet aux voisins

    @Synchronized fun init(c: Context) {
        if (::app.isInitialized) return
        app = c.applicationContext
        Crypto.init(app); MeshStore.init(app); ChatRepo.init(app)
        com.atlasglobe.app.connect.AtlasConfig.install(app)
        Transport.handler = { m -> outboundMessage(m) }
        Transport.controlHandler = { c2 -> outboundControl(c2) }
        flushQueued()
    }

    private fun me() = ChatRepo.myId()
    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)

    private fun publish(p: Packet) { if (MeshStore.add(p)) announce?.invoke(p.id) }

    // ---------------- envoi ----------------
    /** Toujours false : le statut passe à "Envoyé" quand un voisin prend réellement le paquet. */
    private fun outboundMessage(m: Msg): Boolean {
        if (MeshStore.has(m.id)) return false
        val o = JSONObject().put("id", m.id).put("c", m.conv).put("f", m.from).put("k", m.kind)
            .put("tx", m.text).put("d", m.durMs).put("t", m.t)
        m.replyTo?.let { o.put("r", it) }
        m.media?.let { path -> val f = File(path)
            if (!f.exists() || f.length() > MAX_MEDIA) return false
            o.put("mb", b64(f.readBytes())) }
        val plain = o.toString().toByteArray()
        val key = if (ChatRepo.isGroup(m.conv)) ChatRepo.group(m.conv)?.key?.takeIf { it.isNotEmpty() }?.let { Crypto.keyBytes(it) }
                  else MeshStore.keyOf(m.conv)?.let { Crypto.shared(it) }
        if (key == null) return false                 // clé pas encore connue : réessayé à la prochaine rencontre
        publish(Packet(m.id, "msg", me(), m.conv, TTL, m.t, Crypto.publicB64(), b64(Crypto.seal(key, plain))))
        return false
    }

    fun flushQueued() { ChatRepo.queuedMessages().forEach { outboundMessage(it) } }

    /** Mises à jour de groupe, suppressions : un paquet chiffré par destinataire. */
    private fun outboundControl(c: JSONObject): Boolean {
        val type = c.optString("type")
        if (type == "report") return false            // réservé au serveur
        val to = c.optJSONArray("to") ?: return false
        for (i in 0 until to.length()) sendControl(to.getString(i), c)
        return true
    }

    /** Envoi d'un contrôle chiffré à une personne précise (ex. note du revendeur à Atlas Assistant). */
    fun sendControlTo(dst: String, c: JSONObject) = sendControl(dst, c)

    private fun sendControl(dst: String, c: JSONObject) {
        val payload = JSONObject(c.toString()); payload.remove("to")
        if (payload.optString("type") == "group") {      // la clé du groupe n'est donnée qu'aux membres
            val g = payload.getJSONObject("group")
            if (!g.getJSONObject("mb").has(dst)) g.put("key", "")
        }
        val pub = MeshStore.keyOf(dst)
        if (pub == null) { MeshStore.addPending(dst, payload.toString()); return }
        publish(Packet(UUID.randomUUID().toString(), "ctl", me(), dst, TTL, System.currentTimeMillis(),
            Crypto.publicB64(), b64(Crypto.seal(Crypto.shared(pub), payload.toString().toByteArray()))))
    }

    // ---------------- réception ----------------
    fun onHello(id: String, pub: String, name: String) {
        if (id == me()) return
        MeshStore.setKey(id, pub, name)
        MeshStore.takePending(id).forEach { sendControl(id, JSONObject(it)) }
        flushQueued(); retryUnread()
    }

    fun onPacket(p: Packet) {
        if (!MeshStore.add(p)) return
        consume(p)
    }

    fun retryUnread() { MeshStore.unconsumed(me()).forEach { consume(it) } }

    private fun consume(p: Packet) {
        val me = me()
        when (p.type) {
            "ack" -> if (p.dst == me) {
                runCatching { ChatRepo.setStatus(String(unb64(p.body)), "delivered") }; MeshStore.consume(p.id) }
            "msg" -> when {
                p.src == me -> {}
                p.dst == me -> readDirect(p)
                ChatRepo.isGroup(p.dst) -> readGroup(p)
            }
            "ctl" -> if (p.dst == me) readControl(p)
        }
    }

    private fun peerKey(p: Packet): ByteArray? {
        val known = MeshStore.keyOf(p.src)
        if (known == null && p.sp.isNotEmpty()) MeshStore.setKey(p.src, p.sp, "")       // première rencontre : clé retenue
        return (MeshStore.keyOf(p.src))?.let { Crypto.shared(it) }
    }

    private fun readDirect(p: Packet) {
        val key = peerKey(p) ?: return
        val plain = Crypto.open(key, unb64(p.body)) ?: return
        val m = toMsg(JSONObject(String(plain)), p.id) ?: return
        if (m.from != p.src) return
        MeshStore.consume(p.id)
        ChatRepo.receive(m.copy(conv = m.from))
        publish(Packet(UUID.randomUUID().toString(), "ack", me(), p.src, TTL, System.currentTimeMillis(), "", b64(p.id.toByteArray())))
    }

    private fun readGroup(p: Packet) {
        val g = ChatRepo.group(p.dst) ?: return
        if (g.key.isEmpty() || ChatRepo.myId() !in g.members) return
        val plain = Crypto.open(Crypto.keyBytes(g.key), unb64(p.body)) ?: return
        val m = toMsg(JSONObject(String(plain)), p.id) ?: return
        MeshStore.consume(p.id)
        ChatRepo.receive(m.copy(conv = p.dst))
    }

    private fun readControl(p: Packet) {
        val key = peerKey(p) ?: return
        val plain = Crypto.open(key, unb64(p.body)) ?: return
        val o = JSONObject(String(plain))
        MeshStore.consume(p.id)
        when (o.optString("type")) {
            "group" -> ChatRepo.applyGroupUpdate(o, p.src)
            "delete" -> ChatRepo.applyRemoteDelete(o.getString("id"), p.src)
            "grant" -> if (p.src == com.atlasglobe.app.connect.AtlasConfig.ASSISTANT_ID) {      // seul Atlas Assistant peut accorder un accès
                com.atlasglobe.app.connect.ConnectStore.init(app)
                com.atlasglobe.app.connect.ConnectStore.onGrant(o)
                com.atlasglobe.app.notify.NotificationHelper.connectivity(app, "Paiement approuvé",
                    "Connexion à ${com.atlasglobe.app.connect.ConnectStore.grant?.resellerName ?: "votre revendeur"} en cours.")
                com.atlasglobe.app.connect.ConnectionManager.autoConnect(app) }
            "reseller" -> if (p.src == com.atlasglobe.app.connect.AtlasConfig.ASSISTANT_ID) {
                val ok = o.optBoolean("approved", false)
                com.atlasglobe.app.data.ProfileStore(app).setResellerApprovedFromServer(ok)
                if (!ok) com.atlasglobe.app.reseller.ResellerServer.stop()
                com.atlasglobe.app.notify.NotificationHelper.connectivity(app, "Revendeur Wi-Fi Atlas",
                    if (ok) "Votre compte Revendeur Wi-Fi est approuvé. Ouvrez Paramètres." else "Votre accès Revendeur Wi-Fi a été retiré.") }
            "cert" -> if (p.src == com.atlasglobe.app.connect.AtlasConfig.ASSISTANT_ID) {
                com.atlasglobe.app.settings.SettingsStore.init(app); com.atlasglobe.app.settings.SettingsStore.setCert(o.optInt("level", 0)); com.atlasglobe.app.settings.SettingsStore.setCertNote("")
                com.atlasglobe.app.notify.NotificationHelper.post(app, "Compte certifié", "Votre certification est active.") }
            "announce" -> if (p.src == com.atlasglobe.app.connect.AtlasConfig.ASSISTANT_ID)
                com.atlasglobe.app.notify.NotificationHelper.announcement(app, Announcement(o.optString("id", UUID.randomUUID().toString()),
                    o.optString("title"), o.optString("body"), o.optString("whatsapp"), o.optString("telegram"), o.optString("support")))
            "blocked" -> if (p.src == com.atlasglobe.app.connect.AtlasConfig.ASSISTANT_ID) AccountState.set(app, o.optBoolean("on"), o.optString("reason"))
            "cert_rejected" -> if (p.src == com.atlasglobe.app.connect.AtlasConfig.ASSISTANT_ID) {
                com.atlasglobe.app.settings.SettingsStore.init(app); com.atlasglobe.app.settings.SettingsStore.setCertNote("rejected:" + o.optString("reason"))
                com.atlasglobe.app.notify.NotificationHelper.post(app, "Certification refusée", o.optString("reason")) }
            "join" -> com.atlasglobe.app.reseller.ResellerServer.onJoin(p.src, o)
            "wifi" -> com.atlasglobe.app.connect.ConnectionManager.onWifi(p.src, o)
            "wifi_err" -> com.atlasglobe.app.connect.ConnectionManager.onWifiError(o.optInt("code", 0))
            "refused" -> if (p.src == com.atlasglobe.app.connect.AtlasConfig.ASSISTANT_ID) {
                com.atlasglobe.app.connect.ConnectStore.init(app); com.atlasglobe.app.connect.ConnectStore.onRefused(o.getString("orderId")) }
        }
        if (o.optString("type") == "group") retryUnread()
    }

    private fun toMsg(o: JSONObject, id: String): Msg? = try {
        var path: String? = null
        val kind = o.getString("k")
        if (o.has("mb")) {
            val f = File(app.filesDir, "media/$id.${if (kind == "voice") "m4a" else "jpg"}").also { it.parentFile?.mkdirs() }
            f.writeBytes(unb64(o.getString("mb"))); path = f.absolutePath
        }
        Msg(o.getString("id"), o.getString("c"), o.getString("f"), kind, o.optString("tx", ""), path,
            o.optLong("d", 0), o.optString("r", "").ifEmpty { null }, o.getLong("t"), "delivered", false, false, emptyMap())
    } catch (e: Exception) { null }

    /** Un voisin a pris notre paquet : le message passe de "En attente" à "Envoyé". */
    fun handedOff(p: Packet) { if (p.type == "msg" && p.src == me()) ChatRepo.markSentIfQueued(p.id) }
}
