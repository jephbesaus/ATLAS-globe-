package com.atlasglobe.app.ui.screens.call

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.atlasglobe.app.call.CallManager
import com.atlasglobe.app.call.CallManager.State
import com.atlasglobe.app.data.ChatRepo
import com.atlasglobe.app.ui.components.SpinningGlobe
import com.atlasglobe.app.ui.components.Text
import com.atlasglobe.app.ui.theme.Atlas

@Composable
private fun Round(icon: ImageVector, label: String, filled: Boolean, red: Boolean = false, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(68.dp).background(if (red) Atlas.Alert else if (filled) Color.White else Color.Transparent, CircleShape)
            .border(if (filled || red) 0.dp else 1.5.dp, Color.White, CircleShape).clickable { onClick() }, contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = if (filled && !red) Color.Black else Color.White, modifier = Modifier.size(30.dp)) }
        Text(label, color = Atlas.Grey, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Écran d'appel, devant toute l'application. */
@Composable
fun CallOverlay() {
    val ctx = LocalContext.current
    val st = CallManager.state
    if (st == State.IDLE) return
    BackHandler { /* l'appel se termine avec le bouton rouge */ }
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) CallManager.accept(ctx) else CallManager.reject(ctx) }
    fun hasMic() = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    Column(Modifier.fillMaxSize().background(Atlas.Black).statusBarsPadding().navigationBarsPadding().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(60.dp))
        SpinningGlobe(120.dp)
        Spacer(Modifier.height(28.dp))
        Text(ChatRepo.nameOf(CallManager.peer), color = Color.White, fontSize = 28.sp)
        Text(when (st) { State.ACTIVE -> "%02d:%02d".format(CallManager.seconds / 60, CallManager.seconds % 60); else -> CallManager.info }, color = Atlas.Grey, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.weight(1f))
        when (st) {
            State.INCOMING -> Row(horizontalArrangement = Arrangement.spacedBy(56.dp)) {
                Round(Icons.Filled.CallEnd, "Refuser", false, red = true) { CallManager.reject(ctx) }
                Round(Icons.Filled.Call, "Répondre", true) { if (hasMic()) CallManager.accept(ctx) else mic.launch(Manifest.permission.RECORD_AUDIO) } }
            State.ACTIVE -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                    Round(if (CallManager.muted) Icons.Filled.MicOff else Icons.Filled.Mic, if (CallManager.muted) "Micro coupé" else "Micro", CallManager.muted) { CallManager.toggleMute() }
                    Round(Icons.Filled.VolumeUp, "Haut-parleur", CallManager.speaker) { CallManager.toggleSpeaker() } }
                Spacer(Modifier.height(32.dp)); Round(Icons.Filled.CallEnd, "Raccrocher", false, red = true) { CallManager.end(ctx) } }
            else -> Round(Icons.Filled.CallEnd, "Annuler", false, red = true) { CallManager.end(ctx) }
        }
        Spacer(Modifier.height(30.dp))
    }
}
