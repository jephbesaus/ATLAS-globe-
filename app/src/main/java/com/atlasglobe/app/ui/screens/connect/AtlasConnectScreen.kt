package com.atlasglobe.app.ui.screens.connect

import android.app.Activity
import android.net.VpnService
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atlasglobe.app.connect.AcTariff
import com.atlasglobe.app.connect.AtlasConfig
import com.atlasglobe.app.connect.AtlasConnectManager
import com.atlasglobe.app.connect.AtlasConnectManager.Status
import com.atlasglobe.app.ui.components.SpinningGlobe
import com.atlasglobe.app.ui.components.Text
import com.atlasglobe.app.ui.screens.register.LineField
import com.atlasglobe.app.ui.screens.register.WhiteButton
import com.atlasglobe.app.ui.theme.Atlas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun fc(n: Int) = "%,d FC".format(n).replace(',', ' ')

@Composable
fun AtlasConnectScreen(onOpenChat: (String) -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { AtlasConnectManager.init(ctx) }
    var welcome by remember { mutableStateOf(false) }
    when {
        !AtlasConnectManager.activated || welcome -> TokenStage(welcome) { welcome = it }
        else -> HomeStage(onOpenChat)
    }
}

// ---------------- 1. Le client colle son token ----------------
@Composable
private fun TokenStage(welcome: Boolean, setWelcome: (Boolean) -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }; var err by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(Atlas.Black).statusBarsPadding().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        SpinningGlobe(if (busy || welcome) 150.dp else 110.dp)
        Spacer(Modifier.height(24.dp))
        when {
            welcome -> { Text("Félicitations, vous êtes connecté dans l'espace Atlas Connect", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                LaunchedEffect(Unit) { delay(1800); setWelcome(false) } }
            busy -> Text("Vérification du token…", color = Color.White, fontSize = 16.sp)
            else -> {
                Text("Atlas Connect", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("Collez le token que vous a remis Atlas", color = Atlas.Grey, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp, bottom = 20.dp))
                LineField(token, { token = it.trim(); err = "" }, "Atlas-XXXX-XXXX-XXXX-XXXX", error = err.isNotEmpty())
                if (err.isNotEmpty()) Text(err, color = Atlas.Alert, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                Spacer(Modifier.height(24.dp))
                WhiteButton("Continuer", token.startsWith("Atlas", ignoreCase = true) && token.length >= 12) {
                    busy = true
                    scope.launch { val e = withContext(Dispatchers.IO) { AtlasConnectManager.activate(ctx, token) }
                        busy = false; if (e == null) setWelcome(true) else err = e } }
            }
        }
    }
}

// ---------------- 2. Bouton power ----------------
@Composable
private fun HomeStage(onOpenChat: (String) -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var tariffs by remember { mutableStateOf(false) }
    val st = AtlasConnectManager.state
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { AtlasConnectManager.refresh(ctx) } }
    val vpn = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) scope.launch(Dispatchers.IO) { AtlasConnectManager.connect(ctx) } }

    if (tariffs && st != null) { TariffStage(st.tariffs, st.balance, onOpenChat) { tariffs = false }; return }

    fun power() {
        val s = AtlasConnectManager.state ?: return
        when {
            AtlasConnectManager.status == Status.ON || AtlasConnectManager.status == Status.CONNECTING -> scope.launch(Dispatchers.IO) { AtlasConnectManager.disconnect() }
            s.suspended -> Toast.makeText(ctx, "Ce compte est suspendu", Toast.LENGTH_SHORT).show()
            !s.hasConfig -> Toast.makeText(ctx, "Votre configuration n'est pas encore activée par Atlas", Toast.LENGTH_LONG).show()
            !(s.expiresAt > System.currentTimeMillis()) -> { Toast.makeText(ctx, "Aucun abonnement actif : choisissez un tarif", Toast.LENGTH_LONG).show(); tariffs = true }
            else -> { val i = VpnService.prepare(ctx); if (i != null) vpn.launch(i) else scope.launch(Dispatchers.IO) { AtlasConnectManager.connect(ctx) } }
        }
    }

    val status = AtlasConnectManager.status; val on = status == Status.ON
    Box(Modifier.fillMaxSize().background(Atlas.Black).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Atlas Connect", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Row(Modifier.border(1.dp, Color.White, RoundedCornerShape(16.dp)).clickable { tariffs = true }.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Receipt, null, tint = Color.White, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Tarif", color = Color.White, fontSize = 13.sp) }
        }
        Column(Modifier.fillMaxSize().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(190.dp).clip(CircleShape).border(if (on) 0.dp else 3.dp, Color.White, CircleShape).background(if (on) Color.White else Color.Transparent).clickable { power() }, contentAlignment = Alignment.Center) {
                if (status == Status.CONNECTING) SpinningGlobe(130.dp) else Icon(Icons.Filled.PowerSettingsNew, "Power", tint = if (on) Color.Black else Color.White, modifier = Modifier.size(90.dp)) }
            Spacer(Modifier.height(28.dp))
            Text(when (status) { Status.ON -> "Connecté"; Status.CONNECTING -> "Connexion…"; else -> "Déconnecté" }, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            if (AtlasConnectManager.message.isNotEmpty() && status == Status.OFF) Text(AtlasConnectManager.message, color = Atlas.Alert, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp, 8.dp))
            Spacer(Modifier.height(22.dp))
            if (st != null) {
                Text(st.name, color = Atlas.Grey, fontSize = 14.sp)
                Text("Solde : ${fc(st.balance)}", color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
                Text(if (st.expiresAt > System.currentTimeMillis()) "Abonnement actif jusqu'au " + SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(Date(st.expiresAt)) else "Aucun abonnement actif",
                    color = Atlas.Grey, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                if (!st.hasConfig) Text("Votre configuration n'est pas encore activée par Atlas.", color = Atlas.Grey, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp, 14.dp))
            }
        }
    }
}

// ---------------- 3. Tarifs ----------------
@Composable
private fun TariffStage(list: List<AcTariff>, balance: Int, onOpenChat: (String) -> Unit, back: () -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    BackHandler { back() }
    var confirm by remember { mutableStateOf<AcTariff?>(null) }; var msg by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Atlas.Black).statusBarsPadding()) {
        Text("Tarif", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp))
        Text("Votre solde : ${fc(AtlasConnectManager.state?.balance ?: balance)}", color = Atlas.Grey, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 20.dp))
        if (msg.isNotEmpty()) Text(msg, color = if (msg.startsWith("Abonnement")) Color.White else Atlas.Alert, fontSize = 14.sp, modifier = Modifier.padding(20.dp))
        if (list.isEmpty()) Text("Aucun tarif disponible pour le moment.", color = Atlas.Grey, modifier = Modifier.padding(20.dp))
        LazyColumn { items(list, key = { it.id }) { t ->
            Row(Modifier.fillMaxWidth().clickable { confirm = t }.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(t.label, color = Color.White, fontSize = 17.sp); Text("${t.days} jour(s) de connexion", color = Atlas.Grey, fontSize = 12.sp) }
                Text(fc(t.price), color = Color.White, fontWeight = FontWeight.SemiBold) }
            HorizontalDivider(color = Atlas.Line) } }
        Spacer(Modifier.weight(1f))
        Column(Modifier.padding(20.dp).navigationBarsPadding()) { WhiteButton("Recharger mon solde", true) { AtlasConfig.install(ctx); onOpenChat(AtlasConfig.ASSISTANT_ID) } }
    }
    confirm?.let { t -> AlertDialog(onDismissRequest = { confirm = null }, containerColor = Atlas.Card, title = { Text("Payer ${fc(t.price)} ?", color = Color.White) },
        text = { Text("${t.label} sera payé avec votre solde (${fc(AtlasConnectManager.state?.balance ?: balance)}).", color = Atlas.Grey) },
        confirmButton = { TextButton({ if (!busy) { busy = true; confirm = null
            scope.launch { val e = withContext(Dispatchers.IO) { AtlasConnectManager.buy(ctx, t) }; busy = false
                msg = e ?: "Abonnement activé : ${t.label}."; if (e == null) back() } } }) { Text("Payer", color = Color.White) } },
        dismissButton = { TextButton({ confirm = null }) { Text("Annuler", color = Atlas.Grey) } }) }
}
