package com.atlasglobe.app.ui.screens.connect

import android.widget.Toast
import com.atlasglobe.app.ui.components.Text
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.atlasglobe.app.connect.*
import com.atlasglobe.app.data.ChatRepo
import com.atlasglobe.app.mesh.BleMesh
import com.atlasglobe.app.mesh.Offer
import com.atlasglobe.app.mesh.ResellerDirectory
import com.atlasglobe.app.ui.components.SpinningGlobe
import com.atlasglobe.app.ui.screens.register.WhiteButton
import com.atlasglobe.app.ui.theme.Atlas
import kotlinx.coroutines.delay

private val NEAR = Color(0xFF2ECC71)    // vert : revendeur proche (seule couleur ajoutée, demandée pour cet écran)

private fun zoneColor(o: Offer) = when (o.zone) { 0 -> NEAR; 1 -> Color.White; else -> Atlas.Alert }
private fun dist(o: Offer) = "environ ${o.meters.toInt().coerceIn(1, 100)} m"

private fun clock(ms: Long): String {
    val s = ms / 1000; val d = s / 86400; val h = (s % 86400) / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return (if (d > 0) "${d} j " else "") + "%02d:%02d:%02d".format(h, m, sec)
}
private fun statusLabel(s: String) = when (s) { "awaiting" -> "En attente de paiement"; "approved" -> "Paiement approuvé"
    "active" -> "En cours"; "ended" -> "Terminé"; "refused" -> "Refusé"; else -> s }

@Composable
fun ConnectivityScreen(onOpenChat: (String) -> Unit) {
    val ctx = LocalContext.current
    var stage by remember { mutableIntStateOf(0) }          // 0 accueil, 1 recherche, 2 résultats, 3 forfaits
    var selected by remember { mutableStateOf<Offer?>(null) }
    BackHandler(enabled = stage != 0) { stage = if (stage == 3) 2 else 0 }

    Box(Modifier.fillMaxSize().background(Atlas.Black).statusBarsPadding()) {
        when (stage) {
            0 -> HomeStage(onSearch = { BleMesh.searchResellers(ctx, 10_000); stage = 1 })
            1 -> { LaunchedEffect(Unit) { delay(10_500); stage = 2 }
                Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    SpinningGlobe(220.dp); Spacer(Modifier.height(28.dp))
                    Text("Recherche des connexions Atlas autour de vous", color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center)
                    Text("Restez à l'endroit où vous êtes", color = Atlas.Grey, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                } }
            2 -> ResultsStage(onPick = { o ->
                if (o.full) Toast.makeText(ctx, "Équipe complète : choisissez un autre revendeur", Toast.LENGTH_SHORT).show()
                else { selected = o; stage = 3 } }, onRetry = { BleMesh.searchResellers(ctx, 10_000); stage = 1 })
            3 -> selected?.let { DetailStage(it, onOpenChat) }
        }
    }
}

// ---------------- Accueil ----------------
@Composable
private fun HomeStage(onSearch: () -> Unit) {
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    var rateOrder by remember { mutableStateOf<Order?>(null) }
    val g = ConnectStore.grant
    val rem = remember(now, g) { ConnectStore.remainingMs() }
    val unrated = ConnectStore.lastEndedUnrated()

    LazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, contentPadding = PaddingValues(24.dp)) {
        item {
            Spacer(Modifier.height(8.dp)); SpinningGlobe(150.dp); Spacer(Modifier.height(16.dp))
            Text("Atlas Connect", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("Trouvez une connexion Internet près de vous", color = Atlas.Grey, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp, bottom = 24.dp))
            WhiteButton("Vérifier la disponibilité Internet", true, onSearch)
            Spacer(Modifier.height(20.dp))
        }
        if (g != null) item {
            Column(Modifier.fillMaxWidth().border(1.dp, Color.White, RoundedCornerShape(6.dp)).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val plan = Tariffs.find(g.planId)
                Text(g.resellerName, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(plan?.label ?: "", color = Atlas.Grey, fontSize = 13.sp)
                if (ConnectStore.isActive) {
                    Text(clock(rem), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                    Text("Temps restant", color = Atlas.Grey, fontSize = 12.sp)
                } else ConnectionPanel()
            }
            Spacer(Modifier.height(16.dp))
        }
        if (unrated != null) item {
            Row(Modifier.fillMaxWidth().border(1.dp, Atlas.Grey, RoundedCornerShape(6.dp)).clickable { rateOrder = unrated }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Notez ${unrated.resellerName}", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("Une note par achat", color = Atlas.Grey, fontSize = 12.sp) }
                Icon(Icons.Filled.Star, null, tint = Color.White) }
            Spacer(Modifier.height(16.dp))
        }
        if (ConnectStore.orders.isNotEmpty()) {
            item { Text("Mes achats", color = Atlas.Grey, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) }
            items(ConnectStore.orders.take(8), key = { it.id }) { o ->
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("${Tariffs.find(o.planId)?.label ?: o.planId} · ${o.resellerName}", color = Color.White, fontSize = 14.sp)
                        Text(statusLabel(o.status), color = if (o.status == "refused") Atlas.Alert else Atlas.Grey, fontSize = 12.sp) }
                    Text(Tariffs.fc(o.amountFc), color = Color.White, fontSize = 14.sp) }
                HorizontalDivider(color = Atlas.Line)
            }
        }
    }
    rateOrder?.let { o -> var stars by remember { mutableIntStateOf(0) }
        AlertDialog(onDismissRequest = { rateOrder = null }, containerColor = Atlas.Card, title = { Text("Notez ${o.resellerName}", color = Color.White) },
            text = { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { (1..5).forEach { i ->
                Icon(if (i <= stars) Icons.Filled.Star else Icons.Filled.StarBorder, "$i", tint = Color.White, modifier = Modifier.size(36.dp).clickable { stars = i }) } } },
            confirmButton = { TextButton({ if (stars > 0) { ConnectStore.rate(o.id, stars); rateOrder = null
                Toast.makeText(ctx, "Merci pour votre note", Toast.LENGTH_SHORT).show() } }) { Text("Envoyer", color = Color.White) } },
            dismissButton = { TextButton({ rateOrder = null }) { Text("Plus tard", color = Atlas.Grey) } }) }
}

// ---------------- Résultats ----------------
@Composable
private fun ResultsStage(onPick: (Offer) -> Unit, onRetry: () -> Unit) {
    val offers = ResellerDirectory.offers.sortedWith(compareBy<Offer>({ it.zone }, { -it.rating }))
    val rec = ResellerDirectory.recommended()
    Column(Modifier.fillMaxSize()) {
        Text("Connexions disponibles", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp))
        if (offers.isEmpty()) Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("Aucun revendeur Atlas trouvé près de vous", color = Color.White, fontSize = 17.sp, textAlign = TextAlign.Center)
            Text("Rapprochez-vous d'un point Atlas ou d'un agent de votre avenue, puis relancez.", color = Atlas.Grey, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 12.dp))
            WhiteButton("Relancer la recherche", true, onRetry)
        } else LazyColumn {
            if (rec != null) item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).background(Atlas.Card, RoundedCornerShape(10.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    SpinningGlobe(40.dp); Spacer(Modifier.width(12.dp))
                    Text("Je te recommande ${rec.name.ifEmpty { "ce revendeur" }}, ${dist(rec)} de toi.", color = Color.White, fontSize = 14.sp) }
            }
            items(offers, key = { it.short }) { o ->
                Column(Modifier.fillMaxWidth().clickable { onPick(o) }.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(o.name.ifEmpty { "Atlas ${o.short.take(4).uppercase()}" }, color = zoneColor(o), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(6.dp)); Icon(Icons.Filled.Verified, "Officiel", tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.weight(1f))
                        if (o.rating > 0) { Icon(Icons.Filled.Star, null, tint = Color.White, modifier = Modifier.size(14.dp)); Text(" %.1f".format(o.rating), color = Color.White, fontSize = 13.sp) } }
                    Text("Revendeur officiel · ${dist(o)}" + if (o.speed > 0) " · %.0f Mb/s".format(o.speed) else "", color = Atlas.Grey, fontSize = 13.sp)
                    Text(if (o.full) "Équipe complète : plus de place" else "${o.slots} place${if (o.slots > 1) "s" else ""} libre${if (o.slots > 1) "s" else ""}",
                        color = if (o.full) Atlas.Alert else Atlas.Grey, fontSize = 12.sp)
                    if (!o.full) Text(when (o.zone) { 0 -> "Cette connexion peut être bonne : touchez pour demander l'accès."
                        1 -> "Distance moyenne : rapprochez-vous pour une meilleure vitesse."; else -> "Un peu loin : la vitesse risque d'être faible." },
                        color = Atlas.Grey, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                }
                HorizontalDivider(color = Atlas.Line)
            }
        }
    }
}

// ---------------- Forfaits + paiement ----------------
@Composable
private fun DetailStage(o: Offer, onOpenChat: (String) -> Unit) {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var sel by remember { mutableStateOf<Plan?>(null) }
    val plans = if (tab == 0) Tariffs.time else Tariffs.data
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Row(verticalAlignment = Alignment.CenterVertically) {
                Text(o.name.ifEmpty { "Revendeur Atlas" }, color = zoneColor(o), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp)); Icon(Icons.Filled.Verified, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                Text("Revendeur officiel · ${dist(o)}", color = Atlas.Grey, fontSize = 13.sp) } }
        Row(Modifier.fillMaxWidth()) { listOf("Temps", "Forfait").forEachIndexed { i, t ->
            Column(Modifier.weight(1f).clickable { tab = i; sel = null }, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(t, color = if (tab == i) Color.White else Atlas.Grey, fontWeight = if (tab == i) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.padding(vertical = 10.dp))
                Box(Modifier.fillMaxWidth().height(if (tab == i) 2.dp else 1.dp).background(if (tab == i) Color.White else Atlas.Line)) } } }
        LazyColumn(Modifier.weight(1f)) {
            items(plans, key = { it.id }) { p ->
                val on = sel?.id == p.id
                Column(Modifier.fillMaxWidth().clickable { sel = p }.background(if (on) Atlas.Card else Color.Transparent).padding(horizontal = 20.dp, vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Rester connecté ${if (p.kind == "time") "pendant " else ": "}${p.label}", color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Text(Tariffs.fc(p.priceFc), color = Color.White, fontWeight = FontWeight.SemiBold) }
                    if (on) Text(p.note, color = Atlas.Grey, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp)) }
                HorizontalDivider(color = Atlas.Line)
            }
        }
        Column(Modifier.padding(16.dp).navigationBarsPadding()) {
            if (o.id.isEmpty()) Text("Profil du revendeur en cours de chargement : restez à portée.", color = Atlas.Grey, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
            WhiteButton(sel?.let { "Payer ${Tariffs.fc(it.priceFc)}" } ?: "Choisissez une offre", sel != null && o.id.isNotEmpty()) {
                val plan = sel!!
                val order = ConnectStore.createOrder(plan, o.id, o.name)
                AtlasConfig.install(ctx)
                ChatRepo.send(AtlasConfig.ASSISTANT_ID, "text",
                    "COMMANDE ${order.id}\nOffre : ${plan.label} (${Tariffs.fc(plan.priceFc)}) [${plan.id}]\nRevendeur : ${o.name}\nJe vais payer ma connexion. Envoyez-moi les numéros de paiement.", null)
                onOpenChat(AtlasConfig.ASSISTANT_ID)
            }
        }
    }
}


/** Connexion invisible : le globe tourne, puis "Félicitations, vous êtes connecté". */
@Composable
private fun ConnectionPanel() {
    val ctx = LocalContext.current
    val st = ConnectionManager.state
    val vpn = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { if (it.resultCode == Activity.RESULT_OK) ConnectionManager.connect(ctx) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 8.dp)) {
        when (st) {
            ConnectionManager.State.ASKING, ConnectionManager.State.JOINING, ConnectionManager.State.TUNNEL -> {
                SpinningGlobe(90.dp); Spacer(Modifier.height(8.dp)); Text(ConnectionManager.message, color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center) }
            ConnectionManager.State.CONNECTED -> Text(ConnectionManager.message, color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center)
            else -> {
                Text(if (st == ConnectionManager.State.ERROR) ConnectionManager.message else "Paiement approuvé", color = if (st == ConnectionManager.State.ERROR) Atlas.Alert else Color.White,
                    fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 10.dp))
                WhiteButton("Se connecter", true) { val i = VpnService.prepare(ctx); if (i != null) vpn.launch(i) else ConnectionManager.connect(ctx) }
            }
        }
    }
}
