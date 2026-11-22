package com.atlasglobe.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.atlasglobe.app.notify.NotificationHelper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class Contact(val id: String, val name: String, val phone: String, val bio: String)

/** status : queued (en attente) | sent | relayed | delivered | read.  from = "system" pour les événements du groupe. */
data class Msg(
    val id: String, val conv: String, val from: String, val kind: String,   // text | image | voice | system
    val text: String, val media: String?, val durMs: Long, val replyTo: String?, val t: Long,
    val status: String, val deleted: Boolean, val hidden: Boolean, val reactions: Map<String, String>
)

/** members : id -> "admin" | "member".  suspended : id -> fin de suspension (ms).  banned : exclus définitivement. */
data class Group(
    val id: String, val name: String, val desc: String, val photo: String?, val creator: String,
    val members: Map<String, String>, val locked: Boolean, val suspended: Map<String, Long>,
    val banned: Set<String>, val ver: Int, val createdAt: Long,
    val key: String = ""            // clé AES du groupe (jamais affichée)
)

/** Branché au mesh (étape 6) et à Internet (serveur). Sans transport actif, les messages restent "en attente". */
object Transport {
    var handler: ((Msg) -> Boolean)? = null
    var controlHandler: ((JSONObject) -> Boolean)? = null
    fun send(m: Msg) = handler?.invoke(m) == true
}

object ChatRepo {
    private lateinit var app: Context
    private val file get() = File(app.filesDir, "chats.json")
    var contacts by mutableStateOf<List<Contact>>(emptyList()); private set
    var msgs by mutableStateOf<List<Msg>>(emptyList()); private set
    var groups by mutableStateOf<List<Group>>(emptyList()); private set
    var blocked by mutableStateOf<Set<String>>(emptySet()); private set
    @Volatile var activeConv: String? = null
    private var controls = JSONArray()   // suppressions, signalements et mises à jour de groupe à transmettre

    @Synchronized fun init(ctx: Context) {
        if (::app.isInitialized) return
        app = ctx.applicationContext
        runCatching {
            val j = JSONObject(file.readText())
            contacts = j.getJSONArray("contacts").let { a -> (0 until a.length()).map { i -> a.getJSONObject(i).let { o ->
                Contact(o.getString("id"), o.getString("n"), o.optString("p", ""), o.optString("b", "")) } } }
            msgs = j.getJSONArray("msgs").let { a -> (0 until a.length()).map { i -> a.getJSONObject(i).toMsg() } }
            groups = (j.optJSONArray("groups") ?: JSONArray()).let { a -> (0 until a.length()).map { i -> a.getJSONObject(i).toGroup() } }
            blocked = j.getJSONArray("blocked").let { a -> (0 until a.length()).map { i -> a.getString(i) }.toSet() }
            controls = j.optJSONArray("controls") ?: JSONArray()
        }
    }

    fun myId(): String = ProfileStore(app).load()?.atlasId ?: ""
    fun contact(id: String) = contacts.firstOrNull { it.id == id }
    fun nameOf(id: String) = if (id == myId()) "Vous" else contact(id)?.name ?: id
    fun isGroup(id: String) = id.startsWith("GRP-")
    fun group(id: String) = groups.firstOrNull { it.id == id }
    fun titleOf(conv: String) = if (isGroup(conv)) group(conv)?.name ?: "Groupe" else nameOf(conv)

    fun addContact(rawId: String, name: String, phone: String = ""): Boolean {
        val id = rawId.trim().uppercase()
        if (!Regex("ATL-[A-Z0-9]{4}-[A-Z0-9]{4}").matches(id) || id == myId() || contact(id) != null || name.isBlank()) return false
        contacts = contacts + Contact(id, name.trim(), phone, ""); save(); return true
    }

    /** Contact ajouté par QR : identité et clé authentifiées en face à face. Format : atlas://c?i=ID&k=CLE&n=NOM */
    fun addContactFromQr(raw: String): String? {
        val u = runCatching { android.net.Uri.parse(raw.trim()) }.getOrNull() ?: return "QR invalide"
        if (u.scheme != "atlas" || u.host != "c") return "Ce n'est pas un QR Atlas"
        val id = (u.getQueryParameter("i") ?: "").uppercase(); val key = (u.getQueryParameter("k") ?: "").replace('-', '+').replace('_', '/'); val name = u.getQueryParameter("n") ?: id
        if (!Regex("ATL-[A-Z0-9]{4}-[A-Z0-9]{4}").matches(id) || !Crypto.validKey(key)) return "QR invalide"
        if (id == myId()) return "C'est votre propre QR"
        if (contact(id) == null) { contacts = contacts + Contact(id, name, "", ""); save() }
        com.atlasglobe.app.mesh.MeshStore.forceKey(id, key + "=".repeat((4 - key.length % 4) % 4), name)
        com.atlasglobe.app.mesh.MeshRouter.flushQueued()
        return null
    }

    fun conversation(id: String) = msgs.filter { it.conv == id && !it.hidden }.sortedBy { it.t }
    fun conversations(): List<String> = msgs.filter { !it.hidden }.groupBy { it.conv }
        .entries.sortedByDescending { e -> e.value.maxOf { it.t } }.map { it.key }
    fun unread(conv: String) = msgs.count { it.conv == conv && it.from != myId() && it.from != "system" && it.status != "read" && !it.hidden }

    // ---------------- messages ----------------
    fun send(conv: String, kind: String, text: String, media: String?, durMs: Long = 0, replyTo: String? = null) {
        if (isGroup(conv)) { val g = group(conv) ?: return; if (!canWrite(g, myId())) return }
        var m = Msg(UUID.randomUUID().toString(), conv, myId(), kind, text, media, durMs, replyTo,
            System.currentTimeMillis(), "queued", false, false, emptyMap())
        if (Transport.send(m)) m = m.copy(status = "sent")
        msgs = msgs + m; save()
    }

    /** Appelé par le mesh ou le serveur. Un message de groupe n'est accepté que d'un membre autorisé à écrire. */
    fun receive(m: Msg) {
        if (m.from in blocked || msgs.any { it.id == m.id }) return
        val inGroup = isGroup(m.conv)
        val conv = if (inGroup) { val g = group(m.conv) ?: return; if (!canWrite(g, m.from)) return; m.conv } else m.from
        if (!inGroup && contact(m.from) == null) contacts = contacts + Contact(m.from, m.from, "", "")
        msgs = msgs + m.copy(conv = conv, status = "delivered"); save()
        if (activeConv != conv) NotificationHelper.message(app,
            if (inGroup) "${group(conv)?.name} · ${nameOf(m.from)}" else nameOf(m.from),
            when (m.kind) { "image" -> "Photo"; "voice" -> "Note vocale"; else -> m.text }, conv)
    }

    fun markSentIfQueued(id: String) { if (msgs.any { it.id == id && it.status == "queued" }) setStatus(id, "sent") }
    fun setStatus(id: String, s: String) { msgs = msgs.map { if (it.id == id) it.copy(status = s) else it }; save() }
    fun deleteForMe(id: String) { msgs = msgs.map { if (it.id == id) it.copy(hidden = true) else it }; save() }
    fun canDeleteForAll(m: Msg): Boolean {
        val me = myId(); val g = group(m.conv)
        return m.from == me || (g != null && isAdmin(g, me))      // un admin peut supprimer pour tous dans son groupe
    }
    fun deleteForAll(id: String) {
        val m = msgs.firstOrNull { it.id == id } ?: return
        if (!canDeleteForAll(m)) return
        msgs = msgs.map { if (it.id == id) it.copy(deleted = true, text = "", media = null) else it }
        val to = if (isGroup(m.conv)) (group(m.conv)?.members?.keys ?: emptySet()) - myId() else setOf(m.conv)
        push(JSONObject().put("type", "delete").put("id", id).put("by", myId()).put("t", System.currentTimeMillis())
            .put("to", JSONArray(to.toList()))); save()
    }
    fun queuedMessages() = msgs.filter { it.status == "queued" && it.from == myId() && it.kind != "system" && !it.deleted }
    fun applyRemoteDelete(id: String, by: String) {
        val m = msgs.firstOrNull { it.id == id } ?: return
        val g = group(m.conv)
        if (m.from == by || (g != null && isAdmin(g, by))) {
            msgs = msgs.map { if (it.id == id) it.copy(deleted = true, text = "", media = null) else it }; save() }
    }
    fun react(id: String, emoji: String) {
        val me = myId()
        msgs = msgs.map { if (it.id != id) it else it.copy(reactions =
            if (it.reactions[me] == emoji) it.reactions - me else it.reactions + (me to emoji)) }; save()
    }
    fun markRead(conv: String) {
        msgs = msgs.map { if (it.conv == conv && it.from != myId() && it.from != "system" && it.status != "read") it.copy(status = "read") else it }; save()
    }
    fun block(id: String, on: Boolean) { blocked = if (on) blocked + id else blocked - id; save() }
    fun report(id: String) { push(JSONObject().put("type", "report").put("user", id).put("t", System.currentTimeMillis())); save() }

    // ---------------- groupes ----------------
    fun isAdmin(g: Group, u: String) = g.members[u] == "admin"
    fun suspendedUntil(g: Group, u: String): Long = g.suspended[u]?.takeIf { it > System.currentTimeMillis() } ?: 0L
    fun canWrite(g: Group, u: String) =
        u in g.members && suspendedUntil(g, u) == 0L && (!g.locked || isAdmin(g, u))

    private fun sys(conv: String, text: String) {
        msgs = msgs + Msg(UUID.randomUUID().toString(), conv, "system", "system", text, null, 0, null,
            System.currentTimeMillis(), "read", false, false, emptyMap())
    }
    private fun push(c: JSONObject) { if (Transport.controlHandler?.invoke(c) != true) controls.put(c) }

    private fun commit(old: Group, new: Group, event: String?) {
        val shrunk = old.members.keys.any { it !in new.members }          // retrait ou bannissement : nouvelle clé
        val g = new.copy(ver = old.ver + 1, key = if (shrunk) Crypto.newGroupKey() else old.key)
        groups = groups.map { if (it.id == g.id) g else it }
        if (event != null) sys(g.id, event)
        val to = (old.members.keys + g.members.keys + g.banned) - myId()
        push(JSONObject().put("type", "group").put("by", myId()).put("event", event ?: "").put("group", g.toJson())
            .put("to", JSONArray(to.toList())))
        save()
    }

    fun createGroup(name: String, desc: String, photo: String?, memberIds: List<String>): String {
        val id = "GRP-" + UUID.randomUUID().toString().take(8).uppercase()
        val g = Group(id, name.trim(), desc.trim(), photo, myId(),
            mapOf(myId() to "admin") + memberIds.associateWith { "member" }, false, emptyMap(), emptySet(), 1, System.currentTimeMillis(),
            Crypto.newGroupKey())
        groups = groups + g
        sys(id, "Vous avez créé le groupe « ${g.name} »")
        push(JSONObject().put("type", "group").put("by", myId()).put("event", "Groupe « ${g.name} » créé").put("group", g.toJson())
            .put("to", JSONArray(memberIds)))
        save(); return id
    }

    private inline fun adminAction(gid: String, f: (Group) -> Pair<Group, String?>?) {
        val g = group(gid) ?: return
        if (!isAdmin(g, myId())) return
        val (n, ev) = f(g) ?: return
        commit(g, n, ev)
    }

    fun addMembers(gid: String, ids: List<String>) = adminAction(gid) { g ->
        val add = ids.filter { it !in g.members && it !in g.banned }
        if (add.isEmpty()) null else g.copy(members = g.members + add.associateWith { "member" }) to
            "Vous avez ajouté ${add.joinToString(", ") { nameOf(it) }}"
    }
    fun removeMember(gid: String, u: String, ban: Boolean) = adminAction(gid) { g ->
        if (u == g.creator || u !in g.members) null
        else g.copy(members = g.members - u, suspended = g.suspended - u, banned = if (ban) g.banned + u else g.banned) to
            (if (ban) "Vous avez banni ${nameOf(u)}" else "Vous avez retiré ${nameOf(u)}")
    }
    fun unban(gid: String, u: String) = adminAction(gid) { g -> g.copy(banned = g.banned - u) to "Vous avez autorisé à nouveau ${nameOf(u)}" }
    fun setRole(gid: String, u: String, admin: Boolean) = adminAction(gid) { g ->
        if (u == g.creator || u !in g.members) null
        else g.copy(members = g.members + (u to if (admin) "admin" else "member")) to
            (if (admin) "${nameOf(u)} est maintenant administrateur" else "${nameOf(u)} n'est plus administrateur")
    }
    fun suspend(gid: String, u: String, minutes: Int) = adminAction(gid) { g ->
        if (u == g.creator || u !in g.members) null
        else g.copy(suspended = g.suspended + (u to System.currentTimeMillis() + minutes * 60_000L)) to
            "Vous avez suspendu ${nameOf(u)} pendant ${if (minutes >= 60) "${minutes / 60} h" else "$minutes min"}"
    }
    fun unsuspend(gid: String, u: String) = adminAction(gid) { g -> g.copy(suspended = g.suspended - u) to "Suspension de ${nameOf(u)} levée" }
    fun setLocked(gid: String, on: Boolean) = adminAction(gid) { g ->
        g.copy(locked = on) to (if (on) "Seuls les administrateurs peuvent écrire" else "Tous les membres peuvent écrire") }
    fun editGroup(gid: String, name: String, desc: String, photo: String?) = adminAction(gid) { g ->
        g.copy(name = name.trim().ifEmpty { g.name }, desc = desc.trim(), photo = photo ?: g.photo) to "Le profil du groupe a été modifié" }

    fun leaveGroup(gid: String) {
        val g = group(gid) ?: return; val me = myId()
        var members = g.members - me
        if (members.isNotEmpty() && members.none { it.value == "admin" })      // plus d'admin : le plus ancien membre le devient
            members = members + (members.keys.first() to "admin")
        val n = g.copy(members = members, suspended = g.suspended - me, ver = g.ver + 1)
        groups = groups.map { if (it.id == gid) n else it }
        sys(gid, "Vous avez quitté le groupe")
        push(JSONObject().put("type", "group").put("by", me).put("event", "${nameOf(me)} a quitté le groupe").put("group", n.toJson())
            .put("to", JSONArray((g.members.keys - me).toList())))
        save()
    }

    /** Mise à jour reçue du mesh / serveur : acceptée seulement d'un administrateur connu (ou du créateur pour un nouveau groupe). */
    fun applyGroupUpdate(c: JSONObject, by: String) {
        var inc = c.getJSONObject("group").toGroup()
        val cur = group(inc.id)
        if (cur != null && inc.key.isEmpty()) inc = inc.copy(key = cur.key)
        val ok = if (cur == null) inc.creator == by && by in inc.members
                 else inc.ver > cur.ver && (isAdmin(cur, by) || (by in cur.members && by !in inc.members))
        if (!ok) return
        groups = if (cur == null) groups + inc else groups.map { if (it.id == inc.id) inc else it }
        c.optString("event").takeIf { it.isNotEmpty() }?.let { sys(inc.id, it) }
        if (myId() !in inc.members && cur != null && myId() in cur.members) sys(inc.id, "Vous n'êtes plus dans ce groupe")
        save()
    }

    // ---------------- persistance ----------------
    @Synchronized private fun save() {
        val j = JSONObject()
        j.put("contacts", JSONArray().also { a -> contacts.forEach { a.put(JSONObject().put("id", it.id).put("n", it.name).put("p", it.phone).put("b", it.bio)) } })
        j.put("msgs", JSONArray().also { a -> msgs.forEach { a.put(it.toJson()) } })
        j.put("groups", JSONArray().also { a -> groups.forEach { a.put(it.toJson()) } })
        j.put("blocked", JSONArray(blocked.toList())); j.put("controls", controls)
        runCatching { file.writeText(j.toString()) }
    }
    private fun Msg.toJson() = JSONObject().put("id", id).put("c", conv).put("f", from).put("k", kind).put("tx", text)
        .apply { media?.let { put("m", it) }; replyTo?.let { put("r", it) } }
        .put("d", durMs).put("t", t).put("s", status).put("del", deleted).put("h", hidden)
        .put("re", JSONObject().also { o -> reactions.forEach { (k, v) -> o.put(k, v) } })
    private fun JSONObject.toMsg(): Msg {
        val re = optJSONObject("re"); val map = mutableMapOf<String, String>()
        re?.keys()?.forEach { map[it] = re.getString(it) }
        return Msg(getString("id"), getString("c"), getString("f"), getString("k"), getString("tx"),
            optString("m", "").ifEmpty { null }, getLong("d"), optString("r", "").ifEmpty { null },
            getLong("t"), getString("s"), getBoolean("del"), getBoolean("h"), map)
    }
    private fun Group.toJson() = JSONObject().put("id", id).put("n", name).put("d", desc)
        .apply { photo?.let { put("p", it) } }.put("cr", creator).put("lk", locked).put("v", ver).put("t", createdAt)
        .put("mb", JSONObject().also { o -> members.forEach { (k, v) -> o.put(k, v) } })
        .put("su", JSONObject().also { o -> suspended.forEach { (k, v) -> o.put(k, v) } })
        .put("bn", JSONArray(banned.toList())).put("key", key)
    private fun JSONObject.toGroup(): Group {
        val mb = getJSONObject("mb"); val su = getJSONObject("su"); val bn = getJSONArray("bn")
        return Group(getString("id"), getString("n"), optString("d", ""), optString("p", "").ifEmpty { null }, getString("cr"),
            mb.keys().asSequence().associateWith { mb.getString(it) }, getBoolean("lk"),
            su.keys().asSequence().associateWith { su.getLong(it) }, (0 until bn.length()).map { bn.getString(it) }.toSet(),
            getInt("v"), getLong("t"), optString("key", ""))
    }
}
