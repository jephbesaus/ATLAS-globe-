package com.atlasglobe.app.ui.screens.chat

import android.content.Intent
import com.atlasglobe.app.ui.components.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.atlasglobe.app.data.*
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import com.atlasglobe.app.ui.components.Avatar
import com.atlasglobe.app.ui.components.timeAgo
import com.atlasglobe.app.ui.screens.register.LineField
import com.atlasglobe.app.ui.screens.register.WhiteButton
import com.atlasglobe.app.ui.theme.Atlas

@Composable
fun ChatListScreen(onOpen: (String) -> Unit, onContacts: () -> Unit, onNewGroup: () -> Unit) {
    val convs = ChatRepo.conversations()
    Column(Modifier.fillMaxSize().background(Atlas.Black).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Messages", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(if (com.atlasglobe.app.mesh.MeshStats.running) "${com.atlasglobe.app.mesh.MeshStats.nearby} appareils Atlas à proximité" else "Réseau mesh en pause",
                    color = Atlas.Grey, fontSize = 12.sp)
            }
            Text("Groupe", color = Color.White, fontSize = 14.sp, modifier = Modifier.clickable { onNewGroup() }.padding(8.dp))
            Text("Contacts", color = Color.White, fontSize = 14.sp, modifier = Modifier.clickable { onContacts() }.padding(8.dp))
        }
        HorizontalDivider(color = Atlas.Line)
        if (convs.isEmpty()) Column(Modifier.fillMaxSize().padding(36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("Aucune discussion", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("Ajoutez un contact Atlas ou créez un groupe pour commencer.", color = Atlas.Grey, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp)); WhiteButton("Contacts Atlas", true, onContacts)
        } else LazyColumn {
            items(convs, key = { it }) { id ->
                val last = ChatRepo.conversation(id).lastOrNull(); val un = ChatRepo.unread(id)
                Row(Modifier.fillMaxWidth().clickable { onOpen(id) }.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(ChatRepo.titleOf(id), if (id == com.atlasglobe.app.connect.AtlasConfig.ASSISTANT_ID) com.atlasglobe.app.connect.AssistantAvatar.path(ctx) else ChatRepo.group(id)?.photo, 50.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(ChatRepo.titleOf(id), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(when {
                            last == null -> ""; last.deleted -> "Message supprimé"
                            last.kind == "image" -> "Photo"; last.kind == "voice" -> "Note vocale"
                            last.kind == "system" -> last.text
                            ChatRepo.isGroup(id) && last.from != ChatRepo.myId() -> "${ChatRepo.nameOf(last.from)} : ${last.text}"
                            else -> last.text }, color = if (un > 0) Color.White else Atlas.Grey, fontSize = 14.sp, maxLines = 1)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        last?.let { Text(timeAgo(it.t), color = Atlas.Grey, fontSize = 11.sp) }
                        if (un > 0) Box(Modifier.padding(top = 4.dp).defaultMinSize(22.dp, 22.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                            Text("$un", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    }
                }
                HorizontalDivider(color = Atlas.Line)
            }
        }
    }
}

@Composable
fun ContactsScreen(onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    var adding by remember { mutableStateOf(false) }
    var id by remember { mutableStateOf("") }; var name by remember { mutableStateOf("") }
    var err by remember { mutableStateOf(false) }
    val me = remember { ProfileStore(ctx).load() }
    var showQr by remember { mutableStateOf(false) }
    val scan = rememberLauncherForActivityResult(ScanContract()) { r ->
        val txt = r.contents; if (txt != null) { val e = ChatRepo.addContactFromQr(txt)
            android.widget.Toast.makeText(ctx, e ?: "Contact ajouté et vérifié", android.widget.Toast.LENGTH_LONG).show() } }
    LazyColumn(Modifier.fillMaxSize().background(Atlas.Black).statusBarsPadding()) {
        item { Text("Contacts Atlas", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp))
            Text("Votre ID : ${me?.atlasId}", color = Atlas.Grey, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp)) }
        item { Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) { WhiteButton(if (adding) "Fermer" else "Ajouter un contact", true) { adding = !adding } }
            Box(Modifier.weight(1f)) { WhiteButton("Inviter", true) {
                runCatching { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,
                    "Rejoins-moi sur Atlas Globe : discute sans Internet. Mon ID : ${me?.atlasId}"), "Inviter").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } } } } }
        item { Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) { WhiteButton("Mon QR", true) { showQr = true } }
            Box(Modifier.weight(1f)) { WhiteButton("Scanner un QR", true) { scan.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt("Scannez le QR Atlas de votre contact").setBeepEnabled(false).setOrientationLocked(false)) } } } }
        if (adding) item { Column(Modifier.padding(horizontal = 28.dp)) {
            LineField(id, { id = it.uppercase(); err = false }, "ID Atlas (ATL-XXXX-XXXX)", error = err)
            Spacer(Modifier.height(8.dp)); LineField(name, { name = it }, "Nom du contact")
            Spacer(Modifier.height(16.dp))
            WhiteButton("Enregistrer", id.isNotBlank() && name.isNotBlank()) {
                if (ChatRepo.addContact(id, name)) { adding = false; id = ""; name = "" } else err = true }
            if (err) Text("ID invalide ou déjà enregistré", color = Atlas.Alert, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(16.dp)) } }
        items(ChatRepo.contacts, key = { it.id }) { c ->
            Row(Modifier.fillMaxWidth().clickable { onOpen(c.id) }.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(c.name, null, 44.dp); Spacer(Modifier.width(14.dp))
                Column { Text(c.name, color = Color.White, fontSize = 16.sp); Text(c.id, color = Atlas.Grey, fontSize = 12.sp) } }
            HorizontalDivider(color = Atlas.Line)
        }
        item { if (showQr) MyQrDialog { showQr = false } }
        item { Text("Retrouver vos contacts du téléphone par leur numéro sera disponible avec le serveur. Sans Internet, ajoutez par ID ou par QR de proximité.",
            color = Atlas.Grey, fontSize = 12.sp, modifier = Modifier.padding(20.dp)) }
    }
}

@Composable
fun MyQrDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val me = remember { ProfileStore(ctx).load() }
    val bmp = remember {
        val key = Crypto.publicB64().replace('+', '-').replace('/', '_').trimEnd('=')
        val text = "atlas://c?i=${me?.atlasId}&k=$key&n=" + android.net.Uri.encode("${me?.prenom} ${me?.nom}".trim())
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 640, 640)
        val b = android.graphics.Bitmap.createBitmap(640, 640, android.graphics.Bitmap.Config.RGB_565)
        for (x in 0 until 640) for (y in 0 until 640) b.setPixel(x, y, if (m[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE); b }
    AlertDialog(onDismissRequest = onClose, containerColor = Atlas.Card, title = { Text("Mon QR Atlas", color = Color.White) },
        text = { Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(bmp.asImageBitmap(), null, Modifier.size(240.dp).background(Color.White).padding(8.dp))
            Text(me?.atlasId ?: "", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
            Text("Faites-le scanner par votre contact : l'échange est sécurisé face à face.", color = Atlas.Grey, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp)) } },
        confirmButton = { TextButton(onClose) { Text("Fermer", color = Color.White) } })
}
