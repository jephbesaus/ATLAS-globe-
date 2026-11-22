package com.atlasglobe.app.ui.screens

import androidx.compose.animation.core.*
import com.atlasglobe.app.ui.components.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atlasglobe.app.ui.components.GlobeCanvas
import com.atlasglobe.app.ui.components.rememberGlobeRotation
import com.atlasglobe.app.ui.theme.Atlas
import kotlinx.coroutines.delay

/** Globe petit -> grand, il tourne, "Bienvenue dans Atlas Globe", puis disparaît. */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val scale = remember { Animatable(0.1f) }
    val textAlpha = remember { Animatable(0f) }
    val screenAlpha = remember { Animatable(1f) }
    val rot by rememberGlobeRotation(6000)

    LaunchedEffect(Unit) {
        scale.animateTo(1f, tween(1400, easing = FastOutSlowInEasing))
        textAlpha.animateTo(1f, tween(700))
        delay(1100)
        screenAlpha.animateTo(0f, tween(500))
        onDone()
    }
    Box(Modifier.fillMaxSize().background(Atlas.Black).alpha(screenAlpha.value),
        contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GlobeCanvas(rot, 220.dp, Modifier.scale(scale.value))
            Spacer(Modifier.height(28.dp))
            Text("Bienvenue dans", color = Color.White, fontSize = 16.sp,
                modifier = Modifier.alpha(textAlpha.value))
            Text("Atlas Globe", color = Color.White, fontSize = 30.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.alpha(textAlpha.value))
        }
    }
}
