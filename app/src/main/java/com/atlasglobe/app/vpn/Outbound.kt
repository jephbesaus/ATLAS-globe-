package com.atlasglobe.app.vpn

import android.util.Base64
import org.json.JSONObject
import java.io.*
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.zip.CRC32
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.*

/**
 * Moteur de sortie d'Atlas Connect. Il n'a AUCUNE configuration en dur : il lit celle que le panel admin donne au client
 * (VLESS, VMess, Trojan, Shadowsocks ; transports TCP ou WebSocket ; TLS ou non) et fait passer chaque flux par ce serveur.
 */
class OutboundConfig(
    val proto: String, val host: String, val port: Int, val id: String, val pass: String, val method: String,
    val scy: String, val net: String, val path: String, val hostHeader: String, val security: String, val sni: String, val insecure: Boolean
) {
    companion object {
        fun from(o: JSONObject): OutboundConfig? = try {
            OutboundConfig(o.getString("proto"), o.getString("host"), o.getInt("port"), o.optString("id"), o.optString("pass"), o.optString("method"),
                o.optString("scy", "auto"), o.optString("net", "tcp"), o.optString("path", "/").ifEmpty { "/" }, o.optString("hostHeader"),
                if (o.optString("proto") == "trojan") "tls" else o.optString("security", "none"), o.optString("sni"), o.optBoolean("insecure", false))
        } catch (e: Exception) { null }
    }
}

/** Socket virtuel : les flux passent par les couches TLS / WebSocket / protocole. */
class ProxySocket(private val i: InputStream, private val o: OutputStream, private val base: Socket) : Socket() {
    override fun getInputStream(): InputStream = i
    override fun getOutputStream(): OutputStream = o
    override fun shutdownOutput() { Thread { try { Thread.sleep(20_000) } catch (e: Exception) {}; close() }.also { it.isDaemon = true }.start() }
    override fun close() { runCatching { base.close() } }
    override fun isClosed(): Boolean = base.isClosed
}

object Outbounds {
    private val rnd = SecureRandom()
    private fun rb(n: Int) = ByteArray(n).also { rnd.nextBytes(it) }
    private fun ip(h: String) = h.split(".").map { it.toInt().toByte() }.toByteArray()
    private fun uuid(s: String): ByteArray { val h = s.replace("-", ""); return ByteArray(16) { h.substring(it * 2, it * 2 + 2).toInt(16).toByte() } }
    private fun u16(v: Int) = byteArrayOf((v shr 8).toByte(), v.toByte())

    /** Connexion de transport (TCP + TLS + WebSocket) prête à recevoir l'en-tête du protocole. */
    class Transport(val raw: Socket, val base: Socket, val inp: InputStream, val out: OutputStream, val born: Long = System.currentTimeMillis())

    fun openTransport(c: OutboundConfig, protect: (Socket) -> Unit): Transport {
        val raw = Socket()
        try {
            protect(raw); raw.tcpNoDelay = true; raw.receiveBufferSize = 262144; raw.sendBufferSize = 262144; raw.connect(InetSocketAddress(c.host, c.port), 8000)
            var inp: InputStream = raw.getInputStream(); var out: OutputStream = raw.getOutputStream(); var base: Socket = raw
            if (c.security == "tls") { val t = tls(raw, c.sni.ifEmpty { c.hostHeader.ifEmpty { c.host } }, c.insecure); inp = t.inputStream; out = t.outputStream; base = t }
            if (c.net == "ws") { val (wi, wo) = ws(inp, out, c.hostHeader.ifEmpty { c.sni.ifEmpty { c.host } }, c.path); inp = wi; out = wo }
            return Transport(raw, base, inp, out)
        } catch (e: Exception) { runCatching { raw.close() }; throw e }
    }

    /** Ouvre un flux vers (host, port) à travers le serveur de la configuration. `warm` = transport déjà prêt (gain de 2 allers-retours). */
    fun dial(c: OutboundConfig, host: String, port: Int, protect: (Socket) -> Unit, warm: Transport? = null): Socket? {
        var t: Transport? = warm
        return try {
            if (t == null) t = openTransport(c, protect)
            val (pi, po) = when (c.proto) {
                "vless" -> vless(c, t.inp, t.out, host, port); "trojan" -> trojan(c, t.inp, t.out, host, port)
                "ss" -> shadowsocks(c, t.inp, t.out, host, port); "vmess" -> vmess(c, t.inp, t.out, host, port)
                else -> throw IOException("protocole")
            }
            ProxySocket(pi, po, t.base)
        } catch (e: Exception) { runCatching { t?.raw?.close() }; null }
    }

    // ---------------- TLS ----------------
    private fun tls(s: Socket, sni: String, insecure: Boolean): SSLSocket {
        val ctx = SSLContext.getInstance("TLS")
        if (insecure) ctx.init(null, arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(c: Array<X509Certificate>?, a: String?) {}
            override fun checkServerTrusted(c: Array<X509Certificate>?, a: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }), SecureRandom()) else ctx.init(null, null, null)
        val ss = ctx.socketFactory.createSocket(s, sni, s.port, true) as SSLSocket
        val isIp = Regex("^[0-9.]+$|:").containsMatchIn(sni)
        if (!isIp) { val p = ss.sslParameters; p.serverNames = listOf(SNIHostName(sni)); ss.sslParameters = p }
        ss.startHandshake()
        if (!insecure && !HttpsURLConnection.getDefaultHostnameVerifier().verify(sni, ss.session)) throw SSLPeerUnverifiedException("hôte")
        return ss
    }

    // ---------------- WebSocket ----------------
    private fun readLine(i: InputStream): String { val sb = StringBuilder(); while (sb.length < 8192) { val b = i.read(); if (b < 0) throw EOFException(); if (b == '\n'.code) break; if (b != '\r'.code) sb.append(b.toChar()) }; return sb.toString() }

    private class WsOut(private val o: OutputStream) : OutputStream() {
        private val rnd = SecureRandom()
        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
        @Synchronized override fun write(b: ByteArray, off: Int, len: Int) {
            var p = off; var left = len
            while (left > 0) { val n = minOf(left, 16000); frame(0x82, b, p, n); p += n; left -= n }
        }
        @Synchronized fun frame(op: Int, b: ByteArray, off: Int, n: Int) {
            val h = ByteArrayOutputStream(); h.write(op)
            when { n < 126 -> h.write(0x80 or n); else -> { h.write(0x80 or 126); h.write(n shr 8); h.write(n) } }
            val mask = ByteArray(4).also { rnd.nextBytes(it) }; h.write(mask)
            val body = ByteArray(n) { (b[off + it].toInt() xor mask[it % 4].toInt()).toByte() }
            h.write(body); o.write(h.toByteArray()); o.flush()
        }
        override fun flush() = o.flush()
        override fun close() { runCatching { frame(0x88, ByteArray(0), 0, 0) }; o.close() }
    }

    private class WsIn(private val i: InputStream, private val out: WsOut) : InputStream() {
        private var remain = 0L
        override fun read(): Int { val b = ByteArray(1); return if (read(b, 0, 1) < 0) -1 else b[0].toInt() and 0xff }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            while (remain == 0L) { if (!next()) return -1 }
            val n = i.read(b, off, minOf(len.toLong(), remain).toInt()); if (n < 0) return -1; remain -= n; return n
        }
        private fun next(): Boolean {
            val b0 = i.read(); val b1 = i.read(); if (b0 < 0 || b1 < 0) return false
            val op = b0 and 0x0f; var len = (b1 and 0x7f).toLong()
            if (len == 126L) len = ((i.read() shl 8) or i.read()).toLong()
            else if (len == 127L) { len = 0; repeat(8) { len = (len shl 8) or i.read().toLong() } }
            val mask = if ((b1 and 0x80) != 0) ByteArray(4).also { readFully(i, it) } else null
            when (op) {
                8 -> return false
                9, 10 -> { val d = ByteArray(len.toInt()); readFully(i, d); if (op == 9) out.frame(0x8A, d, 0, d.size); remain = 0 }
                else -> remain = len
            }
            if (mask != null && remain > 0) throw IOException("trame masquée")
            return true
        }
    }

    private fun ws(i: InputStream, o: OutputStream, hostHeader: String, path: String): Pair<InputStream, OutputStream> {
        val key = Base64.encodeToString(rb(16), Base64.NO_WRAP)
        o.write(("GET $path HTTP/1.1\r\nHost: $hostHeader\r\nUser-Agent: Mozilla/5.0\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: $key\r\nSec-WebSocket-Version: 13\r\n\r\n").toByteArray()); o.flush()
        val st = readLine(i); if (!st.contains(" 101")) throw IOException("ws refusé: $st")
        while (readLine(i).isNotEmpty()) { /* en-têtes */ }
        val wo = WsOut(o); return WsIn(i, wo) to wo
    }

    private fun readFully(i: InputStream, b: ByteArray) { var o = 0; while (o < b.size) { val n = i.read(b, o, b.size - o); if (n < 0) throw EOFException(); o += n } }

    // ---------------- VLESS ----------------
    private fun vless(c: OutboundConfig, i: InputStream, o: OutputStream, host: String, port: Int): Pair<InputStream, OutputStream> {
        val h = ByteArrayOutputStream(); h.write(0); h.write(uuid(c.id)); h.write(0); h.write(1); h.write(u16(port)); h.write(1); h.write(ip(host))
        o.write(h.toByteArray()); o.flush()
        val inp = object : FilterInputStream(i) {
            private var done = false
            private fun head() { if (!done) { done = true; val v = `in`.read(); val al = `in`.read(); if (v < 0 || al < 0) throw EOFException(); if (al > 0) readFully(`in`, ByteArray(al)) } }
            override fun read(): Int { head(); return super.read() }
            override fun read(b: ByteArray, off: Int, len: Int): Int { head(); return super.read(b, off, len) }
        }
        return inp to o
    }

    // ---------------- Trojan ----------------
    private fun trojan(c: OutboundConfig, i: InputStream, o: OutputStream, host: String, port: Int): Pair<InputStream, OutputStream> {
        val hash = MessageDigest.getInstance("SHA-224").digest(c.pass.toByteArray()).joinToString("") { "%02x".format(it) }
        val h = ByteArrayOutputStream(); h.write(hash.toByteArray()); h.write("\r\n".toByteArray()); h.write(1); h.write(1); h.write(ip(host)); h.write(u16(port)); h.write("\r\n".toByteArray())
        o.write(h.toByteArray()); o.flush(); return i to o
    }

    // ---------------- Shadowsocks AEAD ----------------
    private fun aead(name: String, key: ByteArray, nonce: ByteArray, data: ByteArray, enc: Boolean): ByteArray {
        val c: Cipher = if (name.startsWith("chacha")) Cipher.getInstance("ChaCha20-Poly1305") else Cipher.getInstance("AES/GCM/NoPadding")
        val k = SecretKeySpec(key, if (name.startsWith("chacha")) "ChaCha20" else "AES")
        c.init(if (enc) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE, k, if (name.startsWith("chacha")) IvParameterSpec(nonce) else GCMParameterSpec(128, nonce))
        return c.doFinal(data)
    }
    private fun incr(n: ByteArray) { for (j in n.indices) { n[j] = (n[j] + 1).toByte(); if (n[j].toInt() != 0) break } }
    private fun hmac(alg: String, key: ByteArray, d: ByteArray): ByteArray { val m = Mac.getInstance(alg); m.init(SecretKeySpec(key, alg)); return m.doFinal(d) }

    private fun shadowsocks(c: OutboundConfig, i: InputStream, o: OutputStream, host: String, port: Int): Pair<InputStream, OutputStream> {
        val m = c.method; val kl = if (m == "aes-128-gcm") 16 else 32
        var key = ByteArray(0); var prev = ByteArray(0)                                            // EVP_BytesToKey (MD5)
        while (key.size < kl) { prev = MessageDigest.getInstance("MD5").digest(prev + c.pass.toByteArray()); key += prev }
        key = key.copyOf(kl)
        fun sub(salt: ByteArray): ByteArray {                                                      // HKDF-SHA1, info = "ss-subkey"
            val prk = hmac("HmacSHA1", salt, key); var t = ByteArray(0); var out = ByteArray(0); var n = 1
            while (out.size < kl) { t = hmac("HmacSHA1", prk, t + "ss-subkey".toByteArray() + byteArrayOf(n.toByte())); out += t; n++ }; return out.copyOf(kl)
        }
        val salt = rb(kl); val ek = sub(salt); val en = ByteArray(12); o.write(salt)
        fun send(b: ByteArray, off: Int, n: Int) {
            val l = aead(m, ek, en, u16(n), true); incr(en); val p = aead(m, ek, en, b.copyOfRange(off, off + n), true); incr(en); o.write(l); o.write(p); o.flush()
        }
        val hdr = byteArrayOf(1) + ip(host) + u16(port); send(hdr, 0, hdr.size)
        val outS = object : OutputStream() {
            override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
            @Synchronized override fun write(b: ByteArray, off: Int, len: Int) { var p = off; var left = len; while (left > 0) { val n = minOf(left, 0x3fff); send(b, p, n); p += n; left -= n } }
            override fun flush() = o.flush(); override fun close() = o.close()
        }
        val inS = object : InputStream() {
            private var dk: ByteArray? = null; private val dn = ByteArray(12); private var buf = ByteArray(0); private var pos = 0
            override fun read(): Int { val b = ByteArray(1); return if (read(b, 0, 1) < 0) -1 else b[0].toInt() and 0xff }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (dk == null) { val s = ByteArray(kl); readFully(i, s); dk = sub(s) }
                while (pos >= buf.size) {
                    val lc = ByteArray(2 + 16); try { readFully(i, lc) } catch (e: EOFException) { return -1 }
                    val l = aead(m, dk!!, dn, lc, false); incr(dn); val n = ((l[0].toInt() and 0xff) shl 8) or (l[1].toInt() and 0xff)
                    val pc = ByteArray(n + 16); readFully(i, pc); buf = aead(m, dk!!, dn, pc, false); incr(dn); pos = 0
                }
                val n = minOf(len, buf.size - pos); System.arraycopy(buf, pos, b, off, n); pos += n; return n
            }
        }
        return inS to outS
    }

    // ---------------- VMess (AEAD, alterId = 0) ----------------
    private fun sha256(d: ByteArray) = MessageDigest.getInstance("SHA-256").digest(d)
    private fun hmacH(h: (ByteArray) -> ByteArray, key: ByteArray, msg: ByteArray): ByteArray {
        var k = key; if (k.size > 64) k = h(k); k = k.copyOf(64)
        val ip = ByteArray(64) { (k[it].toInt() xor 0x36).toByte() }; val op = ByteArray(64) { (k[it].toInt() xor 0x5c).toByte() }
        return h(op + h(ip + msg))
    }
    private fun kdf(key: ByteArray, vararg path: ByteArray): ByteArray {
        var h: (ByteArray) -> ByteArray = { d -> hmac("HmacSHA256", "VMess AEAD KDF".toByteArray(), d) }
        for (p in path) { val parent = h; h = { d -> hmacH(parent, p, d) } }
        return h(key)
    }
    private fun s(x: String) = x.toByteArray()
    private fun gcm(key: ByteArray, nonce: ByteArray, data: ByteArray, aad: ByteArray?, enc: Boolean): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(if (enc) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        if (aad != null) c.updateAAD(aad); return c.doFinal(data)
    }
    private fun chachaKey(k: ByteArray): ByteArray { val t = MessageDigest.getInstance("MD5").digest(k); return t + MessageDigest.getInstance("MD5").digest(t) }

    private fun vmess(c: OutboundConfig, i: InputStream, o: OutputStream, host: String, port: Int): Pair<InputStream, OutputStream> {
        val cmd = MessageDigest.getInstance("MD5").digest(uuid(c.id) + s("c48619fe-8f02-49e0-b9e9-edf763e17e21"))
        val iv = rb(16); val key = rb(16); val respV = rb(1)[0]
        val sec = when (c.scy) { "none", "zero" -> 5; "chacha20-poly1305" -> 4; else -> 3 }
        val h = ByteArrayOutputStream(); h.write(1); h.write(iv); h.write(key); h.write(respV.toInt()); h.write(0x01); h.write(sec); h.write(0); h.write(1)
        h.write(u16(port)); h.write(1); h.write(ip(host))
        var fnv = 0x811c9dc5.toInt(); for (b in h.toByteArray()) { fnv = fnv xor (b.toInt() and 0xff); fnv *= 0x01000193 }
        h.write(byteArrayOf((fnv ushr 24).toByte(), (fnv ushr 16).toByte(), (fnv ushr 8).toByte(), fnv.toByte()))
        val data = h.toByteArray()
        // en-tête AEAD
        val t = System.currentTimeMillis() / 1000; val a = ByteArray(16); for (j in 0 until 8) a[j] = (t shr (56 - 8 * j)).toByte(); rb(4).copyInto(a, 8)
        val crc = CRC32().also { it.update(a, 0, 12) }.value; for (j in 0 until 4) a[12 + j] = (crc shr (24 - 8 * j)).toByte()
        val ec = Cipher.getInstance("AES/ECB/NoPadding"); ec.init(Cipher.ENCRYPT_MODE, SecretKeySpec(kdf(cmd, s("AES Auth ID Encryption")).copyOf(16), "AES")); val authId = ec.doFinal(a)
        val nonce = rb(8)
        val lenEnc = gcm(kdf(cmd, s("VMess Header AEAD Key_Length"), authId, nonce).copyOf(16), kdf(cmd, s("VMess Header AEAD Nonce_Length"), authId, nonce).copyOf(12), u16(data.size), authId, true)
        val hdrEnc = gcm(kdf(cmd, s("VMess Header AEAD Key"), authId, nonce).copyOf(16), kdf(cmd, s("VMess Header AEAD Nonce"), authId, nonce).copyOf(12), data, authId, true)
        o.write(authId + lenEnc + nonce + hdrEnc); o.flush()

        val reqKey = if (sec == 4) chachaKey(key) else key
        fun chunkNonce(count: Int, ivx: ByteArray) = u16(count) + ivx.copyOfRange(2, 12)
        fun sealC(k: ByteArray, n: ByteArray, d: ByteArray): ByteArray = when (sec) { 5 -> d
            4 -> { val cc = Cipher.getInstance("ChaCha20-Poly1305"); cc.init(Cipher.ENCRYPT_MODE, SecretKeySpec(k, "ChaCha20"), IvParameterSpec(n)); cc.doFinal(d) }
            else -> gcm(k, n, d, null, true) }
        fun openC(k: ByteArray, n: ByteArray, d: ByteArray): ByteArray = when (sec) { 5 -> d
            4 -> { val cc = Cipher.getInstance("ChaCha20-Poly1305"); cc.init(Cipher.DECRYPT_MODE, SecretKeySpec(k, "ChaCha20"), IvParameterSpec(n)); cc.doFinal(d) }
            else -> gcm(k, n, d, null, false) }
        var count = 0
        val outV = object : OutputStream() {
            override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
            @Synchronized override fun write(b: ByteArray, off: Int, len: Int) {
                var p = off; var left = len
                while (left > 0) { val n = minOf(left, 8000); val sealed = sealC(reqKey, chunkNonce(count++, iv), b.copyOfRange(p, p + n)); o.write(u16(sealed.size)); o.write(sealed); p += n; left -= n }
                o.flush()
            }
            override fun flush() = o.flush(); override fun close() = o.close()
        }
        val rKey16 = sha256(key).copyOf(16); val rIv = sha256(iv).copyOf(16); val respKey = if (sec == 4) chachaKey(rKey16) else rKey16
        val inV = object : InputStream() {
            private var started = false; private var rc = 0; private var buf = ByteArray(0); private var pos = 0; private var eof = false
            override fun read(): Int { val b = ByteArray(1); return if (read(b, 0, 1) < 0) -1 else b[0].toInt() and 0xff }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (eof) return -1
                if (!started) { started = true
                    val lc = ByteArray(18); readFully(i, lc)
                    val l = gcm(kdf(rKey16, s("AEAD Resp Header Len Key")).copyOf(16), kdf(rIv, s("AEAD Resp Header Len IV")).copyOf(12), lc, null, false)
                    val n = ((l[0].toInt() and 0xff) shl 8) or (l[1].toInt() and 0xff); val hc = ByteArray(n + 16); readFully(i, hc)
                    val hd = gcm(kdf(rKey16, s("AEAD Resp Header Key")).copyOf(16), kdf(rIv, s("AEAD Resp Header IV")).copyOf(12), hc, null, false)
                    if (hd.isEmpty() || hd[0] != respV) throw IOException("réponse VMess invalide") }
                while (pos >= buf.size) {
                    val lh = ByteArray(2); try { readFully(i, lh) } catch (e: EOFException) { eof = true; return -1 }
                    val n = ((lh[0].toInt() and 0xff) shl 8) or (lh[1].toInt() and 0xff); val ch = ByteArray(n); readFully(i, ch)
                    buf = openC(respKey, chunkNonce(rc++, rIv), ch); pos = 0
                    if (buf.isEmpty()) { eof = true; return -1 }
                }
                val n = minOf(len, buf.size - pos); System.arraycopy(buf, pos, b, off, n); pos += n; return n
            }
        }
        return inV to outV
    }
}
