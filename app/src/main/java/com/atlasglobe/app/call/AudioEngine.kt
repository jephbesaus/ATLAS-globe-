package com.atlasglobe.app.call

import android.content.Context
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Audio de l'appel : micro 8 kHz, compression G.711 µ-law (sans bibliothèque externe), envoi UDP vers le relais Atlas.
 * Environ 64 kbit/s dans chaque sens (0,5 Mo par minute).
 */
class AudioEngine(private val ctx: Context, private val host: String, private val port: Int, tokenHex: String) {
    private val token = ByteArray(8) { tokenHex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    @Volatile private var running = false
    @Volatile var muted = false
    private var sock: DatagramSocket? = null
    private var rec: AudioRecord? = null
    private var track: AudioTrack? = null

    fun start() {
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        val s = DatagramSocket().also { it.receiveBufferSize = 16384 }
        s.connect(InetAddress.getByName(host), port); sock = s; running = true
        Thread({ hello() }, "call-hello").also { it.isDaemon = true }.start()
        Thread({ capture() }, "call-mic").also { it.isDaemon = true }.start()
        Thread({ play() }, "call-play").also { it.isDaemon = true }.start()
    }

    fun setSpeaker(on: Boolean) { am.isSpeakerphoneOn = on }

    fun stop() {
        running = false
        runCatching { sock?.close() }; runCatching { rec?.stop(); rec?.release() }; runCatching { track?.stop(); track?.release() }
        runCatching { am.isSpeakerphoneOn = false; am.mode = AudioManager.MODE_NORMAL }
    }

    private fun hello() {     // garde la route NAT ouverte et fait connaître notre adresse au relais
        val p = ByteArray(9); p[0] = 1; token.copyInto(p, 1)
        while (running) { runCatching { sock?.send(DatagramPacket(p, p.size)) }; try { Thread.sleep(2000) } catch (e: InterruptedException) { return } }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun capture() {
        val min = AudioRecord.getMinBufferSize(8000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val r = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, 8000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 3200)); rec = r
        runCatching { if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(r.audioSessionId)?.enabled = true }
        runCatching { if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(r.audioSessionId)?.enabled = true }
        r.startRecording()
        val pcm = ShortArray(160); var seq = 0
        val pkt = ByteArray(11 + 160); pkt[0] = 2; token.copyInto(pkt, 1)
        while (running) {
            var got = 0
            while (got < 160 && running) { val n = r.read(pcm, got, 160 - got); if (n <= 0) break; got += n }
            if (got < 160 || muted) continue
            pkt[9] = (seq shr 8).toByte(); pkt[10] = seq.toByte(); seq = (seq + 1) and 0xffff
            for (i in 0 until 160) pkt[11 + i] = enc(pcm[i].toInt())
            runCatching { sock?.send(DatagramPacket(pkt, pkt.size)) }
        }
    }

    private fun play() {
        val min = AudioTrack.getMinBufferSize(8000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(8000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(maxOf(min, 4800)).setTransferMode(AudioTrack.MODE_STREAM).build()
        track = t; t.play()
        val buf = ByteArray(700); val pk = DatagramPacket(buf, buf.size); val out = ShortArray(700)
        while (running) {
            try { sock?.receive(pk) } catch (e: Exception) { if (!running) return; continue }
            if (pk.length < 4 || buf[0].toInt() != 2) continue
            val n = pk.length - 3; for (i in 0 until n) out[i] = dec(buf[3 + i])
            t.write(out, 0, n)
        }
    }

    // G.711 µ-law
    private fun enc(s0: Int): Byte {
        var s = s0; val sign = if (s < 0) { s = -s; 0x80 } else 0
        if (s > 32635) s = 32635; s += 0x84
        var e = 7; var m = 0x4000; while (e > 0 && (s and m) == 0) { e--; m = m shr 1 }
        return (sign or (e shl 4) or ((s shr (e + 3)) and 0x0f)).inv().toByte()
    }
    private fun dec(b: Byte): Short {
        val x = b.toInt().inv() and 0xff; val e = (x shr 4) and 7
        val v = (((x and 0x0f) shl 3) + 0x84 shl e) - 0x84
        return (if ((x and 0x80) != 0) -v else v).toShort()
    }
}
