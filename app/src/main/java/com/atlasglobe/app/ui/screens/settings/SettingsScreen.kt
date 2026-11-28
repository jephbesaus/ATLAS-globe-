package com.atlasglobe.app.ui.screens.settings

import android.app.ActivityManager
import com.atlasglobe.app.ui.components.Text
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.atlasglobe.app.connect.AtlasConfig
import com.atlasglobe.app.connect.ConnectStore
import com.atlasglobe.app.connect.Tariffs
import com.atlasglobe.app.data.*
import com.atlasglobe.app.mesh.MeshMode
import com.atlasglobe.app.mesh.MeshPolicy
import com.atlasglobe.app.mesh.MeshStats
import com.atlasglobe.app.notify.NotificationHelper
import com.atlasglobe.app.security.AppLockState
import com.atlasglobe.app.security.Biometric
import com.atlasglobe.app.settings.Certification
import com.atlasglobe.app.settings.SettingsStore
import com.atlasglobe.app.ui.components.Avatar
import com.atlasglobe.app.ui.screens.register.LineField
import com.atlasglobe.app.ui.screens.register.Perms
import com.atlasglobe.app.ui.screens.register.WhiteButton
import com.atlasglobe.app.ui.theme.Atlas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID

@Composable private fun Title(t: String) = Text(t, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp))
@Composable private fun Group(t: String) = Text(t, color = Atlas.Grey, fontSize = 12.sp, modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 6.dp))
@Composable private fun Item(title: String, sub: String = "", color: Color = Color.White, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable { onClick() }) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) { Text(title, color = color, fontSize = 16.sp); if (sub.isNotEmpty()) Text(sub, color = Atlas.Grey, fontSize = 12.sp) }
        HorizontalDivider(color = Atlas.Line) }
}
@Composable private fun Toggle(title: String, sub: String, on: Boolean, enabled: Boolean = true, set: (Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(title, color = Color.White, fontSize = 16.sp); if (sub.isNotEmpty()) Text(sub, color = Atlas.Grey, fontSize = 12.sp) }
            Switch(on, set, enabled = enabled, colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = Color.White,
                uncheckedThumbColor = Atlas.Grey, uncheckedTrackColor = Atlas.Card, uncheckedBorderColor = Atlas.Grey)) }
        HorizontalDivider(color = Atlas.Line) }
}
@Composable private fun Body(content: @Composable ColumnScope.() -> Unit) =
    LazyColumn(Modifier.fillMaxSize().background(Atlas.Black).statusBarsPadding()) { item { Column { content(); Spacer(Modifier.height(30.dp)) } } }

@Composable
fun SettingsScreen(onOpenChat: (String) -> Unit, onOpenReseller: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { ProfileStore(ctx) }
    var page by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("home") }
    BackHandler(enabled = page != "home") { page = "home" }
    when (page) {
        "home" -> Home(store, go = { page = it }, onOpenReseller)
        "profile" -> ProfilePage(store)
        "privacy" -> PrivacyPage()
        "notifs" -> NotifsPage()
        "mesh" -> MeshPage()
        "connect" -> ConnectPage(onOpenReseller)
        "wallet" -> WalletPage()
        "battery" -> BatteryPage()
        "chats" -> ChatsPage()
        "storage" -> StoragePage()
        "blocked" -> BlockedPage()
        "invite" -> InvitePage(store)
        "security" -> SecurityPage(store)
        "cert" -> CertPage(onOpenChat, go = { page = it })
        "atlasconnect" -> com.atlasglobe.app.ui.screens.connect.AtlasConnectScreen(onOpenChat)
        "help" -> HelpPage()
        "about" -> AboutPage(store)
        else -> if (page.startsWith("identity:")) IdentityPage(page.removePrefix("identity:").toInt(), onOpenChat, back = { page = "cert" })
    }
}

@Composable
private fun Home(store: ProfileStore, go: (String) -> Unit, onOpenReseller: () -> Unit) {
    val me = store.load()
    Body {
        Row(Modifier.fillMaxWidth().clickable { go("profile") }.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar("${me?.prenom} ${me?.nom}", me?.photoPath, 64.dp); Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text("${me?.prenom} ${me?.nom}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    if (SettingsStore.certLevel > 0) { Spacer(Modifier.width(6.dp)); Icon(Icons.Filled.Verified, "Certifié", tint = Color.White, modifier = Modifier.size(18.dp)) } }
                Text(me?.atlasId ?: "", color = Atlas.Grey, fontSize = 12.sp)
                if (SettingsStore.certLevel > 0) Text("Certifié ${Certification.names[SettingsStore.certLevel]}", color = Atlas.Grey, fontSize = 12.sp) } }
        HorizontalDivider(color = Atlas.Line)
        Group("COMPTE")
        Item("Profil", "Nom, photo, bio, confirmer le numéro et l'e-mail") { go("profile") }
        Item("Certification du compte", "Standard, Premium, Pro") { go("cert") }
        Item("Sécurité", "PIN, empreinte, autorisations") { go("security") }
        Item("Confidentialité", "Présence, numéro, e-mail, appels") { go("privacy") }
        Group("APPLICATION")
        Item("Notifications", "Un son différent pour chaque catégorie") { go("notifs") }
        Item("Discussions", "Fond d'écran, langue") { go("chats") }
        Item("Mesh", "Mode ${MeshPolicy.chosen.label} · ${MeshStats.nearby} appareils proches") { go("mesh") }
        Item("Batterie", "${MeshPolicy.batteryPct} % · ${"%.0f".format(MeshPolicy.tempC)} °C") { go("battery") }
        Item("Stockage", "Médias et nettoyage") { go("storage") }
        Group("CONNECTIVITÉ")
        Item("Mes achats Wi-Fi", "${ConnectStore.orders.size} achat(s)") { go("connect") }
        Item("Portefeuille Atlas", "Solde : ${com.atlasglobe.app.connect.WalletStore.balanceFc} FC") { go("wallet") }
        if (ResellerAccess.approved) Item("Espace Revendeur Wi-Fi", "Ouvrir ma connexion, clients, vitesse") { onOpenReseller() }
        Group("PLUS")
        Item("Contacts bloqués", "${ChatRepo.blocked.size}") { go("blocked") }
        Item("Inviter des amis", "Partager Atlas par Bluetooth") { go("invite") }
        Item("Aide") { go("help") }
        Item("À propos", "Qui sommes-nous, avantages, créateur") { go("about") }
        Item("Atlas Connect", "VPN Atlas") { go("atlasconnect") }
        Item("Verrouiller maintenant", "") { AppLockState.unlocked = false }
    }
}

// ---------------- Profil + confirmation du compte ----------------
@Composable
private fun ProfilePage(store: ProfileStore) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val me = remember { store.load()!! }
    var nom by remember { mutableStateOf(me.nom) }; var prenom by remember { mutableStateOf(me.prenom) }
    var bio by remember { mutableStateOf(store.bio) }; var phone by remember { mutableStateOf(me.phone) }
    var photo by remember { mutableStateOf(me.photoPath) }; var email by remember { mutableStateOf(store.email) }
    var code by remember { mutableStateOf("") }; var sent by remember { mutableStateOf(false) }; var info by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) scope.launch { photo = withContext(Dispatchers.IO) { ImageUtil.saveProfilePhoto(ctx, u) } } }
    Body {
        Title("Profil")
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.clickable { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Avatar("$prenom $nom", photo, 96.dp) }
            Text("Changer la photo", color = Atlas.Grey, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
        Column(Modifier.padding(horizontal = 28.dp, vertical = 12.dp)) {
            LineField(nom, { nom = it }, "Nom"); LineField(prenom, { prenom = it }, "Prénom"); LineField(bio, { bio = it.take(140) }, "Bio (facultatif)")
            Spacer(Modifier.height(20.dp))
            WhiteButton("Enregistrer", nom.isNotBlank() && prenom.isNotBlank()) {
                store.updateProfile(nom.trim(), prenom.trim(), phone, photo); store.bio = bio.trim(); Toast.makeText(ctx, "Profil enregistré", Toast.LENGTH_SHORT).show() } }
        Group("CONFIRMER MON COMPTE")
        Column(Modifier.padding(horizontal = 28.dp)) {
            if (store.numberConfirmed) Text("Félicitations, votre numéro est enregistré.", color = Color.White, fontSize = 15.sp)
            else {
                LineField(phone, { phone = it.filter { c -> c.isDigit() || c == '+' } }, "Numéro de téléphone", KeyboardType.Phone)
                LineField(email, { email = it.trim() }, "Adresse e-mail", KeyboardType.Email)
                Spacer(Modifier.height(14.dp))
                if (!sent) WhiteButton(if (busy) "Envoi…" else "Envoyer le code", phone.length >= 8 && email.contains("@") && !busy) {
                    if (!Net.isOnline(ctx)) { info = "Vous devez être connecté à Internet pour confirmer votre compte"; return@WhiteButton }
                    if (!Api.configured()) { info = "Le serveur Atlas n'est pas encore configuré"; return@WhiteButton }
                    busy = true; scope.launch { val r = withContext(Dispatchers.IO) { Api.post("/v1/verify/request", JSONObject().put("id", me.atlasId).put("phone", phone).put("email", email)) }
                        busy = false; if (r != null) { sent = true; info = "Un code à 6 chiffres a été envoyé à $email. Veuillez le confirmer." } else info = "Envoi impossible. Réessayez." } }
                else { LineField(code, { code = it.filter(Char::isDigit).take(6) }, "Code à 6 chiffres", KeyboardType.NumberPassword)
                    Spacer(Modifier.height(14.dp))
                    WhiteButton(if (busy) "Vérification…" else "Confirmer", code.length == 6 && !busy) {
                        busy = true; scope.launch { val r = withContext(Dispatchers.IO) { Api.post("/v1/verify/confirm", JSONObject().put("id", me.atlasId).put("code", code)) }
                            busy = false; if (r?.optBoolean("ok") == true) { store.updateProfile(nom, prenom, phone, photo); store.email = email; store.numberConfirmed = true; info = "Félicitations, votre numéro a été enregistré." } else info = "Code incorrect." } } }
                if (info.isNotEmpty()) Text(info, color = Atlas.Grey, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp)) }
        }
    }
}

@Composable
private fun PrivacyPage() = Body {
    Title("Confidentialité")
    Toggle("Présence en ligne", "Les autres voient quand vous êtes actif", SettingsStore.presence) { SettingsStore.changePresence(it) }
    Toggle("Afficher mon numéro", "Visible sur votre profil", SettingsStore.showPhone) { SettingsStore.changeShowPhone(it) }
    Toggle("Afficher mon e-mail", "Sinon seul votre nom apparaît", SettingsStore.showEmail) { SettingsStore.changeShowEmail(it) }
    Toggle("Confirmations de lecture", "Désactivées : vous ne voyez pas non plus celles des autres", SettingsStore.readReceipts) { SettingsStore.changeReadReceipts(it) }
    Toggle("Autoriser les appels", "Désactivé : personne ne peut vous appeler", SettingsStore.allowCalls) { SettingsStore.changeAllowCalls(it) }
}

// ---------------- Notifications : un son par catégorie ----------------
@Composable
private fun NotifsPage() {
    val ctx = LocalContext.current
    var pick by remember { mutableStateOf<NotificationHelper.Category?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    Body {
        Title("Notifications")
        NotificationHelper.categories.forEach { k ->
            val snd = remember(refresh) { NotificationHelper.soundOf(ctx, k.id) }
            Item(k.label, NotificationHelper.sounds.firstOrNull { it.first == snd }?.second ?: "") { pick = k } }
        Item("Réglages Android", "Vibration, priorité, écran verrouillé") {
            ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    pick?.let { k -> AlertDialog(onDismissRequest = { pick = null }, containerColor = Atlas.Card, title = { Text(k.label, color = Color.White) },
        text = { Column { NotificationHelper.sounds.forEach { (id, label) ->
            Row(Modifier.fillMaxWidth().clickable { NotificationHelper.setSound(ctx, k.id, id); NotificationHelper.preview(ctx, id); refresh++ }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = Color.White, modifier = Modifier.weight(1f)); if (NotificationHelper.soundOf(ctx, k.id) == id) Icon(Icons.Filled.Check, null, tint = Color.White) } } } },
        confirmButton = { TextButton({ pick = null }) { Text("Terminer", color = Color.White) } }) }
}

@Composable
private fun MeshPage() {
    val ctx = LocalContext.current
    Body {
        Title("Mesh")
        MeshMode.values().forEach { m -> Column(Modifier.fillMaxWidth().clickable { MeshPolicy.setMode(ctx, m) }) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(m.label, color = Color.White, fontSize = 16.sp)
                    Text(when (m) { MeshMode.ACTIVE -> "Journée, avenue dense : messages plus rapides, plus de batterie"; MeshMode.BALANCED -> "Recommandé"
                        MeshMode.ECONOMY -> "Peu de batterie : recherche lente" }, color = Atlas.Grey, fontSize = 12.sp) }
                if (MeshPolicy.chosen == m) Icon(Icons.Filled.Check, null, tint = Color.White) }
            HorizontalDivider(color = Atlas.Line) } }
        Group("ÉTAT")
        Item("Mode appliqué", MeshPolicy.effective.label + if (MeshPolicy.effective != MeshPolicy.chosen) " (ajusté : batterie, chaleur ou nuit)" else "") {}
        Item("Appareils Atlas à proximité", "${MeshStats.nearby}") {}
        Item("Connexions actives", "${MeshStats.links}") {}
        Item("Relais des messages des autres", if (MeshPolicy.relay) "Actif" else "Arrêté (batterie sous 15 %)") {}
        if (MeshPolicy.paused) Text("Téléphone trop chaud : le mesh est en pause.", color = Atlas.Alert, fontSize = 13.sp, modifier = Modifier.padding(20.dp))
    }
}

@Composable
private fun ConnectPage(onOpenReseller: () -> Unit) = Body {
    Title("Mes achats Wi-Fi")
    if (ConnectStore.orders.isEmpty()) Text("Aucun achat pour le moment.", color = Atlas.Grey, modifier = Modifier.padding(20.dp))
    ConnectStore.orders.forEach { o -> Item("${Tariffs.find(o.planId)?.label ?: o.planId} · ${o.resellerName}", "${Tariffs.fc(o.amountFc)} · ${o.status}") {} }
    if (ResellerAccess.approved) { Group("REVENDEUR"); Item("Espace Revendeur Wi-Fi", "Ouvrir ma connexion, clients, vitesse") { onOpenReseller() } }
}

@Composable
private fun BatteryPage() {
    val ctx = LocalContext.current
    Body {
        Title("Batterie")
        Item("Batterie du téléphone", "${MeshPolicy.batteryPct} %" + if (MeshPolicy.charging) " · en charge" else "") {}
        Item("Température", "%.0f °C".format(MeshPolicy.tempC)) {}
        Item("Mode du mesh", "${MeshPolicy.effective.label} : réglé automatiquement selon la batterie, la chaleur et la nuit") {}
        Item("Consommation par application", "Voir la part d'Atlas dans Android") { ctx.startActivity(Intent(Intent.ACTION_POWER_USAGE_SUMMARY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        Item("Garder Atlas actif en arrière-plan", if (Perms.batteryOk(ctx)) "Autorisé" else "Touchez pour autoriser") { if (!Perms.batteryOk(ctx)) Perms.batteryRequest(ctx) }
        Text("Sous 20 % le mesh passe en mode Économie ; sous 15 % Atlas arrête de relayer les messages des autres.", color = Atlas.Grey, fontSize = 12.sp, modifier = Modifier.padding(20.dp))
    }
}

@Composable
private fun ChatsPage() {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) scope.launch { withContext(Dispatchers.IO) { ImageUtil.saveScaled(ctx, u, 1080, File(ctx.filesDir, "chat_bg.jpg"), 70) }?.let { SettingsStore.changeChatBg("photo:$it") } } }
    Body {
        Title("Discussions")
        Group("FOND DE LA DISCUSSION")
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            listOf("black" to Color.Black, "grey" to Color(0xFF2A2A2A), "white" to Color.White, "blue" to Color(0xFF0A2A5E)).forEach { (id, col) ->
                Box(Modifier.size(54.dp).background(col, CircleShape).border(if (SettingsStore.chatBg == id) 3.dp else 1.dp, if (SettingsStore.chatBg == id) Color.White else Atlas.Grey, CircleShape).clickable { SettingsStore.changeChatBg(id) }) } }
        Spacer(Modifier.height(12.dp))
        Item("Utiliser ma photo", "Choisir une image de la galerie") { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        Group("LANGUE")
        Row(Modifier.fillMaxWidth().clickable { com.atlasglobe.app.ui.components.Lang.set(ctx, "fr") }.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Français", color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f)); if (com.atlasglobe.app.ui.components.Lang.code == "fr") Icon(Icons.Filled.Check, null, tint = Color.White) }
        HorizontalDivider(color = Atlas.Line)
        Row(Modifier.fillMaxWidth().clickable { com.atlasglobe.app.ui.components.Lang.set(ctx, "sw") }.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Kiswahili", color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f)); if (com.atlasglobe.app.ui.components.Lang.code == "sw") Icon(Icons.Filled.Check, null, tint = Color.White) }
        HorizontalDivider(color = Atlas.Line)
    }
}

@Composable
private fun StoragePage() {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var size by remember { mutableLongStateOf(-1L) }
    fun measure() { scope.launch { size = withContext(Dispatchers.IO) { listOf("media", "voice").sumOf { File(ctx.filesDir, it).walkTopDown().filter { f -> f.isFile }.sumOf { f -> f.length() } } } } }
    LaunchedEffect(Unit) { measure() }
    Body {
        Title("Stockage")
        Item("Médias et notes vocales", if (size < 0) "Calcul…" else "%.1f Mo".format(size / 1e6)) {}
        Item("Supprimer les médias de plus de 30 jours", "Les messages restent, sans l'image ou la note vocale", Atlas.Alert) {
            scope.launch { withContext(Dispatchers.IO) { val lim = System.currentTimeMillis() - 30L * 86400_000
                listOf("media", "voice").forEach { d -> File(ctx.filesDir, d).walkTopDown().filter { it.isFile && it.lastModified() < lim }.forEach { it.delete() } } }
                measure(); Toast.makeText(ctx, "Nettoyage terminé", Toast.LENGTH_SHORT).show() } }
    }
}

@Composable
private fun BlockedPage() = Body {
    Title("Contacts bloqués")
    if (ChatRepo.blocked.isEmpty()) Text("Aucun contact bloqué.", color = Atlas.Grey, modifier = Modifier.padding(20.dp))
    ChatRepo.blocked.forEach { id -> Item(ChatRepo.nameOf(id), "Touchez pour débloquer") { ChatRepo.block(id, false) } }
}

@Composable
private fun InvitePage(store: ProfileStore) {
    val ctx = LocalContext.current
    Body {
        Title("Inviter des amis")
        Item("Partager mon lien d'invitation", "Message avec votre ID Atlas") {
            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,
                "Rejoins-moi sur Atlas Globe : discute sans Internet. Mon ID : ${store.load()?.atlasId}"), "Inviter").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        Item("Partager l'application par Bluetooth", "Sans mégas : envoie le fichier de l'application à un voisin") {
            runCatching { val src = File(ctx.applicationInfo.sourceDir); val out = File(ctx.cacheDir, "share/AtlasGlobe.apk").also { it.parentFile?.mkdirs() }
                src.copyTo(out, overwrite = true)
                val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", out)
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/vnd.android.package-archive").putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Partager Atlas").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        Text("Choisissez Bluetooth dans la liste. Le voisin doit autoriser l'installation d'applications de cette source.", color = Atlas.Grey, fontSize = 12.sp, modifier = Modifier.padding(20.dp))
    }
}

@Composable
private fun SecurityPage(store: ProfileStore) {
    val ctx = LocalContext.current
    var lock by remember { mutableStateOf(store.lockEnabled) }; var bio by remember { mutableStateOf(store.biometricEnabled) }
    var changing by remember { mutableStateOf(false) }
    val bioOk = remember { Biometric.available(ctx) }
    Body {
        Title("Sécurité")
        Toggle("Verrouiller Atlas avec le PIN", "Demandé à chaque ouverture", lock) { lock = it; store.lockEnabled = it }
        Toggle("Empreinte digitale", if (bioOk) "Déverrouiller avec l'empreinte" else "Aucune empreinte enregistrée sur ce téléphone", bio && bioOk, bioOk && lock) { bio = it; store.biometricEnabled = it }
        Item("Changer le code PIN") { changing = true }
        Item("Autorisations de l'application", "Bluetooth, localisation, micro, notifications") { Perms.appSettings(ctx) }
        Item("Verrouiller maintenant") { AppLockState.unlocked = false }
        Group("ZONE DANGEREUSE")
        var del by remember { mutableStateOf(false) }
        Item("Supprimer mon compte et mes données", "Efface tout sur ce téléphone. Impossible à annuler.", Atlas.Alert) { del = true }
        if (del) AlertDialog(onDismissRequest = { del = false }, containerColor = Atlas.Card, title = { Text("Tout supprimer ?", color = Color.White) },
            text = { Text("Votre identité, vos messages et vos achats seront effacés de ce téléphone.", color = Atlas.Grey) },
            confirmButton = { TextButton({ (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).clearApplicationUserData() }) { Text("Supprimer", color = Atlas.Alert) } },
            dismissButton = { TextButton({ del = false }) { Text("Annuler", color = Color.White) } })
    }
    if (changing) { var o by remember { mutableStateOf("") }; var n by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { changing = false }, containerColor = Atlas.Card, title = { Text("Changer le PIN", color = Color.White) },
            text = { Column { LineField(o, { o = it.filter(Char::isDigit).take(6) }, "PIN actuel", KeyboardType.NumberPassword, password = true)
                LineField(n, { n = it.filter(Char::isDigit).take(6) }, "Nouveau PIN (4 à 6 chiffres)", KeyboardType.NumberPassword, password = true) } },
            confirmButton = { TextButton({ if (n.length >= 4 && store.changePin(o, n)) { changing = false; Toast.makeText(ctx, "PIN modifié", Toast.LENGTH_SHORT).show() }
                else Toast.makeText(ctx, "PIN actuel incorrect", Toast.LENGTH_SHORT).show() }) { Text("Valider", color = Color.White) } },
            dismissButton = { TextButton({ changing = false }) { Text("Annuler", color = Atlas.Grey) } }) }
}

@Composable
private fun CertPage(onOpenChat: (String) -> Unit, go: (String) -> Unit) {
    Body {
        Title("Certification du compte")
        Text("Le badge prouve que votre identité est vérifiée. Vous envoyez votre pièce d'identité et un selfie ; après vérification et paiement, le badge est activé.",
            color = Atlas.Grey, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        val note = SettingsStore.certNote
        if (note.startsWith("pending:")) Text("Votre demande est en cours de vérification. Vous serez notifié.", color = Color.White, fontSize = 14.sp,
            modifier = Modifier.padding(20.dp).border(1.dp, Color.White, RoundedCornerShape(6.dp)).padding(14.dp))
        if (note.startsWith("rejected:")) Text("Demande refusée : ${note.removePrefix("rejected:")}", color = Atlas.Alert, fontSize = 14.sp,
            modifier = Modifier.padding(20.dp).border(1.dp, Atlas.Alert, RoundedCornerShape(6.dp)).padding(14.dp))
        (1..3).forEach { lvl ->
            val active = SettingsStore.certLevel == lvl
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp).border(if (active) 2.dp else 1.dp, if (active) Color.White else Atlas.Line, RoundedCornerShape(8.dp)).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Certification.names[lvl], color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (active) Text("ACTIF", color = Color.Black, fontSize = 11.sp, modifier = Modifier.background(Color.White, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) }
                Text(Certification.prices[lvl], color = Atlas.Grey, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
                Certification.perks[lvl].forEach { Text("•  $it", color = Color.White, fontSize = 14.sp, lineHeight = 20.sp) }
                if (!active && !SettingsStore.certNote.startsWith("pending:")) { Spacer(Modifier.height(14.dp)); WhiteButton("Demander ${Certification.names[lvl]}", true) { go("identity:$lvl") } } } }
    }
}

/** Vérification d'identité : pièce (recto, verso) puis selfie, envoyés au serveur Atlas pour contrôle manuel. */
@Composable
private fun IdentityPage(level: Int, onOpenChat: (String) -> Unit, back: () -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val files = androidx.compose.runtime.saveable.rememberSaveable(saver = androidx.compose.runtime.saveable.listSaver<androidx.compose.runtime.snapshots.SnapshotStateMap<String, String>, String>(
        save = { m -> m.entries.flatMap { listOf(it.key, it.value) } },
        restore = { l -> mutableStateMapOf<String, String>().also { m -> l.chunked(2).forEach { m[it[0]] = it[1] } } })) { mutableStateMapOf<String, String>() }
    var target by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }; var info by remember { mutableStateOf("") }
    val cam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) scope.launch { withContext(Dispatchers.IO) { ImageUtil.saveScaled(ctx, android.net.Uri.fromFile(File(ctx.cacheDir, "cam/$target.jpg")), 1400, File(ctx.filesDir, "id/$target.jpg"), 80) }
            ?.let { files[target] = it } } }
    fun capture(k: String) { target = k; val f = File(ctx.cacheDir, "cam/$k.jpg").also { it.parentFile?.mkdirs() }
        cam.launch(FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)) }
    fun send() {
        if (!Net.isOnline(ctx)) { info = "Vous devez être connecté à Internet pour envoyer votre demande"; return }
        if (!Api.configured()) { info = "Le serveur Atlas n'est pas encore configuré"; return }
        busy = true; info = ""
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                fun b64(k: String) = android.util.Base64.encodeToString(File(files[k]!!).readBytes(), android.util.Base64.NO_WRAP)
                Api.signed("POST", "/v1/identity", JSONObject().put("level", level).put("front", b64("front")).put("back", b64("back")).put("selfie", b64("selfie"))) }
            busy = false
            when {
                r.ok -> { files.values.forEach { File(it).delete() }; SettingsStore.changeCertNote("pending:$level")
                    AtlasConfig.install(ctx)
                    ChatRepo.send(AtlasConfig.ASSISTANT_ID, "text", "CERTIFICATION ${Certification.names[level]} (${Certification.prices[level]})\nMes documents d'identité sont envoyés. Envoyez-moi les numéros de paiement.", null)
                    Toast.makeText(ctx, "Demande envoyée", Toast.LENGTH_SHORT).show(); back() }
                r.code == 409 -> info = "Une demande est déjà en cours de vérification"
                else -> info = "Envoi impossible. Vérifiez votre connexion et réessayez." } }
    }
    Body {
        Title("Vérifier mon identité")
        Text("Niveau demandé : ${Certification.names[level]} (${Certification.prices[level]}). Posez la pièce sur une surface plane, bien éclairée, sans reflet : tout le texte doit être lisible.",
            color = Atlas.Grey, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp))
        listOf("front" to "Pièce d'identité : recto", "back" to "Pièce d'identité : verso", "selfie" to "Selfie (visage bien visible)").forEach { (k, label) ->
            val bmp = remember(files[k]) { ImageUtil.loadBitmap(files[k]) }
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                Text(label, color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(bottom = 6.dp))
                if (bmp != null) androidx.compose.foundation.Image(bmp.asImageBitmap(), null, Modifier.fillMaxWidth().heightIn(max = 190.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                Spacer(Modifier.height(6.dp)); WhiteButton(if (bmp == null) "Prendre la photo" else "Reprendre", true) { capture(k) } } }
        Column(Modifier.padding(20.dp)) {
            WhiteButton(if (busy) "Envoi…" else "Envoyer ma demande", files.size == 3 && !busy) { send() }
            if (info.isNotEmpty()) Text(info, color = Atlas.Alert, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
            Text("Vos images sont envoyées par connexion sécurisée à l'équipe Atlas, utilisées seulement pour la vérification, puis supprimées 30 jours après la décision.",
                color = Atlas.Grey, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp)) }
    }
}

@Composable
private fun HelpPage() = Body {
    Title("Aide")
    listOf("Comment écrire sans Internet ?" to "Activez le Bluetooth et la localisation, et restez près d'autres téléphones Atlas. Plus il y en a dans votre avenue, plus vos messages vont loin.",
        "Pourquoi mon message est « En attente » ?" to "Aucun voisin Atlas n'est à portée, ou vous n'avez pas encore croisé ce contact. Le message part dès qu'un téléphone passe près de vous.",
        "Comment obtenir Internet ?" to "Touchez le bouton Wi-Fi au centre, lancez la recherche, choisissez un revendeur proche et payez avec Atlas Assistant.",
        "Comment devenir revendeur ?" to "Contactez l'équipe Atlas. Votre compte doit être approuvé avant que l'espace Revendeur Wi-Fi apparaisse.",
        "Mon téléphone chauffe ou se vide vite" to "Passez le mesh en mode Économie dans Paramètres > Mesh. Atlas ralentit aussi tout seul quand le téléphone chauffe.").forEach { (q, a) ->
        Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) { Text(q, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold); Text(a, color = Atlas.Grey, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp)) }
        HorizontalDivider(color = Atlas.Line) }
}

@Composable private fun H(t: String) = Text(t, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 8.dp))
@Composable private fun P(t: String) = Text(t, color = Color(0xFFD0D0D0), fontSize = 15.sp, lineHeight = 23.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 3.dp))
@Composable private fun Bul(t: String) = Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) { Text("—  ", color = Color.White, fontSize = 15.sp); Text(t, color = Color(0xFFD0D0D0), fontSize = 15.sp, lineHeight = 22.sp) }

@Composable
private fun AboutPage(store: ProfileStore) {
    val ctx = LocalContext.current
    val ver = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrDefault("1.0") }
    Body {
        Column(Modifier.fillMaxWidth().padding(top = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            com.atlasglobe.app.ui.components.SpinningGlobe(150.dp)
            Text("Atlas Globe", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            Text("Le monde connecté. The world connected.", color = Atlas.Grey, fontSize = 14.sp) }

        H("Qui sommes-nous ?")
        P("Atlas Globe est une plateforme de communication et de connectivité née en République démocratique du Congo. Elle réunit deux services : une messagerie qui fonctionne même sans Internet, de téléphone à téléphone, et un marché de partage de connexion Internet entre voisins.")
        P("Notre projet est congolais dans son origine, et sans frontière dans son ambition.")

        H("Pourquoi Atlas Globe existe")
        P("Dans beaucoup de quartiers, Internet coûte cher, coupe souvent ou n'arrive pas. Pourtant les gens veulent rester en contact avec leurs proches, leur église, leur école, leur commerce.")
        P("Atlas Globe a été créé pour que personne ne soit isolé parce qu'il n'a plus de mégas. Quand il n'y a pas de réseau, les téléphones Atlas forment eux-mêmes un réseau. Quand il y a besoin d'Internet, un voisin équipé peut le partager, à un prix simple et connu à l'avance.")
        P("Notre ambition : commencer petit, quartier par quartier, pour grandir grand. D'abord Panzi et Bukavu, puis la RDC, puis d'autres pays.")

        H("Comment ça marche")
        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.atlasglobe.app.R.drawable.bluetooth_glass), null, Modifier.size(84.dp).clip(CircleShape).border(1.dp, Atlas.Grey, CircleShape))
            Spacer(Modifier.width(14.dp))
            Column { Text("Messagerie mesh", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("Vos messages, chiffrés, sautent de téléphone Atlas en téléphone Atlas jusqu'à leur destinataire, sans mégas. Plus il y a de membres dans votre avenue, plus ils vont loin.", color = Color(0xFFD0D0D0), fontSize = 14.sp, lineHeight = 20.sp) } }
        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.atlasglobe.app.R.drawable.wifi_glass), null, Modifier.size(84.dp).clip(CircleShape).border(1.dp, Atlas.Grey, CircleShape))
            Spacer(Modifier.width(14.dp))
            Column { Text("Marché du Wi-Fi", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("Un revendeur officiel partage sa connexion. Vous le trouvez près de vous, vous payez par Mobile Money avec Atlas Assistant, et la connexion s'ouvre toute seule, sans code à saisir.", color = Color(0xFFD0D0D0), fontSize = 14.sp, lineHeight = 20.sp) } }

        H("Pourquoi nous choisir")
        Bul("Discuter sans Internet, avec vos proches et votre quartier.")
        Bul("Messages chiffrés de bout en bout : les téléphones qui relaient ne peuvent pas les lire.")
        Bul("Internet partagé près de chez vous, avec des prix clairs et un paiement simple.")
        Bul("Des revendeurs officiels, approuvés par Atlas et notés par leurs clients.")
        Bul("Chaque client est suivi individuellement : quand son temps est fini, seul lui est coupé.")
        Bul("Une application légère, pensée pour les téléphones et les réalités d'ici.")

        H("Être Leader de quartier")
        P("Un Leader est la personne qui mobilise son quartier : il présente Atlas Globe avenue par avenue, aide les voisins à installer l'application et leur montre comment la laisser active pour que le réseau soit fort.")
        Bul("Reconnaissance : vous êtes le visage d'Atlas dans votre quartier, avec le badge Leader et votre code agent personnel.")
        Bul("Réseau : chaque voisin que vous amenez renforce la messagerie mesh de votre avenue, donc la vôtre.")
        Bul("Suivi : votre code agent permet de voir combien de membres vous avez recrutés et dans quelles avenues.")
        Bul("Priorité : les Leaders sont les premiers informés des nouveautés Atlas, et testent les nouvelles fonctions avant tout le monde.")
        Bul("Opportunités : un Leader actif peut devenir Revendeur Wi-Fi et vendre de la connexion à ses voisins.")

        H("Être Revendeur Wi-Fi")
        P("Le Revendeur Wi-Fi transforme ses mégas en revenu en les partageant avec ses voisins.")
        Bul("Un espace dédié : ouvrir et fermer sa connexion, voir ses clients, leur temps restant, sa vitesse, sa batterie et sa température.")
        Bul("Des clients envoyés par l'application : les voisins vous trouvent autour d'eux, vous n'avez rien à afficher ni à expliquer.")
        Bul("Paiement vérifié par Atlas Assistant : vous ne gérez pas les paiements un par un.")
        Bul("Protection de votre téléphone : le nombre de clients baisse quand il chauffe, et vous êtes alerté quand la batterie est faible.")
        Bul("Coupure automatique : à la fin du temps d'un client, il est déconnecté, sans toucher aux autres.")
        Bul("Badge de revendeur officiel et notes de vos clients, qui vous apportent leur confiance.")
        P("Le statut de Revendeur Wi-Fi est accordé par Atlas à des comptes de confiance. Il apparaît dans vos paramètres dès que votre compte est approuvé.")

        H("D'autres revendeurs arrivent")
        P("Atlas Globe ouvrira d'autres formes de revente en plus du Wi-Fi. Elles seront présentées ici et dans les annonces officielles dès qu'elles seront disponibles.")

        H("Le créateur")
        P("Atlas Globe a été conçu et développé par Joseph Baliwa, dit Jeph, développeur full stack congolais.")
        P("Sa conviction : la technologie construite ici, par des gens d'ici, peut résoudre les problèmes d'ici, puis servir le monde entier.")

        H("Informations")
        Item("Version", ver ?: "1.0") {}
        Item("Votre ID Atlas", store.load()?.atlasId ?: "") {}
        Item("Chiffrement", "De bout en bout : les relais ne lisent jamais vos messages") {}
        Item("Données visibles par l'administration", "Nom, numéro, origine, publications et membres des groupes. Jamais le contenu de vos messages.") {}
    }
}

@Composable
private fun AtlasConnectPage() = Body {
    Title("Atlas Connect")
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { com.atlasglobe.app.ui.components.SpinningGlobe(110.dp) }
    P("Atlas Connect, le VPN Atlas, est en préparation.")
    P("Cette section sera complétée dès que ses détails seront confirmés. Pour acheter du Wi-Fi auprès d'un revendeur près de vous, utilisez le bouton Wi-Fi au centre de la barre du bas.")
}

// ---------------- Portefeuille : recharge par Mobile Money, paiement instantané ----------------
@Composable
private fun WalletPage() {
    val ctx = LocalContext.current
    val clip = androidx.compose.ui.platform.LocalClipboardManager.current
    var amount by remember { mutableStateOf("") }
    var tidInput by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    Body {
        Title("Portefeuille")
        Text("Solde actuel : ${com.atlasglobe.app.connect.WalletStore.balanceFc} FC", color = Color.White, fontSize = 18.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        Group("RECHARGER PAR MOBILE MONEY")
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).clickable {
            clip.setText(androidx.compose.ui.text.AnnotatedString("0994435517"))
        }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("0994435517", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("Joseph Jeph", color = Atlas.Grey, fontSize = 13.sp)
            }
            Icon(Icons.Filled.ContentCopy, "Copier", tint = Atlas.Grey)
        }
        HorizontalDivider(color = Atlas.Line)
        Text("Envoyez le montant souhaité à ce numéro, puis indiquez ci-dessous combien vous avez envoyé et l'identifiant de la transaction reçu par SMS.",
            color = Atlas.Grey, fontSize = 13.sp, modifier = Modifier.padding(20.dp))
        OutlinedTextField(value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() } }, label = { Text("Montant envoyé (FC)") },
            singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(value = tidInput, onValueChange = { tidInput = it }, label = { Text("ID de la transaction") },
            singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
        Spacer(Modifier.height(16.dp))
        WhiteButton(if (sent) "Envoyé, en attente d'approbation" else "J'ai payé",
            amount.toIntOrNull()?.let { it > 0 } == true && tidInput.isNotBlank() && !sent) {
            com.atlasglobe.app.connect.WalletStore.requestRecharge(ctx, amount.toInt(), tidInput)
            sent = true
        }
        Spacer(Modifier.height(24.dp))
    }
}
