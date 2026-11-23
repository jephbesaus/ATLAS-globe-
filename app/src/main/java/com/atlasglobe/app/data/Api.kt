package com.atlasglobe.app.data

import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Contrat avec le serveur Atlas. SERVER_URL vide = serveur non configuré. */
object Api {
    const val SERVER_URL = "https://atlasglobe.duckdns.org:8443"            // ex. "https://api.votredomaine.com"  (obligatoirement https)

    fun configured() = SERVER_URL.startsWith("https://")

    class Result(val code: Int, val json: JSONObject?) { val ok get() = code in 200..299 }

    private fun sha256(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    /** Requête signée par la clé d'identité du téléphone : ECDSA( id|ts|METHODE|chemin|sha256(corps) ). */
    fun signed(method: String, path: String, body: JSONObject?, readMs: Int = 25000): Result {
        if (!configured()) return Result(0, null)
        return try {
            val raw = body?.toString() ?: ""
            val ts = System.currentTimeMillis(); val id = ChatRepo.myId()
            val sig = Base64.encodeToString(Crypto.sign("$id|$ts|$method|$path|${sha256(raw)}".toByteArray()), Base64.NO_WRAP)
            val c = URL(SERVER_URL + path).openConnection() as HttpURLConnection
            c.requestMethod = method; c.connectTimeout = 10000; c.readTimeout = readMs
            c.setRequestProperty("X-Atlas-Id", id); c.setRequestProperty("X-Atlas-Ts", ts.toString()); c.setRequestProperty("X-Atlas-Sig", sig)
            if (body != null) { c.doOutput = true; c.setRequestProperty("Content-Type", "application/json"); c.outputStream.use { it.write(raw.toByteArray(Charsets.UTF_8)) } }
            val code = c.responseCode
            val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText()
            Result(code, txt?.let { runCatching { JSONObject(it) }.getOrNull() })
        } catch (e: Exception) { Result(0, null) }
    }

    /** Envoi d'un fichier (vidéo) signé : ECDSA( id|ts|POST|chemin|sha256(fichier) ). */
    fun upload(path: String, file: File): Boolean {
        if (!configured()) return false
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { i -> val b = ByteArray(65536); while (true) { val n = i.read(b); if (n < 0) break; md.update(b, 0, n) } }
            val hash = md.digest().joinToString("") { "%02x".format(it) }
            val ts = System.currentTimeMillis(); val id = ChatRepo.myId()
            val sig = Base64.encodeToString(Crypto.sign("$id|$ts|POST|$path|$hash".toByteArray()), Base64.NO_WRAP)
            val c = URL(SERVER_URL + path).openConnection() as HttpURLConnection
            c.requestMethod = "POST"; c.doOutput = true; c.setFixedLengthStreamingMode(file.length()); c.connectTimeout = 15000; c.readTimeout = 90000
            c.setRequestProperty("X-Atlas-Id", id); c.setRequestProperty("X-Atlas-Ts", ts.toString()); c.setRequestProperty("X-Atlas-Sig", sig); c.setRequestProperty("Content-Type", "video/mp4")
            c.outputStream.use { o -> file.inputStream().use { it.copyTo(o) } }
            c.responseCode in 200..299
        } catch (e: Exception) { false }
    }

    fun post(path: String, body: JSONObject): JSONObject? = signed("POST", path, body).takeIf { it.ok }?.json

    fun download(path: String, dest: File): Boolean = try {
        val c = URL(SERVER_URL + path).openConnection() as HttpURLConnection; c.connectTimeout = 10000; c.readTimeout = 20000
        if (c.responseCode != 200) false else { dest.parentFile?.mkdirs(); c.inputStream.use { i -> dest.outputStream().use { o -> i.copyTo(o) } }; true }
    } catch (e: Exception) { false }
}
