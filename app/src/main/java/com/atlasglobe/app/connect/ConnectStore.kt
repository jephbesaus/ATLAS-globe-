package com.atlasglobe.app.connect

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.atlasglobe.app.data.ChatRepo
import com.atlasglobe.app.mesh.MeshRouter
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Commande : awaiting (en attente de paiement) -> approved (jeton reçu) -> active -> ended / refused. */
data class Order(val id: String, val planId: String, val resellerId: String, val resellerName: String,
                 val amountFc: Int, val createdAt: Long, val status: String, val rated: Int)

/** Accès acheté. Le jeton est signé par Atlas : il suit l'utilisateur d'un revendeur à l'autre. */
data class Grant(val orderId: String, val token: String, val resellerId: String, val resellerName: String,
                 val planId: String, val durationMs: Long, val megabytes: Int, val activatedAtWall: Long, val activatedAtElapsed: Long)

object ConnectStore {
    var relayAccepted by mutableStateOf(false); private set
    var bonusGiven by mutableStateOf(false); private set

    fun acceptRelay() { relayAccepted = true; save() }

    /** Crédité en continu selon la contribution réelle (voir ResellerServer). Ne dépend plus du bonus unique. */
    fun creditRelayMinutes(min: Int) {
        val g = grant ?: return
        if (!isActive) return
        grant = g.copy(durationMs = g.durationMs + min * 60_000L)
        save(); scheduleAlarms()
    }

    /** +30 min une seule fois, si le relais a été accepté et le bonus pas encore donné. */
    fun giveBonusIfEligible(): Boolean {
        if (!relayAccepted || bonusGiven) return false
        val g = grant ?: return false
        grant = g.copy(durationMs = g.durationMs + 30 * 60_000L)
        bonusGiven = true; save(); scheduleAlarms()
        return true
    }
    private lateinit var app: Context
    private val file get() = File(app.filesDir, "connect.json")
    var orders by mutableStateOf<List<Order>>(emptyList()); private set
    var grant by mutableStateOf<Grant?>(null); private set

    @Synchronized fun init(c: Context) {
        if (::app.isInitialized) return
        app = c.applicationContext
        runCatching {
            val j = JSONObject(file.readText())
            orders = j.getJSONArray("orders").let { a -> (0 until a.length()).map { i -> a.getJSONObject(i).let { o ->
                Order(o.getString("id"), o.getString("p"), o.getString("r"), o.getString("rn"), o.getInt("a"), o.getLong("t"), o.getString("s"), o.getInt("st")) } } }
            j.optJSONObject("grant")?.let { o -> grant = Grant(o.getString("o"), o.getString("k"), o.getString("r"), o.getString("rn"),
                o.getString("p"), o.getLong("d"), o.getInt("mb"), o.getLong("aw"), o.getLong("ae")) }
            relayAccepted = j.optBoolean("relay", false); bonusGiven = j.optBoolean("bonus", false)
        }
    }

    fun order(id: String) = orders.firstOrNull { it.id == id }
    fun lastEndedUnrated() = orders.filter { it.status == "ended" && it.rated == 0 }.maxByOrNull { it.createdAt }

    fun createOrder(plan: Plan, resellerId: String, resellerName: String): Order {
        val o = Order("CMD-" + UUID.randomUUID().toString().take(6).uppercase(), plan.id, resellerId, resellerName,
            plan.priceFc, System.currentTimeMillis(), "awaiting", 0)
        orders = listOf(o) + orders; save(); return o
    }

    /** Reçu d'Atlas Assistant (paquet chiffré et authentifié) après approbation du paiement. */
    fun onGrant(o: JSONObject) {
        val id = o.getString("orderId"); val cur = order(id) ?: return
        val plan = Tariffs.find(cur.planId) ?: return
        orders = orders.map { if (it.id == id) it.copy(status = "approved") else it }
        grant = Grant(id, o.getString("token"), cur.resellerId, cur.resellerName, plan.id,
            if (plan.minutes > 0) plan.minutes * 60_000L else 30L * 24 * 3600 * 1000, plan.megabytes, 0L, 0L)
        save()
    }

    fun onRefused(id: String) { orders = orders.map { if (it.id == id) it.copy(status = "refused") else it }; save() }

    /** Appelé quand la connexion au revendeur réussit : le chronomètre démarre. */
    fun activate() {
        val g = grant ?: return
        if (g.activatedAtWall != 0L) return
        grant = g.copy(activatedAtWall = System.currentTimeMillis(), activatedAtElapsed = SystemClock.elapsedRealtime())
        orders = orders.map { if (it.id == g.orderId) it.copy(status = "active") else it }
        save(); scheduleAlarms()
    }

    /** Temps restant. Basé sur l'horloge interne du téléphone, pas sur l'heure réglable, pour éviter la triche. */
    fun remainingMs(): Long {
        val g = grant ?: return 0
        if (g.activatedAtWall == 0L) return g.durationMs
        return (g.durationMs - (SystemClock.elapsedRealtime() - g.activatedAtElapsed)).coerceAtLeast(0)
    }
    fun usedMs(): Long { val g = grant ?: return 0; return if (g.activatedAtWall == 0L) 0 else SystemClock.elapsedRealtime() - g.activatedAtElapsed }
    val isActive: Boolean get() = grant?.let { it.activatedAtWall != 0L && remainingMs() > 0 } == true

    fun end() {
        val g = grant ?: return
        orders = orders.map { if (it.id == g.orderId) it.copy(status = "ended") else it }
        grant = null; save()
    }

    /** Une seule note par achat payé. */
    fun rate(orderId: String, stars: Int) {
        val o = order(orderId) ?: return
        if (o.rated != 0) return
        orders = orders.map { if (it.id == orderId) it.copy(rated = stars) else it }; save()
        MeshRouter.sendControlTo(AtlasConfig.ASSISTANT_ID, JSONObject().put("type", "rate").put("order", orderId)
            .put("reseller", o.resellerId).put("stars", stars))
    }

    private fun scheduleAlarms() {
        val g = grant ?: return
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val end = System.currentTimeMillis() + remainingMs()
        fun pi(kind: String, code: Int) = PendingIntent.getBroadcast(app, code,
            Intent(app, ExpiryReceiver::class.java).putExtra("kind", kind).putExtra("order", g.orderId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (remainingMs() > 5 * 60_000L) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end - 5 * 60_000L, pi("soon", 11))
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, pi("end", 12))
    }

    private fun save() {
        val j = JSONObject()
        j.put("orders", JSONArray().also { a -> orders.forEach { a.put(JSONObject().put("id", it.id).put("p", it.planId).put("r", it.resellerId)
            .put("rn", it.resellerName).put("a", it.amountFc).put("t", it.createdAt).put("s", it.status).put("st", it.rated)) } })
        grant?.let { g -> j.put("grant", JSONObject().put("o", g.orderId).put("k", g.token).put("r", g.resellerId).put("rn", g.resellerName)
            .put("p", g.planId).put("d", g.durationMs).put("mb", g.megabytes).put("aw", g.activatedAtWall).put("ae", g.activatedAtElapsed)) }
        j.put("relay", relayAccepted).put("bonus", bonusGiven)
        runCatching { file.writeText(j.toString()) }
    }
}
