package com.atlasglobe.app.reseller

import android.Manifest
import com.atlasglobe.app.ui.components.Text
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atlasglobe.app.connect.AtlasConfig
import com.atlasglobe.app.data.ChatRepo
import com.atlasglobe.app.mesh.MeshPolicy
import com.atlasglobe.app.mesh.ResellerState
import com.atlasglobe.app.security.Biometric
import com.atlasglobe.app.ui.components.SpinningGlobe
import com.atlasglobe.app.ui.screens.register.Perms
import com.atlasglobe.app.ui.screens.register.WhiteButton
import com.atlasglobe.app.ui.theme.Atlas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

private fun clock(ms: Long): String { val s = ms / 1000; val d = s / 86400; return (if (d > 0) "$d j " else "") + "%02d:%02d:%02d".format((s % 86400) / 3600, (s % 3600) / 60, s % 60) }

/** ESPACE REVENDEUR WI-FI : visible seulement si le panel admin a approuvé le compte. */
@Composable
fun ResellerHome() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val running = ResellerServer.running
    var saver by remember { mutableStateOf(false) }          // mode écran noir
    var testing by remember { mutableStateOf(false) }
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { MeshPolicy.refresh(ctx); tick = System.currentTimeMillis(); delay(3000) } }

    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) ResellerServer.start(ctx) }
    fun open() {
        when {
            !Perms.locationOn(ctx) -> Perms.locationSettings(ctx)
            Build.VERSION.SDK_INT >= 33 && !Perms.has(ctx, arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)) -> perm.launch(Manifest.permission.NEARBY_WIFI_DEVICES)
            else -> ResellerServer.start(ctx)
        }
    }
    fun speedTest() { testing = true
        scope.launch { val mbps = withContext(Dispatchers.IO) { runCatching {
            val t0 = System.nanoTime(); var n = 0L
            URL(AtlasConfig.SPEEDTEST_URL).openStream().use { i -> val b = ByteArray(32768); while (true) { val r = i.read(b); if (r < 0) break; n += r; if (System.nanoTime() - t0 > 8e9) break } }
            (n * 8.0 / ((System.nanoTime() - t0) / 1e9) / 1e6).toFloat() }.getOrDefault(0f) }
            ResellerState.speed = mbps; testing = false } }

    // Mode écran noir : luminosité minimale, écran allumé
    val activity = remember { Biometric.activity(ctx) }
    DisposableEffect(saver) {
        val w = activity?.window
        if (saver && w != null) { val a = w.attributes; a.screenBrightness = 0.01f; w.attributes = a; w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        onDispose { if (w != null) { val a = w.attributes; a.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE; w.attributes = a; w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) } }
    }
    if (saver) {
        BackHandler { saver = false }
        Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { detectTapGestures(onDoubleTap = { saver = false }) }, contentAlignment = Alignment.Center) {
            Text("Partage actif · ${ResellerServer.clients.size} client(s)\nDouble-touchez pour quitter", color = Color(0xFF333333), fontSize = 12.sp, textAlign = TextAlign.Center)
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize().background(Atlas.Black).statusBarsPadding(), contentPadding = PaddingValues(20.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Revendeur Wi-Fi", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp)); Icon(Icons.Filled.Verified, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
            Text("Compte approuvé par Atlas", color = Atlas.Grey, fontSize = 13.sp, modifier = Modifier.padding(bottom = 20.dp))

            Column(Modifier.fillMaxWidth().border(1.dp, if (running) Color.White else Atlas.Line, RoundedCornerShape(8.dp)).padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (running) SpinningGlobe(100.dp)
                Text(if (running) ResellerState.name else "Connexion fermée", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                Text(if (running) "${ResellerServer.clients.size}/${ResellerState.slots} places utilisées · visible par les clients autour de vous" else ResellerServer.status.ifEmpty { "Les clients ne peuvent pas vous trouver" },
                    color = if (running) Atlas.Grey else (if (ResellerServer.status.isNotEmpty()) Atlas.Alert else Atlas.Grey), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp))
                Spacer(Modifier.height(8.dp))
                if (!running) { WhiteButton("Ouvrir ma connexion", true) { open() }
                    Text("Avant : coupez le partage de connexion d'Android et gardez la localisation activée.", color = Atlas.Grey, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp)) }
                else { WhiteButton("Mode écran noir (économie)", true) { saver = true }; Spacer(Modifier.height(10.dp))
                    Text("Fermer ma connexion", color = Atlas.Alert, fontSize = 15.sp, modifier = Modifier.clickableNoRipple { ResellerServer.stop() }.padding(10.dp)) }
            }
            Spacer(Modifier.height(20.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("Batterie", "${MeshPolicy.batteryPct} %" + if (MeshPolicy.charging) " · charge" else "", Modifier.weight(1f), MeshPolicy.batteryPct < 20 && !MeshPolicy.charging)
                Stat("Température", "%.0f °C".format(MeshPolicy.tempC), Modifier.weight(1f), MeshPolicy.tempC >= 40f)
                Stat("Vitesse", if (ResellerState.speed > 0) "%.1f Mb/s".format(ResellerState.speed) else "—", Modifier.weight(1f), false)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("Places", "${ResellerState.slots}", Modifier.weight(1f), ResellerState.slots == 0)
                Stat("Note", if (ResellerState.rating > 0) "%.1f / 5".format(ResellerState.rating) else "Pas encore", Modifier.weight(1f), false)
            }
            if (MeshPolicy.tempC >= 40f) Text("Téléphone chaud : le nombre de clients est réduit. Retirez la coque et laissez-le à l'air.", color = Atlas.Alert, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
            if (MeshPolicy.batteryPct < 20 && !MeshPolicy.charging) Text("Batterie faible : branchez votre power bank.", color = Atlas.Alert, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Je suis en déplacement", color = Color.White, fontSize = 16.sp)
                    Text("Limite à 3 clients, qui doivent rester près de vous", color = Atlas.Grey, fontSize = 12.sp) }
                Switch(ResellerState.mobile, { ResellerState.mobile = it }, colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = Color.White,
                    uncheckedThumbColor = Atlas.Grey, uncheckedTrackColor = Atlas.Card, uncheckedBorderColor = Atlas.Grey)) }
            Spacer(Modifier.height(12.dp))
            OutlinedButton({ if (!testing) speedTest() }, shape = RoundedCornerShape(4.dp), modifier = Modifier.fillMaxWidth().height(48.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White)) { Text(if (testing) "Mesure en cours…" else "Tester ma vitesse (données mobiles)", color = Color.White) }
            Text("Posez votre téléphone là où le signal est le meilleur, gardez-le en charge et sans coque.", color = Atlas.Grey, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
            Spacer(Modifier.height(24.dp))
            Text("Clients connectés", color = Atlas.Grey, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
            if (ResellerServer.clients.isEmpty()) Text("Aucun client pour le moment.", color = Atlas.Grey, fontSize = 14.sp)
        }
        items(ResellerServer.clients, key = { it.tid }) { c ->
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(ChatRepo.nameOf(c.user), color = Color.White, fontSize = 15.sp)
                    Text("Reste ${clock(c.remainingMs)}", color = Atlas.Grey, fontSize = 12.sp) }
                Text("%.1f Mo".format(c.usedBytes / 1e6), color = Color.White, fontSize = 13.sp) }
            HorizontalDivider(color = Atlas.Line)
        }
        item { Spacer(Modifier.height(30.dp)) }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier, warn: Boolean) {
    Column(modifier.border(1.dp, if (warn) Atlas.Alert else Atlas.Line, RoundedCornerShape(6.dp)).padding(12.dp)) {
        Text(label, color = Atlas.Grey, fontSize = 11.sp); Text(value, color = if (warn) Atlas.Alert else Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit) = this.pointerInput(Unit) { detectTapGestures { onClick() } }
