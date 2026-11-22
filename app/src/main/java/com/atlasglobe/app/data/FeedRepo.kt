package com.atlasglobe.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Publications, stories, abonnements : enregistrés sur le téléphone (visibles hors ligne).
 * Les éléments "synced = false" attendent la synchronisation serveur (quand Internet revient).
 */
object FeedRepo {
    private const val DAY = 24L * 3600 * 1000
    private lateinit var app: Context
    private val file get() = File(app.filesDir, "feed.json")

    var posts by mutableStateOf<List<Post>>(emptyList()); private set
    var stories by mutableStateOf<List<Story>>(emptyList()); private set
    var following by mutableStateOf<Set<String>>(emptySet()); private set
    private var pendingLikes = JSONObject()   // id -> état du like à envoyer au serveur
    private var replies = JSONArray()   // réponses aux stories, livrées par la messagerie (étape 5)

    const val OFFICIAL_ID = "atlas-official"

    @Synchronized fun init(ctx: Context) {
        if (::app.isInitialized) return
        app = ctx.applicationContext
        load()
        if (posts.none { it.id == "official-welcome" }) {
            posts = posts + Post("official-welcome", OFFICIAL_ID, "Atlas Globe", null, true, "text",
                "Bienvenue sur Atlas Globe. Discutez avec vos voisins sans Internet, retrouvez des points de connexion près de vous et restez en contact avec votre quartier.",
                null, System.currentTimeMillis(), 0, false, false, null, true)
        }
        stories = stories.filter { System.currentTimeMillis() - it.createdAt < DAY }
        save()
    }

    private fun me(): Profile? = ProfileStore(app).load()

    fun addPost(kind: String, text: String, media: String?) {
        val m = me() ?: return
        val p = Post(UUID.randomUUID().toString(), m.atlasId, "${m.prenom} ${m.nom}".trim(), m.photoPath, false,
            kind, text, media, System.currentTimeMillis(), 0, false, false, null, false)
        posts = listOf(p) + posts; save()
    }

    fun addStory(media: String) {
        val m = me() ?: return
        stories = stories + Story(UUID.randomUUID().toString(), m.atlasId, "${m.prenom} ${m.nom}".trim(),
            m.photoPath, media, System.currentTimeMillis(), false, true, false); save()
    }

    fun unsyncedPosts(): List<Post> { val me = ProfileStore(app).load()?.atlasId ?: return emptyList(); return posts.filter { !it.synced && it.authorId == me && it.id != "official-welcome" } }
    fun markSynced(id: String) { posts = posts.map { if (it.id == id) it.copy(synced = true) else it }; save() }

    /** Fusionne le fil du serveur : les publications supprimées par l'administration disparaissent aussi du téléphone. */
    fun mergeRemote(remote: List<Post>) {
        val me = ProfileStore(app).load()?.atlasId ?: return
        val ids = remote.associateBy { it.id }
        val mine = posts.filter { it.authorId == me || it.id == "official-welcome" }
            .filter { !(it.synced && it.authorId == me && remote.size < 100 && it.id !in ids) }
            .map { m -> val r = ids[m.id]; if (r != null && !pendingLikes.has(m.id)) m.copy(likes = r.likes) else m }
        val local = posts.associateBy { it.id }
        val others = remote.filter { it.authorId != me }.map { r -> val l = local[r.id]; val pend = pendingLikes.has(r.id) && l != null
            r.copy(saved = l?.saved ?: false, likes = if (pend) l!!.likes else r.likes, likedByMe = if (pend) l!!.likedByMe else r.likedByMe) }
        posts = (mine + others).sortedByDescending { it.createdAt }; save()
    }

    fun deletePost(id: String) { posts = posts.filter { it.id != id }; save() }
    fun deleteStory(id: String) { stories = stories.filter { it.id != id }; save() }

    fun toggleLike(id: String) {
        val p = posts.firstOrNull { it.id == id } ?: return
        val on = !p.likedByMe
        posts = posts.map { if (it.id == id) it.copy(likedByMe = on, likes = (it.likes + if (on) 1 else -1).coerceAtLeast(0)) else it }
        pendingLikes.put(id, on); save()
    }
    fun pendingLikeList(): List<Pair<String, Boolean>> = pendingLikes.keys().asSequence().map { it to pendingLikes.getBoolean(it) }.toList()
    fun clearLike(id: String) { pendingLikes.remove(id); save() }

    fun toggleSave(id: String) { posts = posts.map { if (it.id == id) it.copy(saved = !it.saved) else it }; save() }

    fun repost(id: String) {
        val o = posts.firstOrNull { it.id == id } ?: return
        val m = me() ?: return
        posts = listOf(Post(UUID.randomUUID().toString(), m.atlasId, "${m.prenom} ${m.nom}".trim(), m.photoPath, false,
            o.kind, o.text, o.mediaPath, System.currentTimeMillis(), 0, false, false, o.authorName, false)) + posts
        save()
    }

    fun toggleFollow(authorId: String) {
        following = if (authorId in following) following - authorId else following + authorId; save()
    }

    fun likeStory(id: String) { stories = stories.map { if (it.id == id) it.copy(likedByMe = !it.likedByMe, synced = false) else it }; save() }
    fun markViewed(id: String) { stories = stories.map { if (it.id == id && !it.viewed) it.copy(viewed = true) else it }; save() }

    fun replyToStory(s: Story, text: String) {
        replies.put(JSONObject().put("storyId", s.id).put("toId", s.authorId).put("toName", s.authorName)
            .put("text", text).put("t", System.currentTimeMillis())); save()
    }

    // ---------------- persistance ----------------
    private fun load() {
        runCatching {
            val j = JSONObject(file.readText())
            posts = j.getJSONArray("posts").let { a -> (0 until a.length()).map { i -> a.getJSONObject(i).toPost() } }
            stories = j.getJSONArray("stories").let { a -> (0 until a.length()).map { i -> a.getJSONObject(i).toStory() } }
            following = j.getJSONArray("following").let { a -> (0 until a.length()).map { i -> a.getString(i) }.toSet() }
            replies = j.optJSONArray("replies") ?: JSONArray()
            pendingLikes = j.optJSONObject("pl") ?: JSONObject()
        }
    }

    @Synchronized private fun save() {
        val j = JSONObject()
        j.put("posts", JSONArray().also { a -> posts.forEach { a.put(it.toJson()) } })
        j.put("stories", JSONArray().also { a -> stories.forEach { a.put(it.toJson()) } })
        j.put("following", JSONArray(following.toList()))
        j.put("replies", replies); j.put("pl", pendingLikes)
        runCatching { file.writeText(j.toString()) }
    }

    private fun Post.toJson() = JSONObject().put("id", id).put("aid", authorId).put("an", authorName)
        .apply { authorPhoto?.let { put("ap", it) } }.put("off", official).put("k", kind).put("tx", text)
        .apply { mediaPath?.let { put("m", it) } }.put("t", createdAt).put("l", likes).put("lm", likedByMe)
        .put("sv", saved).apply { repostFrom?.let { put("rp", it) } }.put("sy", synced).put("ct", cert)
    private fun JSONObject.toPost() = Post(getString("id"), getString("aid"), getString("an"),
        optString("ap", "").ifEmpty { null }, getBoolean("off"), getString("k"), getString("tx"),
        optString("m", "").ifEmpty { null }, getLong("t"), getInt("l"), getBoolean("lm"), getBoolean("sv"),
        optString("rp", "").ifEmpty { null }, getBoolean("sy"), optInt("ct", 0))
    private fun Story.toJson() = JSONObject().put("id", id).put("aid", authorId).put("an", authorName)
        .apply { authorPhoto?.let { put("ap", it) } }.put("m", mediaPath).put("t", createdAt)
        .put("lm", likedByMe).put("vw", viewed).put("sy", synced)
    private fun JSONObject.toStory() = Story(getString("id"), getString("aid"), getString("an"),
        optString("ap", "").ifEmpty { null }, getString("m"), getLong("t"), getBoolean("lm"), getBoolean("vw"), getBoolean("sy"))
}
