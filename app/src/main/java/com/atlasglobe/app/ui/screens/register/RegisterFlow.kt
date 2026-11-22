package com.atlasglobe.app.ui.screens.register

import android.app.Activity
import com.atlasglobe.app.ui.components.Text
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.atlasglobe.app.data.*
import com.atlasglobe.app.ui.components.SpinningGlobe
import com.atlasglobe.app.ui.theme.Atlas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val S_FORM = 0
private const val S_BT = 1
private const val S_LOC = 2
private const val S_NOTIF = 3
private const val S_BATT = 4
private const val S_GEO = 5
private const val S_PHOTO = 6
private const val S_LOADING = 7

@Composable
fun RegisterFlow(onFinished: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { ProfileStore(ctx) }
    val scope = rememberCoroutineScope()

    var step by remember { mutableIntStateOf(S_FORM) }
    var nom by remember { mutableStateOf("") }
    var prenom by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var continent by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }
    var province by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<String?>(null) }

    fun next() {
        var s = step + 1
        if (s == S_NOTIF && Build.VERSION.SDK_INT < 33) s++
        step = s
    }
    fun back() {
        var s = step - 1
        if (s == S_NOTIF && Build.VERSION.SDK_INT < 33) s--
        if (s >= 0) step = s
    }
    // Retour système : pas de retour pendant le chargement
    BackHandler(enabled = step in S_BT..S_PHOTO && step != S_GEO) { back() }

    Box(Modifier.fillMaxSize().background(Atlas.Black).statusBarsPadding().navigationBarsPadding().imePadding()) {
        when (step) {
            S_FORM -> FormStep(nom, { nom = it }, prenom, { prenom = it }, pin, { pin = it.filter(Char::isDigit).take(6) },
                phone, { phone = it.filter { c -> c.isDigit() || c == '+' }.take(16) }, onNext = { next() })

            S_BT -> PermissionStep(Reqs.BT) { next() }
            S_LOC -> PermissionStep(Reqs.LOC) { next() }
            S_NOTIF -> PermissionStep(Reqs.NOTIF) { next() }
            S_BATT -> PermissionStep(Reqs.BATT) { next() }

            S_GEO -> GeoStep(
                onDone = { co, pa, pr, ci -> continent = co; country = pa; province = pr; city = ci; next() },
                onExit = { back() })

            S_PHOTO -> PhotoStep(photo,
                onPicked = { uri -> scope.launch {
                    photo = withContext(Dispatchers.IO) { ImageUtil.saveProfilePhoto(ctx, uri) } } },
                onNext = { next() })

            S_LOADING -> LoadingStep {
                store.save(
                    Profile(nom.trim(), prenom.trim(), phone, continent, country, province, city, photo,
                        ProfileStore.newAtlasId()), pin)
                onFinished()
            }
        }
    }
}

/* ---------------- Étape 1 : formulaire ---------------- */
@Composable
private fun FormStep(
    nom: String, onNom: (String) -> Unit, prenom: String, onPrenom: (String) -> Unit,
    pin: String, onPin: (String) -> Unit, phone: String, onPhone: (String) -> Unit, onNext: () -> Unit
) {
    val phoneOk = phone.isEmpty() || phone.count { it.isDigit() } >= 8
    val valid = nom.isNotBlank() && prenom.isNotBlank() && pin.length >= 4 && phoneOk
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(36.dp))
        SpinningGlobe(96.dp)
        Spacer(Modifier.height(8.dp))
        Text("Atlas", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(32.dp))
        LineField(nom, onNom, "Nom")
        Spacer(Modifier.height(10.dp))
        LineField(prenom, onPrenom, "Prénom")
        Spacer(Modifier.height(10.dp))
        LineField(pin, onPin, "Code PIN (4 à 6 chiffres)", KeyboardType.NumberPassword, password = true)
        Spacer(Modifier.height(10.dp))
        LineField(phone, onPhone, "Numéro de téléphone (facultatif)", KeyboardType.Phone, error = !phoneOk)
        Spacer(Modifier.height(8.dp))
        Text("Le numéro permet à vos contacts de vous retrouver et de récupérer votre compte.",
            color = Atlas.Grey, fontSize = 12.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        WhiteButton("Continuer", valid, onNext)
        Spacer(Modifier.height(24.dp))
    }
}

/* ---------------- Étapes autorisations (réutilisé par le blocage permanent) ---------------- */
@Composable
fun PermissionStep(r: Req, onNext: () -> Unit) {
    val ctx = LocalContext.current
    var active by remember { mutableStateOf(r.isActive(ctx)) }
    val startedActive = remember { active }
    var refusals by remember { mutableIntStateOf(0) }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val ob = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) active = r.isActive(ctx) }
        owner.lifecycle.addObserver(ob); onDispose { owner.lifecycle.removeObserver(ob) }
    }

    val btLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        active = r.isActive(ctx)
    }
    val askBt = { btLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)); Unit }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.any { !it }) refusals++
        active = r.isActive(ctx)
        r.afterPerms(ctx, askBt)
    }
    val askPerms: (Array<String>) -> Unit = { permLauncher.launch(it) }

    LaunchedEffect(active) { if (active && !startedActive) { delay(500); onNext() } }

    Column(Modifier.fillMaxSize().background(Atlas.Black).padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (r.imageRes != null) {
            Image(painterResource(r.imageRes), null, Modifier.size(120.dp).clip(CircleShape).border(1.dp, Color.White, CircleShape),
                contentScale = ContentScale.Crop)
        } else Box(Modifier.size(120.dp).border(1.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
            Icon(r.icon, null, tint = Color.White, modifier = Modifier.size(56.dp))
        }
        Spacer(Modifier.height(28.dp))
        Text(r.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(r.message, color = Atlas.Grey, fontSize = 15.sp, textAlign = TextAlign.Center)
        if (refusals > 0 && !active) {
            Spacer(Modifier.height(12.dp))
            Text("Autorisation refusée : ouvrez les réglages d'Atlas pour l'accorder.",
                color = Atlas.Alert, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(36.dp))
        if (active) WhiteButton("Continuer", true, onNext)
        else WhiteButton(if (refusals > 0) "Ouvrir les réglages" else "Activer") {
            if (refusals > 0) Perms.appSettings(ctx) else r.onActivate(ctx, askPerms, askBt)
        }
    }
}

/* ---------------- Pays → province → ville ---------------- */
@Composable
private fun GeoStep(onDone: (String, String, String, String) -> Unit, onExit: () -> Unit) {
    var stage by remember { mutableIntStateOf(0) } // 0 continent, 1 pays, 2 province, 3 ville
    var continent by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }
    var province by remember { mutableStateOf("") }
    var otherCity by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var freeProvince by remember { mutableStateOf("") }
    var freeCity by remember { mutableStateOf("") }
    val isDrc = country == Geo.DRC

    BackHandler {
        when {
            otherCity -> otherCity = false
            stage > 0 -> { stage--; query = "" }
            else -> onExit()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(24.dp))
        Text(
            when (stage) { 0 -> "Votre continent"; 1 -> "Votre pays"
                2 -> if (isDrc) "Votre province" else "Votre région et ville"
                else -> "Votre ville" },
            color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(12.dp))

        when {
            stage == 0 -> LazyColumn { items(Geo.continents) { c -> ListRow(c) { continent = c; stage = 1 } } }

            stage == 1 -> {
                Box(Modifier.padding(horizontal = 12.dp)) { LineField(query, { query = it }, "Rechercher un pays") }
                val list = (Geo.countries[continent] ?: emptyList()).filter { it.contains(query, ignoreCase = true) }
                LazyColumn { items(list) { c -> ListRow(c) {
                    country = c; query = ""; stage = 2 } } }
            }

            stage == 2 && isDrc -> {
                Box(Modifier.padding(horizontal = 12.dp)) { LineField(query, { query = it }, "Rechercher une province") }
                val list = Geo.drc.keys.filter { it.contains(query, ignoreCase = true) }
                LazyColumn { items(list) { p -> ListRow(p) { province = p; query = ""; stage = 3 } } }
            }

            stage == 2 -> Column(Modifier.padding(horizontal = 28.dp)) {
                LineField(freeProvince, { freeProvince = it }, "Province / région (facultatif)")
                Spacer(Modifier.height(10.dp))
                LineField(freeCity, { freeCity = it }, "Ville")
                Spacer(Modifier.height(28.dp))
                WhiteButton("Continuer", freeCity.isNotBlank()) {
                    onDone(continent, country, freeProvince.trim(), freeCity.trim()) }
            }

            stage == 3 && !otherCity -> {
                val list = (Geo.drc[province] ?: emptyList()) + "Autre ville…"
                LazyColumn { items(list) { c -> ListRow(c) {
                    if (c == "Autre ville…") otherCity = true else onDone(continent, country, province, c) } } }
            }

            else -> Column(Modifier.padding(horizontal = 28.dp)) {
                LineField(freeCity, { freeCity = it }, "Nom de votre ville")
                Spacer(Modifier.height(28.dp))
                WhiteButton("Continuer", freeCity.isNotBlank()) {
                    onDone(continent, country, province, freeCity.trim()) }
            }
        }
    }
}

/* ---------------- Photo ---------------- */
@Composable
private fun PhotoStep(photo: String?, onPicked: (android.net.Uri) -> Unit, onNext: () -> Unit) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPicked(uri)
    }
    val bmp = remember(photo) { ImageUtil.loadBitmap(photo) }
    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text("Votre photo de profil", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(28.dp))
        Box(Modifier.size(160.dp).clip(CircleShape).border(1.dp, Color.White, CircleShape)
            .clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            contentAlignment = Alignment.Center) {
            if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Default.CameraAlt, null, tint = Color.White, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text("Touchez pour choisir", color = Atlas.Grey, fontSize = 13.sp)
        Spacer(Modifier.height(36.dp))
        WhiteButton(if (bmp != null) "Se connecter" else "Se connecter sans photo", true, onNext)
    }
}

/* ---------------- Chargement ---------------- */
@Composable
private fun LoadingStep(onDone: () -> Unit) {
    var dots by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        repeat(8) { dots = (it % 3) + 1; delay(300) }
        onDone()
    }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        SpinningGlobe(180.dp)
        Spacer(Modifier.height(32.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Loading", color = Color.White, fontSize = 18.sp)
            Dots(dots)
        }
    }
}
