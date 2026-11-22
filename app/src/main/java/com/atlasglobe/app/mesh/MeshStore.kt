package com.atlasglobe.app.mesh

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Paquet du réseau mesh. L'en-tête est en clair (pour router), le contenu `body` est chiffré.
 * type : msg | ctl | ack     ttl : sauts restants     sp : clé publique de l'expéditeur (base64)
 */
data class Packet(val id: String, val type: String, val src: String, val dst: String,
                  val ttl: Int, val t: Long, val sp: String, val body: String) {
    fun toJson() = JSONObject().put("i", id).put("ty", type).put("s", src).put("d", dst).put("l", ttl).put("t", t).put("sp", sp).put("b", body)
    companion object { fun from(o: JSONObject) = Packet(o.getString("i"), o.getString("ty"), o.getString("s"), o.getString("d"),
        o.getInt("l"), o.getLong("t"), o.optString("sp", ""), o.getString("b")) }
}

/** Mémoire du réseau : paquets à relayer (stockage et retransmission), clés des voisins rencontrés. */
object MeshStore {
    private const val MAX_AGE = 48L * 3600 * 1000
    private const val MAX_BYTES = 8_000_000L
    private lateinit var file: File
    private val main = Handler(Looper.getMainLooper())
    private val packets = LinkedHashMap<String, Packet>()
    private val seen = HashSet<String>()
    private val consumed = HashSet<String>()
    private val keys = HashMap<String, String>()
    private val names = HashMap<String, String>()
    private val pending = ArrayList<Pair<String, String>>()   // contrôles en attente de la clé du destinataire
    private var dirty = false

    @Synchronized fun init(c: Context) {
        if (::file.isInitialized) return
        file = File(c.applicationContext.filesDir, "mesh.json")
        runCatching {
            val j = JSONObject(file.readText())
            j.getJSONArray("p").let { a -> (0 until a.length()).forEach { val p = Packet.from(a.getJSONObject(it)); packets[p.id] = p } }
            j.getJSONArray("seen").let { a -> (0 until a.length()).forEach { seen.add(a.getString(it)) } }
            j.getJSONArray("cons").let { a -> (0 until a.length()).forEach { consumed.add(a.getString(it)) } }
            j.getJSONObject("keys").let { o -> o.keys().forEach { keys[it] = o.getString(it) } }
            j.optJSONObject("names")?.let { o -> o.keys().forEach { names[it] = o.getString(it) } }
            j.optJSONArray("pend")?.let { a -> (0 until a.length()).forEach { val o = a.getJSONObject(it); pending.add(o.getString("d") to o.getString("c")) } }
        }
        seen.addAll(packets.keys)
    }

    @Synchronized fun add(p: Packet): Boolean {
        if (!seen.add(p.id)) return false
        packets[p.id] = p; trim(); touch(); return true
    }
    @Synchronized fun has(id: String) = seen.contains(id)
    @Synchronized fun get(id: String): Packet? = packets[id]
    @Synchronized fun consume(id: String) { consumed.add(id); touch() }
    @Synchronized fun isConsumed(id: String) = consumed.contains(id)

    /** Paquets que ce téléphone doit encore tenter de lire (clé ou groupe pas encore connus au moment de l'arrivée). */
    @Synchronized fun unconsumed(me: String): List<Packet> =
        packets.values.filter { it.id !in consumed && it.src != me && (it.dst == me || it.dst.startsWith("GRP-")) }

    /** Inventaire proposé aux voisins. Sans relais, seulement nos propres paquets. */
    @Synchronized fun inventory(me: String, relay: Boolean, max: Int = 150): List<String> =
        packets.values.filter { it.ttl > 0 && !(it.dst == me) && (relay || it.src == me) }
            .sortedByDescending { it.t }.take(max).map { it.id }

    @Synchronized fun packetsFor(dst: String): List<Packet> = packets.values.filter { it.dst == dst }.toList()

    @Synchronized fun setKey(id: String, pub: String, name: String): Boolean {
        if (name.isNotEmpty()) names[id] = name
        if (keys.containsKey(id)) return false                  // première clé vue = clé retenue
        keys[id] = pub; touch(); return true
    }
    /** Clé reçue par QR (échange en face à face) : elle remplace une clé apprise au hasard d'une rencontre. */
    @Synchronized fun forceKey(id: String, pub: String, name: String) { keys[id] = pub; if (name.isNotEmpty()) names[id] = name; touch() }
    @Synchronized fun keyOf(id: String): String? = keys[id]
    @Synchronized fun nameOf(id: String): String? = names[id]

    @Synchronized fun addPending(dst: String, payload: String) { pending.add(dst to payload); touch() }
    @Synchronized fun takePending(dst: String): List<String> {
        val m = pending.filter { it.first == dst }.map { it.second }; pending.removeAll { it.first == dst }; if (m.isNotEmpty()) touch(); return m }

    private fun trim() {
        val now = System.currentTimeMillis()
        packets.values.removeAll { now - it.t > MAX_AGE }
        var size = packets.values.sumOf { it.body.length.toLong() }
        val it = packets.values.iterator()
        while (size > MAX_BYTES && it.hasNext()) { val p = it.next(); size -= p.body.length; it.remove() }
    }

    private fun touch() { if (!dirty) { dirty = true; main.postDelayed({ save() }, 5000) } }
    @Synchronized private fun save() {
        dirty = false
        val j = JSONObject()
        j.put("p", JSONArray().also { a -> packets.values.forEach { a.put(it.toJson()) } })
        j.put("seen", JSONArray(seen.toList().takeLast(3000))); j.put("cons", JSONArray(consumed.toList().takeLast(3000)))
        j.put("keys", JSONObject().also { o -> keys.forEach { (k, v) -> o.put(k, v) } })
        j.put("names", JSONObject().also { o -> names.forEach { (k, v) -> o.put(k, v) } })
        j.put("pend", JSONArray().also { a -> pending.forEach { a.put(JSONObject().put("d", it.first).put("c", it.second)) } })
        runCatching { file.writeText(j.toString()) }
    }
}
