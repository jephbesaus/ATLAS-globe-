package com.atlasglobe.app.ui.screens

import androidx.compose.foundation.background
import com.atlasglobe.app.ui.components.Text
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.atlasglobe.app.ui.components.SpinningGlobe
import com.atlasglobe.app.ui.theme.Atlas

@Composable
fun PlaceholderScreen(title: String) {
    Box(Modifier.fillMaxSize().background(Atlas.Black), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SpinningGlobe(90.dp)
            Spacer(Modifier.height(16.dp))
            Text(title, color = Color.White)
        }
    }
}
