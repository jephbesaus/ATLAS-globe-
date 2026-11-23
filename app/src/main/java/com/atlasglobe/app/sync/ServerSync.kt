package com.atlasglobe.app.sync

import android.content.Context
import android.util.Base64
import com.atlasglobe.app.connect.AtlasConfig
import com.atlasglobe.app.data.*
import com.atlasglobe.app.mesh.MeshRouter
import com.atlasglobe.app.mesh.MeshStore
import com.atlasglobe.app.mesh.Packet
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Pont entre le réseau mesh et le serveur Atlas, dès que le téléphone a Internet (même brièvement) :
 *  - inscription (une fois) ; remise des paquets destinés à Atlas Assistant (commandes, notes, rapports) ;
 *  - réception des paquets du serveur (accès payés, annonces, messages, certification…) ;
 *  - envoi des publications, réception du fil, déclaration des groupes (nom et membres, jamais le contenu).
 */
object ServerSync {
    private lateinit var app: Context
    @Volatile private var running = false
    private val uploaded = HashSet<String>()
    private val reported = HashMap<String, Int>()

    fun start(c: Context) {
        if (running) return
        app = c.applicationContext; running = true
        Thread({ while (running) { if (Api.configured() && Net.isOnline(app)) runCatching { cycle() }; try { Thread.sleep(45_000) } catch (e: InterruptedException) { break } } },
            "atlas-sync").also { it.isDaemon = true }.start()
        Thread({ pollLoop() }, "atlas-poll").also { it.isDaemon = true }.start()
    }
    fun stop() { running = false }
    fun syncNow() { if (::app.isInitialized) Thread { runCatching { cycle() } }.start() }

    fun ensureRegistered(c: Context): Boolean { if (!::app.isInitialized) app = c.applicationContext; MeshRouter.init(app); return registered() || register() }

    /** Attente longue : le serveur répond dès qu'un message, un appel ou une annonce attend ce téléphone. */
    private fun pollLoop() {
        while (running) {
            if (!Api.configured() || !Net.isOnline(app) || !registered()) { try { Thread.sleep(5000) } catch (e: InterruptedException) { return }; continue }
            val r = Api.signed("GET", "/v1/poll", null, 35_000)
            if (!r.ok) { try { Thread.sleep(4000) } catch (e: InterruptedException) { return }; continue }
            runCatching {
                MeshRouter.init(app)
                r.json?.optJSONArray("packets")?.let { a -> for (i in 0 until a.length()) runCatching { MeshRouter.onPacket(Packet.from(a.getJSONObject(i))) } }
                r.json?.optJSONArray("events")?.let { a -> for (i in 0 until a.length()) runCatching { com.atlasglobe.app.call.CallManager.onEvent(app, a.getJSONObject(i)) } }
            }
        }
    }

    private fun registered() = app.getSharedPreferences("atlas_server", Context.MODE_PRIVATE).getBoolean("registered", false)

    private fun register(): Boolean {
        val me = ProfileStore(app).load() ?: return false
        val r = Api.signed("POST", "/v1/register", JSONObject().put("pub", Crypto.publicB64()).put("nom", me.nom).put("prenom", me.prenom)
            .put("phone", me.phone).put("continent", me.continent).put("country", me.country).put("province", me.province).put("city", me.city)
            .put("bio", ProfileStore(app).bio))
        if (!r.ok) return false
        r.json?.optString("assistantPub")?.takeIf { it.isNotEmpty() }?.let { AtlasConfig.savePub(app, it) }
        app.getSharedPreferences("atlas_server", Context.MODE_PRIVATE).edit().putBoolean("registered", true).apply()
        return true
    }

    private fun cycle() {
        MeshRouter.init(app); FeedRepo.init(app); AccountState.init(app)
        if (!registered() && !register()) return
        // 1. paquets mesh <-> serveur
        val out = MeshStore.packetsFor(AtlasConfig.ASSISTANT_ID).filter { it.id !in uploaded }.take(100)
        val r = Api.signed("POST", "/v1/sync", JSONObject().put("packets", JSONArray(out.map { it.toJson() })))
        if (r.ok) {
            out.forEach { uploaded.add(it.id) }
            r.json?.optJSONArray("packets")?.let { a -> for (i in 0 until a.length()) runCatching { MeshRouter.onPacket(Packet.from(a.getJSONObject(i))) } }
        } else if (r.code == 404) app.getSharedPreferences("atlas_server", Context.MODE_PRIVATE).edit().putBoolean("registered", false).apply()
        if (AccountState.blocked) return
        // 2. publications
        FeedRepo.unsyncedPosts().forEach { p ->
            val b = JSONObject().put("id", p.id).put("kind", p.kind).put("text", p.text)
            if (p.kind == "video") {                                           // la vidéo part en deux temps : fiche, puis fichier
                val vf = p.mediaPath?.let { File(it) }
                if (vf != null && vf.exists() && Api.post("/v1/posts", b) != null && Api.upload("/v1/video/${p.id}", vf)) FeedRepo.markSynced(p.id)
                return@forEach
            }
            p.mediaPath?.let { m -> File(m).takeIf { it.exists() && it.length() < 380_000 }?.let { b.put("media", Base64.encodeToString(it.readBytes(), Base64.NO_WRAP)) } }
            if (Api.post("/v1/posts", b) != null) FeedRepo.markSynced(p.id)
        }
        FeedRepo.pendingLikeList().forEach { (id, on) -> if (Api.post("/v1/like", JSONObject().put("post", id).put("on", on)) != null) FeedRepo.clearLike(id) }
        pullFeed()
        // 3. groupes
        val me = ChatRepo.myId()
        ChatRepo.groups.filter { ChatRepo.isAdmin(it, me) && reported[it.id] != it.ver }.forEach { g ->
            if (Api.post("/v1/groups", JSONObject().put("id", g.id).put("name", g.name).put("creator", g.creator).put("created", g.createdAt)
                    .put("members", JSONArray(g.members.keys.toList()))) != null) reported[g.id] = g.ver }
    }

    private fun pullFeed() {
        val r = Api.signed("GET", "/v1/feed", null); val arr = r.json?.optJSONArray("posts") ?: return
        val list = ArrayList<Post>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i); val id = o.getString("id")
            var path: String? = null; var url: String? = null
            o.optString("media", "").takeIf { it.isNotEmpty() && it != "null" }?.let { m ->
                if (o.getString("kind") == "video") url = Api.SERVER_URL + "/media/" + m
                else { val f = File(app.filesDir, "media/remote_$m"); if (f.exists() || Api.download("/media/$m", f)) path = f.absolutePath } }
            list.add(Post(id, o.getString("author"), o.optString("author_name"), null, false, o.getString("kind"), o.optString("text"), path,
                o.getLong("created_at"), o.optInt("likes", 0), o.optInt("liked", 0) == 1 || o.optBoolean("liked", false), false, null, true, o.optInt("cert", 0), url))
        }
        FeedRepo.mergeRemote(list)
    }
}
