package com.atlasglobe.app.connect

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.atlasglobe.app.data.Api
import com.atlasglobe.app.notify.NotificationHelper
import com.atlasglobe.app.sync.ServerSync
import com.atlasglobe.app.vpn.AtlasVpnService
import com.atlasglobe.app.vpn.OutboundConfig
import com.atlasglobe.app.vpn.Outbounds
import org.json.JSONObject
import java.net.Socket

data class AcTariff(val id: Int, val label: String, val price: Int, val days: Int)
data class AcState(val name: String, val balance: Int, val expiresAt: Long, val active: Boolean, val hasConfig: Boolean, val suspended: Boolean, val tariffs: List<AcTariff>)

/**
 * Atlas Connect : le client colle son token (délivré par le panel admin), puis un seul bouton power.
 * L'application ne contient aucune configuration VPN : elle reçoit celle que l'administration a donnée à ce client
 * et fait passer tout le trafic par ce serveur.
 */
object AtlasConnectManager {
    enum class Status { OFF, CONNECTING, ON }
    private lateinit var app: Context
    private val main = Handler(Looper.getMainLooper())
    var status by mutableStateOf(Status.OFF); private set
    var message by mutableStateOf(""); private set
    var state by mutableStateOf<AcState?>(null); private set
    @Volatile private var outs: List<OutboundConfig> = emptyList()
    @Volatile private var idx = 0
    @Volatile private var okCount = 0
    @Volatile private var failCount = 0
    @Volatile private var lastDial = 0L
    @Volatile private var protectFn: (Socket) -> Unit = {}
    private val warm = java.util.concurrent.ConcurrentLinkedQueue<Outbounds.Transport>()

    /** Garde 2 transports prêts tant que l'utilisateur navigue : les nouvelles connexions s'ouvrent aussitôt (VPN rapide). */
    private fun startFiller() {
        Thread({
            while (status != Status.OFF) {
                val now = System.currentTimeMillis()
                warm.removeAll { t -> val old = now - t.born > 3000 || t.base.isClosed; if (old) runCatching { t.raw.close() }; old }
                val list = outs
                if (list.isNotEmpty() && now - lastDial < 10_000 && warm.size < 2) runCatching { warm.add(Outbounds.openTransport(list[idx % list.size], protectFn)) }
                try { Thread.sleep(120) } catch (e: InterruptedException) { break }
            }
            warm.forEach { runCatching { it.raw.close() } }; warm.clear()
        }, "atlas-warm").also { it.isDaemon = true }.start()
    }

    fun init(c: Context) {
        if (::app.isInitialized) return
        app = c.applicationContext
        app.getSharedPreferences("atlas_ac", Context.MODE_PRIVATE).getString("state", null)?.let { runCatching { state = parse(JSONObject(it)) } }
    }

    private fun parse(o: JSONObject): AcState {
        val t = o.optJSONArray("tariffs"); val list = ArrayList<AcTariff>()
        for (i in 0 until (t?.length() ?: 0)) { val x = t!!.getJSONObject(i); list.add(AcTariff(x.getInt("id"), x.getString("label"), x.getInt("price"), x.getInt("days"))) }
        return AcState(o.optString("name"), o.optInt("balance"), o.optLong("expiresAt"), o.optBoolean("active"), o.optBoolean("hasConfig"), o.optBoolean("suspended"), list)
    }
    private fun store(o: JSONObject) { state = parse(o); app.getSharedPreferences("atlas_ac", Context.MODE_PRIVATE).edit().putString("state", o.toString()).apply() }
    val activated: Boolean get() = state != null

    /** @return null si tout va bien, sinon le message d'erreur à afficher. */
    fun activate(c: Context, token: String): String? {
        init(c)
        if (!Api.configured()) return "Le serveur Atlas n'est pas encore configuré"
        if (!ServerSync.ensureRegistered(c)) return "Connexion Internet requise"
        val r = Api.signed("POST", "/v1/ac/activate", JSONObject().put("token", token.trim()))
        return when {
            r.ok -> { r.json?.optJSONObject("state")?.let { store(it) }; null }
            r.code == 404 -> "Token invalide"
            r.code == 409 -> "Ce token est déjà utilisé sur un autre appareil"
            r.code == 403 -> "Ce compte est suspendu"
            r.code == 429 -> "Trop d'essais. Réessayez dans quelques minutes"
            else -> "Connexion Internet requise"
        }
    }

    fun refresh(c: Context): Boolean { init(c); val r = Api.signed("POST", "/v1/ac/state", JSONObject()); if (r.ok) r.json?.let { store(it) }; return r.ok }

    /** @return null si l'achat est fait, sinon le message à afficher. */
    fun buy(c: Context, tariff: AcTariff): String? {
        val r = Api.signed("POST", "/v1/ac/buy", JSONObject().put("tariff", tariff.id))
        return when {
            r.ok -> { r.json?.optJSONObject("state")?.let { store(it) }; null }
            r.code == 402 -> "Solde insuffisant : il vous manque ${"%,d".format(r.json?.optInt("missing") ?: 0).replace(',', ' ')} FC. Rechargez votre solde auprès d'Atlas."
            r.code == 403 -> "Ce compte est suspendu"
            else -> "Achat impossible. Vérifiez votre connexion Internet."
        }
    }

    /** Démarre le VPN avec la configuration donnée par l'administration. À appeler hors du thread principal. */
    fun connect(c: Context) {
        init(c)
        if (status != Status.OFF) return
        main.post { status = Status.CONNECTING; message = "" }
        val r = Api.signed("POST", "/v1/ac/config", JSONObject())
        val arr = r.json?.optJSONArray("items")
        if (!r.ok || arr == null) { onFailed("Connexion impossible. Contactez le support Atlas."); return }
        outs = (0 until arr.length()).mapNotNull { OutboundConfig.from(arr.getJSONObject(it)) }
        if (outs.isEmpty()) { onFailed("Connexion impossible. Contactez le support Atlas."); return }
        idx = 0; okCount = 0; failCount = 0; startFiller()
        c.startService(Intent(c, AtlasVpnService::class.java).putExtra("mode", "connect"))
        main.postDelayed({ if (status == Status.CONNECTING) { disconnect(); onFailed("Connexion impossible. Réessayez dans un instant.") } }, 15_000)
        state?.expiresAt?.let { exp -> val d = exp - System.currentTimeMillis()
            if (d > 0) main.postDelayed({ if (status != Status.OFF && (state?.expiresAt ?: 0) <= System.currentTimeMillis()) { disconnect()
                NotificationHelper.connectivity(app, "Atlas Connect", "Votre abonnement est terminé. Choisissez un tarif pour vous reconnecter.") } }, d + 1000) }
    }

    fun disconnect() { runCatching { AtlasVpnService.instance?.stopSelf() }; main.post { status = Status.OFF } }

    // ---- appelés par le service VPN ----
    fun onTunnelUp() { main.post { if (status == Status.CONNECTING) status = Status.ON } }
    fun onFailed(msg: String) { main.post { status = Status.OFF; message = msg } }
    fun onStopped() { main.post { status = Status.OFF } }

    /** Chaque flux de l'application passe par le serveur de la configuration ; en cas d'échec on essaie le nœud suivant. */
    fun dial(host: String, port: Int, protect: (Socket) -> Unit): Socket? {
        val list = outs; if (list.isEmpty()) return null
        protectFn = protect; lastDial = System.currentTimeMillis()
        val w = warm.poll()
        for (k in list.indices) {
            val i = (idx + k) % list.size
            val s = Outbounds.dial(list[i], host, port, protect, if (k == 0) w else null)
            if (s != null) { idx = i; okCount++; failCount = 0; return s }
        }
        if (okCount == 0 && ++failCount >= 8) onFailed("Connexion impossible. Contactez le support Atlas.").also { disconnect() }
        return null
    }
}
