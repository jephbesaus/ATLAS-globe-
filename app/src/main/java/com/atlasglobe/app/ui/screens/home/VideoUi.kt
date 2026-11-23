package com.atlasglobe.app.ui.screens.home

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.atlasglobe.app.data.Post
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Miniature d'une vidéo (image extraite du fichier local, sinon fond noir) avec bouton lecture. */
@Composable
fun VideoThumb(post: Post, onOpen: () -> Unit) {
    val thumb by produceState<Bitmap?>(null, post.mediaPath) {
        value = withContext(Dispatchers.IO) {
            post.mediaPath?.let { p -> runCatching { val r = MediaMetadataRetriever(); try { r.setDataSource(p); r.getFrameAtTime(0) } finally { r.release() } }.getOrNull() }
        }
    }
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color(0xFF111111)).clickable { onOpen() }, contentAlignment = Alignment.Center) {
        thumb?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        Box(Modifier.size(64.dp).background(Color(0x99000000), CircleShape).border(2.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.PlayArrow, "Lire", tint = Color.White, modifier = Modifier.size(36.dp)) }
    }
}

/** Lecteur plein écran. Le bouton Retour du téléphone le ferme. */
@Composable
fun VideoDialog(post: Post, onClose: () -> Unit) {
    val uri: Uri? = post.mediaPath?.let { Uri.fromFile(File(it)) } ?: post.mediaUrl?.let { Uri.parse(it) }
    if (uri == null) { LaunchedEffect(Unit) { onClose() }; return }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { c ->
                FrameLayout(c).apply {
                    val v = VideoView(c)
                    addView(v, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER))
                    val mc = MediaController(c); mc.setAnchorView(v); v.setMediaController(mc)
                    v.setVideoURI(uri); v.setOnPreparedListener { it.start() }
                }
            })
        }
    }
}
