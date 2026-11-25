package com.atlasglobe.app.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Base64
import com.atlasglobe.app.connect.AtlasConnectManager
import com.atlasglobe.app.connect.ConnectionManager
import org.json.JSONObject
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * VPN local d'Atlas (icône clé). Il n'envoie rien à l'étranger : tout le trafic va seulement au revendeur proche.
 * Le client montre sa session (obtenue par message chiffré), jamais son jeton, à chaque flux.
 */
class AtlasVpnService : VpnService() {
private fun dbgSvc(line: String) { runCatching {
    val f = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "atlas_debug.txt")
    f.appendText(java.text.SimpleDateFormat("HH:mm:ss.SSS").format(java.util.Date()) + " " + line + "\n")
} }

    private var tun: ParcelFileDescriptor? = null
    private var proxy: TunProxy? = null

    private var mode = "wifi"      // "wifi" = revendeur Wi-Fi, "connect" = Atlas Connect

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int { mode = intent?.getStringExtra("mode") ?: "wifi"; startTunnel(); return START_NOT_STICKY }

    private fun startTunnel() {
        dbgSvc("startTunnel : mode=" + mode)
        stopTunnel()
        val b = Builder().setSession("Atlas Connect").setMtu(1500)
            .addAddress("10.77.0.2", 32).addRoute("0.0.0.0", 0).addDnsServer("10.77.0.1")
        runCatching { b.addAddress("fd77:77::2", 128).addRoute("::", 0) }          // IPv6 bloqué : les applications passent en IPv4
        runCatching { b.addDisallowedApplication(packageName) }
        val fd = b.establish() ?: run { if (mode == "connect") AtlasConnectManager.onFailed("Le VPN n'a pas pu démarrer") else ConnectionManager.onTunnelFailed(); stopSelf(); return }
        tun = fd; instance = this
        val p = TunProxy(FileInputStream(fd.fileDescriptor), FileOutputStream(fd.fileDescriptor)) { h, port -> openTunnel(h, port) }
        proxy = p
        Thread({ p.run() }, "atlas-tun").also { it.isDaemon = true }.start()
        if (mode == "connect") AtlasConnectManager.onTunnelUp() else ConnectionManager.onTunnelUp()
    }

    /** Connexion authentifiée au revendeur, liée au Wi-Fi local (et non au VPN). Réponse : 0 = accepté. */
    private fun openTunnel(host: String, port: Int): Socket? {
        if (mode == "connect") return AtlasConnectManager.dial(host, port) { protect(it) }
        val s = Socket()
        return try {
            protect(s); ConnectionManager.network?.bindSocket(s); s.tcpNoDelay = true; s.receiveBufferSize = 262144; s.sendBufferSize = 262144
            s.connect(InetSocketAddress(ConnectionManager.gateway, ConnectionManager.port), 6000)
            val hdr = JSONObject().put("s", ConnectionManager.session).put("h", host).put("p", port).toString()
            s.getOutputStream().write(("ATL1 " + Base64.encodeToString(hdr.toByteArray(), Base64.NO_WRAP) + "\n").toByteArray())
            s.getOutputStream().flush()
            when (val st = s.getInputStream().read()) {
                0 -> { ConnectionManager.onTunnelOk(); s }
                else -> { s.close(); ConnectionManager.onTunnelRefused(st); null }
            }
        } catch (e: Exception) { runCatching { s.close() }; null }
    }

    private fun stopTunnel() {
        dbgSvc("stopTunnel appele")
        // Arrêt hors du thread principal : fermer des connexions bloquées ne doit jamais figer le bouton ni garder la clé VPN.
        val p = proxy; val t = tun; proxy = null; tun = null
        Thread({
            val th = Thread({ runCatching { p?.stop() } }, "atlas-stop-proxy").also { it.isDaemon = true }
            th.start(); runCatching { th.join(1500) }
            runCatching { t?.close() }
        }, "atlas-stop").also { it.isDaemon = true }.start()
    }

    override fun onRevoke() { if (mode == "connect") AtlasConnectManager.onStopped() else ConnectionManager.onVpnRevoked(); stopTunnel(); super.onRevoke() }
    override fun onDestroy() {
        dbgSvc("onDestroy du service"); stopTunnel(); if (instance === this) instance = null; if (mode == "connect") AtlasConnectManager.onStopped(); super.onDestroy() }

    /** Arrêt complet depuis l'extérieur : on ferme d'abord l'interface VPN (c'est ce qui fait disparaître la clé), puis le service. */
    fun shutdown() {
        val p = proxy; val t = tun; proxy = null; tun = null
        Thread({
            val th = Thread({ runCatching { p?.stop() } }, "atlas-stop-proxy").also { it.isDaemon = true }
            th.start(); runCatching { th.join(1500) }
            runCatching { t?.close() }
            runCatching { stopSelf() }
        }, "atlas-shutdown").also { it.isDaemon = true }.start()
    }

    companion object { @Volatile var instance: AtlasVpnService? = null }
}
