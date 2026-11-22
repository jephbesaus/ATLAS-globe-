package com.atlasglobe.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atlasglobe.app.data.ImageUtil
import com.atlasglobe.app.ui.theme.Atlas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Image locale décodée en tâche de fond, réduite pour économiser la mémoire. */
@Composable
fun rememberLocalBitmap(path: String?, maxPx: Int = 1080): State<Bitmap?> =
    produceState<Bitmap?>(null, path, maxPx) { value = withContext(Dispatchers.IO) { ImageUtil.decodeScaled(path, maxPx) } }

@Composable
fun Avatar(name: String, photoPath: String?, size: Dp) {
    val bmp by rememberLocalBitmap(photoPath, 256)
    Box(Modifier.size(size).clip(CircleShape).background(Atlas.Card), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(name.trim().split(" ").filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1).uppercase() },
            color = Color.White, fontSize = (size.value * 0.36f).sp)
    }
}

fun timeAgo(t: Long): String {
    val m = (System.currentTimeMillis() - t) / 60000
    return when {
        m < 1 -> "à l'instant"
        m < 60 -> "il y a $m min"
        m < 1440 -> "il y a ${m / 60} h"
        else -> "il y a ${m / 1440} j"
    }
}
