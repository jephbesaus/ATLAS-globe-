package com.atlasglobe.app.connect

import android.content.Context
import android.content.Intent
import android.net.*
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.atlasglobe.app.data.ChatRepo
import com.atlasglobe.app.mesh.BleMesh
import com.atlasglobe.app.mesh.MeshRouter
import com.atlasglobe.app.mesh.MeshStore
import com.atlasglobe.app.mesh.ResellerDirectory
import com.atlasglobe.app.notify.NotificationHelper
import com.atlasglobe.app.vpn.AtlasVpnService
import org.json.JSONObject

/**
 * Connexion invisible au revendeur :
 *  1. demande chiffrée "join" (jeton) par le mesh  ->  2. le revendeur répond avec le Wi-Fi local et une session secrète
 *  3. connexion Wi-Fi locale (aucun code affiché)  ->  4. VPN local : tout le trafic passe par le revendeur
 * Si le revendeur disparaît (déplacement), Atlas cherche un autre revendeur et reprend avec le même jeton.
 */
object ConnectionManager {
    enum class State { IDLE, ASKING, JOINING, TUNNEL, CONNECTED, ERROR }

    private lateinit var app: Context
    private val main = Handler(Looper.getMainLooper())
    var state by mutableStateOf(State.IDLE); private set
    var message by mutableStateOf(""); private set
    @Volatile var network: Network? = null
    @Volatile var gateway = ""
    @Volatile var port = 18080
    @Volatile var session = ""
    private var target: String? = null
    private var netCb: ConnectivityManager.NetworkCallback? = null
    private var attempt = 0
    private var okOnce = false

    fun init(c: Context) { if (!::app.isInitialized) app = c.applicationContext }

    private fun cm() = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private fun set(s: State, m: String = "") { state = s; message = m }

    /** Après "Paiement approuvé" : se connecte seul si l'autorisation VPN a déjà été donnée, sinon demande de toucher la notification. */
    fun autoConnect(c: Context) {
        init(c)
        if (VpnService.prepare(c) == null) connect(c)
        else NotificationHelper.connectivity(c, "Atlas Connect", "Touchez pour autoriser la connexion (une seule fois).")
    }

    fun connect(c: Context, resellerId: String? = null) {
        init(c)
        val g = ConnectStore.grant ?: return set(State.ERROR, "Aucun accès acheté")
        if (state == State.TUNNEL || state == State.JOINING) return
        target = resellerId ?: target ?: g.resellerId; attempt++
        val t = target!!
        set(State.ASKING, "Recherche de ${g.resellerName}…")
        BleMesh.searchResellers(app, 10_000)                                   // crée la liaison Bluetooth et échange les clés
        val my = attempt
        main.postDelayed({ if (my == attempt && state == State.ASKING) sendJoin(t) }, 3000)
        main.postDelayed({ if (my == attempt && state == State.ASKING) fail("Revendeur introuvable : rapprochez-vous de lui") }, 30_000)
    }

    private fun sendJoin(t: String) {
        val g = ConnectStore.grant ?: return
        if (MeshStore.keyOf(t) == null) { main.postDelayed({ if (state == State.ASKING) sendJoin(t) }, 3000); return }
        MeshRouter.sendControlTo(t, JSONObject().put("type", "join").put("token", g.token)
            .put("usedMs", ConnectStore.usedMs()).put("usedMb", 0))
    }

    /** Réponse du revendeur (arrive du mesh, chiffrée, authentifiée). */
    fun onWifi(src: String, o: JSONObject) {
        if (state != State.ASKING || src != target) return
        session = o.getString("session"); port = o.optInt("port", 18080)
        set(State.JOINING, "Connexion en cours…")
        joinWifi(o.getString("ssid"), o.getString("pass"))
    }

    fun onWifiError(code: Int) {
        fail(when (code) { 2 -> "Accès refusé par le revendeur"; 3 -> "Équipe complète : essayez un autre revendeur"
            4 -> "Votre temps est terminé"; else -> "Ce revendeur n'est pas disponible" })
        if (code == 3 || code == 1) roam()
    }

    @Suppress("DEPRECATION")
    private fun joinWifi(ssid: String, pass: String) {
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(n: Network) { main.post { onNetwork(n) } }
            override fun onUnavailable() { main.post { fail("Connexion Wi-Fi refusée ou impossible") } }
            override fun onLost(n: Network) { main.post { if (network == n) onLost() } }
        }
        releaseNetwork(); netCb = cb
        if (Build.VERSION.SDK_INT >= 29) {
            val spec = WifiNetworkSpecifier.Builder().setSsid(ssid).setWpa2Passphrase(pass).build()
            val req = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).setNetworkSpecifier(spec).build()
            cm().requestNetwork(req, cb, 30_000)
        } else {
            val wm = app.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val conf = WifiConfiguration().apply { SSID = "\"$ssid\""; preSharedKey = "\"$pass\"" }
            val id = wm.addNetwork(conf); wm.disconnect(); wm.enableNetwork(id, true); wm.reconnect()
            cm().registerNetworkCallback(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), cb)
        }
    }

    private fun onNetwork(n: Network) {
        network = n
        val lp = cm().getLinkProperties(n)
        gateway = lp?.routes?.firstOrNull { it.isDefaultRoute }?.gateway?.hostAddress
            ?: lp?.linkAddresses?.firstOrNull { it.address is java.net.Inet4Address }?.address?.hostAddress
                ?.substringBeforeLast('.')?.plus(".1") ?: ""
        if (gateway.isEmpty()) return fail("Adresse du revendeur introuvable")
        set(State.TUNNEL, "Connexion sécurisée…")
        app.startService(Intent(app, AtlasVpnService::class.java))
    }

    // ---- retours du VPN ----
    fun onTunnelUp() { /* l'interface existe ; la connexion est confirmée au premier flux accepté */ }
    fun onTunnelOk() { if (!okOnce) { okOnce = true; main.post { ConnectStore.activate(); set(State.CONNECTED, "Félicitations, vous êtes connecté")
        NotificationHelper.connectivity(app, "Atlas Connect", "Félicitations, vous êtes connecté.") } } }
    fun onTunnelFailed() { main.post { fail("Le VPN n'a pas pu démarrer") } }
    fun onVpnRevoked() { main.post { set(State.IDLE, "") ; okOnce = false } }
    fun onTunnelRefused(code: Int) { if (code == 2) main.post { ConnectStore.end(); disconnect()
        NotificationHelper.connectivity(app, "Atlas Connect", "Votre temps est terminé. Veuillez renouveler votre connexion.") } }

    /** Le revendeur n'est plus à portée : on cherche un autre revendeur Atlas, avec le même jeton. */
    private fun onLost() {
        stopVpn(); okOnce = false; network = null
        if (ConnectStore.isActive) { set(State.IDLE, "Recherche d'un autre revendeur…"); roam() } else set(State.IDLE)
    }

    private fun roam() {
        main.postDelayed({
            if (!ConnectStore.isActive || state == State.CONNECTED) return@postDelayed
            BleMesh.searchResellers(app, 8_000)
            main.postDelayed({
                val best = ResellerDirectory.recommended()
                if (best != null && best.id.isNotEmpty()) { state = State.IDLE; connect(app, best.id) } else roam()
            }, 9_000)
        }, 4_000)
    }

    fun disconnect() { stopVpn(); releaseNetwork(); network = null; okOnce = false; set(State.IDLE) }
    private fun fail(m: String) { stopVpn(); releaseNetwork(); set(State.ERROR, m) }
    private fun stopVpn() { runCatching { AtlasVpnService.instance?.stopSelf() } }
    private fun releaseNetwork() { netCb?.let { runCatching { cm().unregisterNetworkCallback(it) } }; netCb = null }
}
