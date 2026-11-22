package com.atlasglobe.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.*

/** Globe filaire blanc, dessiné en code (0 Mo d'image). rotation en radians. */
@Composable
fun GlobeCanvas(rotation: Float, size: Dp = 140.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f * 0.96f
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val w = (this.size.minDimension / 140f).coerceAtLeast(1f) * 1.4f
        val tilt = 0.35f
        fun proj(lat: Float, lon: Float): Triple<Float, Float, Float> {
            val x = cos(lat) * sin(lon)
            val y0 = sin(lat)
            val z0 = cos(lat) * cos(lon)
            val y = y0 * cos(tilt) - z0 * sin(tilt)
            val z = y0 * sin(tilt) + z0 * cos(tilt)
            return Triple(c.x + x * r, c.y - y * r, z)
        }
        drawCircle(Color.White, r, c, style = Stroke(w * 1.3f))
        val steps = 48
        // Méridiens
        for (i in 0 until 12) {
            val lon = rotation + i * (PI.toFloat() / 6f)
            val p = Path(); var pen = false
            for (s in 0..steps) {
                val lat = -PI.toFloat() / 2 + s * PI.toFloat() / steps
                val (x, y, z) = proj(lat, lon)
                if (z > 0) { if (pen) p.lineTo(x, y) else { p.moveTo(x, y); pen = true } } else pen = false
            }
            drawPath(p, Color.White, style = Stroke(w))
        }
        // Parallèles
        for (j in -4..4) {
            val lat = j * (PI.toFloat() / 10f)
            val p = Path(); var pen = false
            for (s in 0..steps * 2) {
                val lon = s * (2f * PI.toFloat() / (steps * 2))
                val (x, y, z) = proj(lat, lon)
                if (z > 0) { if (pen) p.lineTo(x, y) else { p.moveTo(x, y); pen = true } } else pen = false
            }
            drawPath(p, Color.White, style = Stroke(w))
        }
    }
}

@Composable
fun rememberGlobeRotation(durationMs: Int = 12000): State<Float> {
    val t = rememberInfiniteTransition(label = "globe")
    return t.animateFloat(
        0f, 2f * PI.toFloat(),
        infiniteRepeatable(tween(durationMs, easing = LinearEasing)), label = "rot"
    )
}

@Composable
fun SpinningGlobe(size: Dp = 140.dp, modifier: Modifier = Modifier) {
    val rot by rememberGlobeRotation()
    GlobeCanvas(rot, size, modifier)
}
