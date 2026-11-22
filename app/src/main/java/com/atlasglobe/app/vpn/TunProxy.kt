package com.atlasglobe.app.vpn

import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

object TunnelStats { @Volatile var rx = 0L; @Volatile var tx = 0L }

/**
 * Mini pile TCP/IP locale ("tun2socks") : lit les paquets IPv4 du VPN, termine les connexions TCP de l'application,
 * et envoie chaque flux au revendeur par un tunnel authentifié par jeton. Le DNS passe aussi par le tunnel (DNS sur TCP).
 * Le VPN local étant sans perte, il n'y a ni retransmission ni contrôle de congestion : seulement séquences, fenêtre et FIN/RST.
 * Le trafic UDP autre que DNS est ignoré (QUIC retombe automatiquement sur TCP).
 */
class TunProxy(
    private val tunIn: FileInputStream,
    private val tunOut: FileOutputStream,
    private val openTunnel: (host: String, port: Int) -> Socket?
) {
    @Volatile private var running = true
    private val M = 0xffffffffL
    private val MSS = 1460
    private val EMPTY = ByteArray(0)
    private val conns = ConcurrentHashMap<Long, Conn>()
    private val dnsPool = Executors.newFixedThreadPool(4)
    private val dnsCache = ConcurrentHashMap<String, Pair<ByteArray, Long>>()
    private var ipId = 0

    private class Conn(val key: Long, val src: ByteArray, val dst: ByteArray, val sp: Int, val dp: Int) {
        val lock = Object()
        @Volatile var rcvNxt = 0L; var sndNxt = 0L; var sndUna = 0L; var appWin = 65535; var appShift = 0; var wsOk = false
        @Volatile var remote: Socket? = null
        val wq = LinkedBlockingQueue<ByteArray>()
        @Volatile var closed = false; @Volatile var finFromApp = false; @Volatile var finSent = false
    }

    fun run() {
        val buf = ByteArray(32767)
        while (running) {
            val n = try { tunIn.read(buf) } catch (e: Exception) { break }
            if (n > 0) runCatching { handle(buf, n) }
        }
    }

    fun stop() {
        running = false
        conns.values.forEach { close(it, false) }
        dnsPool.shutdownNow()
    }

    // ---------------- utilitaires ----------------
    private fun u16(b: ByteArray, o: Int) = ((b[o].toInt() and 0xff) shl 8) or (b[o + 1].toInt() and 0xff)
    private fun u32(b: ByteArray, o: Int): Long = ((b[o].toLong() and 0xff) shl 24) or ((b[o + 1].toLong() and 0xff) shl 16) or
        ((b[o + 2].toLong() and 0xff) shl 8) or (b[o + 3].toLong() and 0xff)
    private fun p16(b: ByteArray, o: Int, v: Int) { b[o] = (v shr 8).toByte(); b[o + 1] = v.toByte() }
    private fun p32(b: ByteArray, o: Int, v: Long) { b[o] = (v shr 24).toByte(); b[o + 1] = (v shr 16).toByte(); b[o + 2] = (v shr 8).toByte(); b[o + 3] = v.toByte() }
    private fun csum(b: ByteArray, off: Int, len: Int, init: Long = 0): Int {
        var sum = init; var i = off; val end = off + len
        while (i + 1 < end) { sum += ((b[i].toInt() and 0xff) shl 8) or (b[i + 1].toInt() and 0xff); i += 2 }
        if (i < end) sum += (b[i].toInt() and 0xff) shl 8
        while ((sum shr 16) != 0L) sum = (sum and 0xffff) + (sum shr 16)
        return sum.inv().toInt() and 0xffff
    }
    private fun pseudo(s: ByteArray, d: ByteArray, proto: Int, len: Int): Long =
        (u16(s, 0) + u16(s, 2) + u16(d, 0) + u16(d, 2) + proto + len).toLong()
    private fun gt(a: Long, b: Long) = ((a - b) and M).let { it in 1..0x7fffffffL }

    private fun ipHeader(p: ByteArray, total: Int, proto: Int, src: ByteArray, dst: ByteArray) {
        p[0] = 0x45.toByte(); p16(p, 2, total); p16(p, 4, (ipId++) and 0xffff); p16(p, 6, 0x4000)
        p[8] = 64; p[9] = proto.toByte(); System.arraycopy(src, 0, p, 12, 4); System.arraycopy(dst, 0, p, 16, 4)
        p16(p, 10, csum(p, 0, 20))
    }

    private fun tcp(src: ByteArray, dst: ByteArray, sp: Int, dp: Int, seq: Long, ack: Long, flags: Int,
                    data: ByteArray?, off: Int, len: Int, mss: Boolean, ws: Boolean = false): ByteArray {
        val th = if (mss) (if (ws) 28 else 24) else 20; val total = 20 + th + len; val p = ByteArray(total)
        ipHeader(p, total, 6, src, dst)
        val t = 20
        p16(p, t, sp); p16(p, t + 2, dp); p32(p, t + 4, seq and M); p32(p, t + 8, ack and M)
        p[t + 12] = ((th / 4) shl 4).toByte(); p[t + 13] = flags.toByte(); p16(p, t + 14, 65535)
        if (mss) { p[t + 20] = 2; p[t + 21] = 4; p16(p, t + 22, MSS) }
        if (mss && ws) { p[t + 24] = 1; p[t + 25] = 3; p[t + 26] = 3; p[t + 27] = 4 }   // fenêtre x16 (1 Mo) : débit élevé
        if (len > 0) System.arraycopy(data!!, off, p, t + th, len)
        p16(p, t + 16, csum(p, t, th + len, pseudo(src, dst, 6, th + len)))
        return p
    }

    private fun write(p: ByteArray) { synchronized(tunOut) { runCatching { tunOut.write(p) } } }
    private fun send(c: Conn, seq: Long, flags: Int, data: ByteArray? = null, off: Int = 0, len: Int = 0, mss: Boolean = false) =
        write(tcp(c.dst, c.src, c.dp, c.sp, seq, c.rcvNxt, flags, data, off, len, mss, mss && c.wsOk))

    // ---------------- paquets entrants ----------------
    private fun handle(p: ByteArray, n: Int) {
        if (n < 20 || ((p[0].toInt() shr 4) and 0xf) != 4) return          // IPv6 et le reste : ignorés
        val ihl = (p[0].toInt() and 0x0f) * 4; val total = u16(p, 2).coerceAtMost(n)
        when (p[9].toInt() and 0xff) { 6 -> onTcp(p, ihl, total); 17 -> onUdp(p, ihl, total) }
    }

    private fun onTcp(p: ByteArray, ihl: Int, total: Int) {
        val src = p.copyOfRange(12, 16); val dst = p.copyOfRange(16, 20)
        val sp = u16(p, ihl); val dp = u16(p, ihl + 2); val seq = u32(p, ihl + 4); val ack = u32(p, ihl + 8)
        val doff = ((p[ihl + 12].toInt() and 0xf0) shr 4) * 4; val flags = p[ihl + 13].toInt() and 0xff; val win = u16(p, ihl + 14)
        val pOff = ihl + doff; val pLen = (total - pOff).coerceAtLeast(0)
        val key = (sp.toLong() shl 48) or (u32(dst, 0) shl 16) or dp.toLong()
        val c = conns[key]

        if ((flags and 0x04) != 0) { c?.let { close(it, false) }; return }                    // RST
        if ((flags and 0x02) != 0 && (flags and 0x10) == 0) {                                  // SYN
            if (c != null) return
            val n = Conn(key, src, dst, sp, dp)
            var oi = ihl + 20
            while (oi + 1 < pOff && oi < p.size) {                       // options TCP : fenêtre étendue de l'application
                val kind = p[oi].toInt() and 0xff
                if (kind == 0) break; if (kind == 1) { oi++; continue }
                val ol = p[oi + 1].toInt() and 0xff; if (ol < 2) break
                if (kind == 3 && ol == 3 && oi + 2 < p.size) { n.appShift = (p[oi + 2].toInt() and 0xff).coerceAtMost(14); n.wsOk = true }
                oi += ol
            }
            n.rcvNxt = (seq + 1) and M
            val isn = ThreadLocalRandom.current().nextLong(1, M)
            conns[key] = n
            synchronized(n.lock) { n.sndNxt = isn; send(n, isn, 0x12, mss = true); n.sndNxt = (isn + 1) and M; n.sndUna = n.sndNxt }
            thread(isDaemon = true) { openRemote(n) }
            return
        }
        if (c == null) {                                                                        // connexion inconnue : RST
            write(if ((flags and 0x10) != 0) tcp(dst, src, dp, sp, ack, 0, 0x04, null, 0, 0, false)
                  else tcp(dst, src, dp, sp, 0, (seq + pLen + (if ((flags and 0x03) != 0) 1 else 0)) and M, 0x14, null, 0, 0, false))
            return
        }
        synchronized(c.lock) {
            if ((flags and 0x10) != 0) { if (gt(ack, c.sndUna)) c.sndUna = ack; c.appWin = win shl c.appShift; c.lock.notifyAll() }
        }
        if (pLen > 0) {
            if (seq == c.rcvNxt) {
                if (c.wq.size > 300) return                    // le revendeur est lent : pas d'accusé, l'application retransmettra
                c.rcvNxt = (c.rcvNxt + pLen) and M
                c.wq.put(p.copyOfRange(pOff, pOff + pLen)); TunnelStats.tx += pLen
            }
            synchronized(c.lock) { send(c, c.sndNxt, 0x10) }
        }
        if ((flags and 0x01) != 0 && !c.finFromApp && ((seq + pLen) and M) == c.rcvNxt) {      // FIN
            c.rcvNxt = (c.rcvNxt + 1) and M; c.finFromApp = true; c.wq.put(EMPTY)
            synchronized(c.lock) { send(c, c.sndNxt, 0x10) }
            finishIfDone(c)
        }
    }

    private fun openRemote(c: Conn) {
        val host = c.dst.joinToString(".") { (it.toInt() and 0xff).toString() }
        val s = try { openTunnel(host, c.dp) } catch (e: Exception) { null }
        if (s == null) { synchronized(c.lock) { send(c, c.sndNxt, 0x14) }; conns.remove(c.key); c.closed = true; return }
        c.remote = s
        thread(isDaemon = true) { writer(c, s) }
        reader(c, s)
    }

    private fun writer(c: Conn, s: Socket) {
        try {
            val out = s.getOutputStream()
            while (!c.closed) {
                val d = c.wq.poll(500, TimeUnit.MILLISECONDS) ?: continue
                if (d.isEmpty()) { runCatching { s.shutdownOutput() }; break }
                out.write(d); out.flush()
            }
        } catch (e: Exception) { close(c, true) }
    }

    private fun reader(c: Conn, s: Socket) {
        val buf = ByteArray(65536)
        try {
            val inp = s.getInputStream()
            while (!c.closed) {
                val n = inp.read(buf); if (n < 0) break
                var off = 0
                while (off < n && !c.closed) {
                    val seg = minOf(MSS, n - off)
                    synchronized(c.lock) {                                // respecte la fenêtre de réception de l'application
                        var waits = 0
                        while (!c.closed && ((c.sndNxt - c.sndUna) and M) + seg > c.appWin.coerceAtLeast(1) && waits++ < 150) c.lock.wait(200)
                        if (!c.closed) { send(c, c.sndNxt, 0x18, buf, off, seg); c.sndNxt = (c.sndNxt + seg) and M }
                    }
                    off += seg
                }
                TunnelStats.rx += n
            }
            synchronized(c.lock) { send(c, c.sndNxt, 0x11); c.sndNxt = (c.sndNxt + 1) and M; c.finSent = true }
            finishIfDone(c)
        } catch (e: Exception) { close(c, true) }
    }

    private fun finishIfDone(c: Conn) { if (c.finFromApp && c.finSent) thread(isDaemon = true) { Thread.sleep(2000); close(c, false) } }

    private fun close(c: Conn, rst: Boolean) {
        if (c.closed && c.remote == null) { conns.remove(c.key); return }
        c.closed = true
        if (rst) synchronized(c.lock) { send(c, c.sndNxt, 0x14) }
        runCatching { c.remote?.close() }; c.wq.offer(EMPTY); conns.remove(c.key)
        synchronized(c.lock) { c.lock.notifyAll() }
    }

    // ---------------- DNS (UDP -> DNS sur TCP à travers le tunnel) ----------------
    private fun onUdp(p: ByteArray, ihl: Int, total: Int) {
        if (u16(p, ihl + 2) != 53) return
        val sp = u16(p, ihl); val src = p.copyOfRange(12, 16); val dst = p.copyOfRange(16, 20)
        val len = u16(p, ihl + 4) - 8; if (len < 12 || ihl + 8 + len > total) return
        val q = p.copyOfRange(ihl + 8, ihl + 8 + len)
        runCatching { dnsPool.execute { runCatching { resolve(q, src, dst, sp) } } }
    }

    private fun resolve(q: ByteArray, src: ByteArray, dst: ByteArray, sp: Int) {
        val key = String(q, 2, q.size - 2, Charsets.ISO_8859_1)
        val now = System.currentTimeMillis()
        val cached = dnsCache[key]?.takeIf { now - it.second < 120_000 }?.first
        val resp = cached?.copyOf() ?: run {
            val s = openTunnel("8.8.8.8", 53) ?: return
            s.use {
                val o = it.getOutputStream(); o.write(byteArrayOf((q.size shr 8).toByte(), q.size.toByte())); o.write(q); o.flush()
                val i = it.getInputStream(); val h = ByteArray(2); readFully(i, h)
                val r = ByteArray(((h[0].toInt() and 0xff) shl 8) or (h[1].toInt() and 0xff)); readFully(i, r)
                dnsCache[key] = r.copyOf() to now; r
            }
        }
        resp[0] = q[0]; resp[1] = q[1]                                  // même identifiant que la question
        val total = 28 + resp.size; val p = ByteArray(total)
        ipHeader(p, total, 17, dst, src)
        p16(p, 20, 53); p16(p, 22, sp); p16(p, 24, 8 + resp.size)
        System.arraycopy(resp, 0, p, 28, resp.size)
        val c = csum(p, 20, 8 + resp.size, pseudo(dst, src, 17, 8 + resp.size)); p16(p, 26, if (c == 0) 0xffff else c)
        write(p)
    }

    private fun readFully(i: java.io.InputStream, b: ByteArray) { var o = 0; while (o < b.size) { val n = i.read(b, o, b.size - o); if (n < 0) throw java.io.EOFException(); o += n } }
}
