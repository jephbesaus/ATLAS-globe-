package com.atlasglobe.app.ui.screens

import android.os.SystemClock
import com.atlasglobe.app.ui.components.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atlasglobe.app.data.ProfileStore
import com.atlasglobe.app.security.Biometric
import com.atlasglobe.app.ui.components.SpinningGlobe
import com.atlasglobe.app.ui.theme.Atlas
import kotlinx.coroutines.delay

/** Verrou : PIN (avec temporisation après 5 erreurs) ou empreinte. */
@Composable
fun LockScreen(store: ProfileStore, onUnlocked: () -> Unit) {
    val ctx = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var wait by remember { mutableLongStateOf(0L) }
    val bio = remember { store.biometricEnabled && Biometric.available(ctx) }

    fun wallNow() = System.currentTimeMillis()
    LaunchedEffect(Unit) {
        if (bio && store.lockUntil <= wallNow()) Biometric.prompt(ctx) { store.failCount = 0; onUnlocked() }
    }
    LaunchedEffect(Unit) {
        while (true) { wait = ((store.lockUntil - wallNow()) / 1000).coerceAtLeast(0); delay(500) }
    }
    val locked = wait > 0

    fun submit() {
        if (locked || pin.length < 4) return
        if (store.verifyPin(pin)) { store.failCount = 0; onUnlocked() }
        else {
            val f = store.failCount + 1; store.failCount = f
            if (f >= 5) store.lockUntil = wallNow() + 30_000L * (f - 4)  // 30 s, 60 s, 90 s...
            error = true; pin = ""
        }
    }

    Column(Modifier.fillMaxSize().background(Atlas.Black).statusBarsPadding().navigationBarsPadding()
        .padding(horizontal = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(48.dp))
        SpinningGlobe(84.dp)
        Spacer(Modifier.height(12.dp))
        Text("Entrez votre PIN", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.height(18.dp)) {
            repeat(6) { i ->
                Box(Modifier.size(14.dp).border(1.dp, Color.White, CircleShape)
                    .background(if (i < pin.length) Color.White else Color.Transparent, CircleShape))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            when { locked -> "Trop d'erreurs. Réessayez dans ${wait}s"; error -> "PIN incorrect"; else -> " " },
            color = Atlas.Alert, fontSize = 13.sp, modifier = Modifier.height(20.dp))
        Spacer(Modifier.weight(1f))

        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))
        rows.forEach { r ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                r.forEach { d -> Key(d) { if (pin.length < 6 && !locked) { pin += d; error = false } } }
            }
            Spacer(Modifier.height(14.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            if (bio) KeyIcon(Icons.Default.Fingerprint) {
                if (!locked) Biometric.prompt(ctx) { store.failCount = 0; onUnlocked() } }
            else KeyIcon(Icons.Default.Backspace) { pin = pin.dropLast(1) }
            Key("0") { if (pin.length < 6 && !locked) { pin += "0"; error = false } }
            KeyIcon(Icons.Default.Check, filled = true) { submit() }
        }
        if (bio) {
            Spacer(Modifier.height(10.dp))
            Icon(Icons.Default.Backspace, "Effacer", tint = Atlas.Grey,
                modifier = Modifier.size(22.dp).clickable { pin = pin.dropLast(1) })
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Key(label: String, onClick: () -> Unit) {
    Box(Modifier.size(70.dp).border(1.dp, Atlas.Line, CircleShape).clickable { onClick() },
        contentAlignment = Alignment.Center) { Text(label, color = Color.White, fontSize = 26.sp) }
}

@Composable
private fun KeyIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, filled: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.size(70.dp).background(if (filled) Color.White else Color.Transparent, CircleShape)
        .clickable { onClick() }, contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = if (filled) Color.Black else Color.White, modifier = Modifier.size(28.dp))
    }
}
