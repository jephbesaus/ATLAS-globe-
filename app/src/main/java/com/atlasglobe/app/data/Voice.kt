package com.atlasglobe.app.data

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.UUID

/** Note vocale compressée (AAC mono 16 kHz, 24 kbit/s : environ 3 Ko par seconde, adaptée au mesh). */
class VoiceRecorder(private val ctx: Context) {
    private var rec: MediaRecorder? = null
    private var file: File? = null
    var paused by mutableStateOf(false); private set
    private var startedAt = 0L; private var acc = 0L

    fun start(): Boolean = try {
        val f = File(ctx.filesDir, "voice/${UUID.randomUUID()}.m4a").also { it.parentFile?.mkdirs() }
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC); r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC); r.setAudioChannels(1)
        r.setAudioSamplingRate(16000); r.setAudioEncodingBitRate(24000)
        r.setOutputFile(f.absolutePath); r.prepare(); r.start()
        rec = r; file = f; startedAt = System.currentTimeMillis(); acc = 0; paused = false; true
    } catch (e: Exception) { rec = null; false }

    fun elapsedMs(): Long = acc + if (paused) 0 else System.currentTimeMillis() - startedAt
    fun togglePause() {
        val r = rec ?: return
        runCatching {
            if (paused) { r.resume(); startedAt = System.currentTimeMillis(); paused = false }
            else { r.pause(); acc += System.currentTimeMillis() - startedAt; paused = true }
        }
    }
    /** Termine et renvoie (chemin, durée). */
    fun finish(): Pair<String, Long>? {
        val d = elapsedMs(); val r = rec ?: return null
        val ok = runCatching { r.stop() }.isSuccess; r.release(); rec = null
        return if (ok && file != null && d > 700) file!!.absolutePath to d else { file?.delete(); null }
    }
    fun cancel() { runCatching { rec?.stop() }; rec?.release(); rec = null; file?.delete() }
}

object VoicePlayer {
    private var mp: MediaPlayer? = null
    var playingId by mutableStateOf<String?>(null); private set
    fun toggle(id: String, path: String) {
        if (playingId == id) { stop(); return }
        stop()
        mp = runCatching { MediaPlayer().apply { setDataSource(path); prepare(); start()
            setOnCompletionListener { stop() } } }.getOrNull()
        if (mp != null) playingId = id
    }
    fun stop() { runCatching { mp?.stop() }; mp?.release(); mp = null; playingId = null }
}
