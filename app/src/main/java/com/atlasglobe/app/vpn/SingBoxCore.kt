package com.atlasglobe.app.vpn

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

/**
 * Moteur universel d'Atlas : l'exécutable officiel sing-box (livré dans l'APK sous le nom libsingbox.so) tourne en
 * processus local avec une entrée SOCKS sur 127.0.0.1. Il lit Reality, XTLS-Vision, uTLS, TLS, WebSocket, gRPC…
 * Chaque flux du tunnel (TunProxy) lui est confié par SOCKS5. L'appli étant exclue de son propre VPN, le processus aussi.
 */
object SingBoxCore {
    @Volatile private var proc: Process? = null
    @Volatile private var port = 0
    @Volatile var lastError = ""; private set
    fun logTail(): String = tail.toList().takeLast(4).joinToString(" | ").take(400)
    fun logTail(): String = tail.toList().takeLast(4).joinToString(" | ").take(400)
    private val tail = ConcurrentLinkedQueue<String>()

    /** Configuration complète : une entrée SOCKS locale, les sorties données par le panel, et le test de vitesse si plusieurs nœuds. */
    fun config(outs: List<JSONObject>, socksPort: Int): JSONObject {
        val ob = JSONArray(); val tags = JSONArray()
        outs.forEachIndexed { i, o -> ob.put(JSONObject(o.toString()).put("tag", "n$i")); tags.put("n$i") }
        val fin = if (outs.size > 1) {
            ob.put(JSONObject().put("type", "urltest").put("tag", "auto").put("outbounds", tags)
                .put("url", "https://www.gstatic.com/generate_204").put("interval", "3m").put("tolerance", 50))
            "auto"
        } else "n0"
        ob.put(JSONObject().put("type", "direct").put("tag", "direct"))
        return JSONObject()
            .put("log", JSONObject().put("level", "warn"))
            .put("dns", JSONObject().put("servers", JSONArray().put(JSONObject().put("type", "local").put("tag", "local"))))
            .put("inbounds", JSONArray().put(JSONObject().put("type", "socks").put("tag", "in").put("listen", "127.0.0.1").put("listen_port", socksPort)))
            .put("outbounds", ob)
            .put("route", JSONObject().put("final", fin).put("default_domain_resolver", "local"))
    }

    /** Démarre le moteur et attend qu'il écoute. À appeler hors du thread principal. */
    @Synchronized fun start(c: Context, outs: List<JSONObject>): Boolean {
        stop(); lastError = ""; tail.clear()
        val bin = File(c.applicationInfo.nativeLibraryDir, "libsingbox.so")
        if (!bin.exists()) { lastError = "Le moteur sing-box n'est pas dans cet APK."; return false }
        runCatching { bin.setExecutable(true) }
        port = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1")).use { it.localPort }
        val dir = File(c.filesDir, "singbox").apply { mkdirs() }
        val cfg = File(dir, "config.json")
        runCatching { cfg.writeText(config(outs, port).toString()) }.onFailure { lastError = "Configuration illisible."; return false }
        val p = try {
            ProcessBuilder(bin.absolutePath, "run", "-c", cfg.absolutePath, "-D", dir.absolutePath).redirectErrorStream(true).start()
        } catch (e: Exception) { lastError = "Impossible de lancer le moteur : " + (e.message ?: ""); return false }
        proc = p
        Thread({
            runCatching { p.inputStream.bufferedReader().forEachLine { tail.add(it.take(300)); while (tail.size > 30) tail.poll() } }
        }, "atlas-sb-log").also { it.isDaemon = true }.start()
        val t0 = System.currentTimeMillis()
        while (System.currentTimeMillis() - t0 < 8000) {
            if (!p.isAlive) { lastError = "Le moteur s'est arrêté : " + tail.lastOrNull().orEmpty(); stop(); return false }
            try { Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 200) }; return true }
            catch (e: Exception) { try { Thread.sleep(150) } catch (ie: InterruptedException) { break } }
        }
        lastError = "Le moteur ne démarre pas : " + tail.lastOrNull().orEmpty(); stop(); return false
    }

    /** Arrêt sans bloquer le thread appelant (le bouton ne doit jamais se figer). */
    @Synchronized fun stop() {
        val p = proc; proc = null
        if (p != null) Thread({ runCatching { p.destroy(); if (!p.waitFor(1, TimeUnit.SECONDS)) p.destroyForcibly() } }, "atlas-sb-stop").also { it.isDaemon = true }.start()
    }

    /** Ouvre un flux vers (host, port) à travers le moteur (SOCKS5 sans authentification, adresse IPv4 ou nom). */
    fun dial(host: String, port: Int): Socket? {
        val s = Socket()
        return try {
            s.tcpNoDelay = true; s.receiveBufferSize = 262144; s.sendBufferSize = 262144
            s.connect(InetSocketAddress("127.0.0.1", this.port), 3000)
            val o = s.getOutputStream(); val i = s.getInputStream()
            o.write(byteArrayOf(5, 1, 0)); o.flush()
            if (i.read() != 5 || i.read() != 0) throw IOException("socks")
            val rq = ByteArrayOutputStream(); rq.write(byteArrayOf(5, 1, 0))
            if (Regex("^[0-9]{1,3}(\\.[0-9]{1,3}){3}$").matches(host)) { rq.write(1); host.split(".").forEach { rq.write(it.toInt()) } }
            else { val hb = host.toByteArray(); rq.write(3); rq.write(hb.size); rq.write(hb) }
            rq.write(port shr 8); rq.write(port and 0xff)
            o.write(rq.toByteArray()); o.flush()
            val ver = i.read(); val rep = i.read(); i.read(); val atyp = i.read()
            if (ver != 5 || rep != 0) throw IOException("refus $rep")
            val extra = when (atyp) { 1 -> 4; 4 -> 16; 3 -> i.read(); else -> throw IOException("socks") }
            skip(i, extra + 2)
            s
        } catch (e: Exception) { runCatching { s.close() }; null }
    }

    private fun skip(i: InputStream, n: Int) { var left = n; while (left > 0) { if (i.read() < 0) throw IOException("fin"); left-- } }
}
