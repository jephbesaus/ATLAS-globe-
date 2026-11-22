package com.atlasglobe.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Atlas {
    val Black = Color(0xFF000000)
    val White = Color(0xFFFFFFFF)
    val Grey = Color(0xFF8A8A8A)
    val Line = Color(0xFF262626)
    val Card = Color(0xFF111111)
    val Alert = Color(0xFFE53935) // seul rouge autorisé : alertes
}

private val scheme = darkColorScheme(
    primary = Atlas.White, onPrimary = Atlas.Black,
    background = Atlas.Black, onBackground = Atlas.White,
    surface = Atlas.Black, onSurface = Atlas.White,
    surfaceVariant = Atlas.Card, onSurfaceVariant = Atlas.Grey,
    error = Atlas.Alert
)

@Composable
fun AtlasTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = scheme, content = content)
