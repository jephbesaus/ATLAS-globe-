package com.atlasglobe.app.ui.screens.chat

import android.Manifest
import com.atlasglobe.app.ui.components.Text
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.atlasglobe.app.data.*
import com.atlasglobe.app.ui.components.Avatar
import com.atlasglobe.app.ui.components.rememberLocalBitmap
import com.atlasglobe.app.ui.screens.register.WhiteButton
import com.atlasglobe.app.ui.theme.Atlas
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private val MINE = Color.White
private val THEIRS = Color(0xFF1E1E1E)
private val REACTIONS = listOf("❤", "👍", "😂", "😮", "😢", "🙏")

private fun hhmm(t: Long) = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(t))
private fun statusLabel(s: String) = when (s) { "queued" -> "En attente"; "sent" -> "Envoyé"; "relayed" -> "Relayé"; "delivered" -> "Livré"; "read" -> "Lu"; else -> "" }
private fun dur(ms: Long) = "%d:%02d".format(ms / 60000, (ms / 1000) % 60)

@Composable
fun ChatScreen(conv: String, onGroupInfo: () -> Unit) {
    val ctx = LocalContext.current
    val me = ChatRepo.myId()
    val isGroup = ChatRepo.isGroup(conv)
    val group = if (isGroup) ChatRepo.group(conv) else null
    val msgs = ChatRepo.conversation(conv)
    val byId = remember(msgs) { msgs.associateBy { it.id } }
    var text by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<Msg?>(null) }
    var menuFor by remember { mutableStateOf<Msg?>(null) }
    var deleteFor by remember { mutableStateOf<Msg?>(null) }
    var imageFull by remember { mutableStateOf<String?>(null) }
    var topMenu by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    var confirmReport by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    val recorder = remember { VoiceRecorder(ctx) }
    var tick by remember { mutableLongStateOf(0L) }

    DisposableEffect(conv) { ChatRepo.activeConv = conv; ChatRepo.markRead(conv)
        onDispose { ChatRepo.activeConv = null; VoicePlayer.stop(); if (recording) recorder.cancel() } }
    LaunchedEffect(msgs.size) { ChatRepo.markRead(conv) }
    LaunchedEffect(recording) { while (recording) { tick = System.currentTimeMillis(); delay(250) } }
    LaunchedEffect(Unit) { while (true) { tick = System.currentTimeMillis(); delay(30_000) } }   // fin de suspension

    // Qui peut écrire ?
    val isBlocked = !isGroup && conv in ChatRepo.blocked
    val blockReason: String? = when {
        isBlocked -> "Vous avez bloqué ce contact"
        isGroup && (group == null || me !in group.members) -> "Vous n'êtes plus dans ce groupe"
        isGroup && ChatRepo.suspendedUntil(group!!, me) > 0 -> "Vous êtes suspendu jusqu'à ${hhmm(ChatRepo.suspendedUntil(group, me))}"
        isGroup && group!!.locked && !ChatRepo.isAdmin(group, me) -> "Seuls les administrateurs peuvent écrire dans ce groupe"
        else -> null
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) ImageUtil.saveScaled(ctx, uri, 800, File(ctx.filesDir, "media/${UUID.randomUUID()}.jpg"), 60)
            ?.let { ChatRepo.send(conv, "image", "", it, 0, replyTo?.id); replyTo = null }
    }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok && recorder.start()) recording = true else if (!ok) Toast.makeText(ctx, "Autorisez le micro pour envoyer des notes vocales", Toast.LENGTH_SHORT).show()
    }
    val callPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) com.atlasglobe.app.call.CallManager.start(ctx, conv) else Toast.makeText(ctx, "Autorisez le micro pour appeler", Toast.LENGTH_SHORT).show() }
    fun startRec() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { if (recorder.start()) recording = true }
        else micPerm.launch(Manifest.permission.RECORD_AUDIO)
    }

    Column(Modifier.fillMaxSize().background(Atlas.Black).statusBarsPadding().navigationBarsPadding().imePadding()) {
        // En-tête
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clickable { if (isGroup) onGroupInfo() else showProfile = true }, verticalAlignment = Alignment.CenterVertically) {
                Avatar(ChatRepo.titleOf(conv), if (conv == com.atlasglobe.app.connect.AtlasConfig.ASSISTANT_ID) com.atlasglobe.app.connect.AssistantAvatar.path(ctx) else group?.photo, 40.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(ChatRepo.titleOf(conv), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, maxLines = 1)
                        if (conv == com.atlasglobe.app.connect.AtlasConfig.ASSISTANT_ID) { Spacer(Modifier.width(4.dp))
                            Icon(Icons.Filled.Verified, "Compte officiel", tint = Color.White, modifier = Modifier.size(16.dp)) } }
                    Text(if (isGroup) "${group?.members?.size ?: 0} membres" else conv, color = Atlas.Grey, fontSize = 11.sp)
                }
            }
            if (!isGroup) Icon(Icons.Filled.Call, "Appel", tint = Color.White, modifier = Modifier.size(26.dp).clickable {
                val err = com.atlasglobe.app.call.CallManager.canCall(ctx)
                if (err != null) Toast.makeText(ctx, err, Toast.LENGTH_LONG).show()
                else if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) com.atlasglobe.app.call.CallManager.start(ctx, conv)
                else callPerm.launch(Manifest.permission.RECORD_AUDIO) })
            Box {
                Icon(Icons.Filled.MoreVert, "Options", tint = Color.White, modifier = Modifier.padding(start = 14.dp).size(26.dp).clickable { topMenu = true })
                DropdownMenu(topMenu, { topMenu = false }, modifier = Modifier.background(Atlas.Card)) {
                    if (isGroup) DropdownMenuItem(text = { Text("Infos du groupe", color = Color.White) }, onClick = { topMenu = false; onGroupInfo() })
                    else {
                        DropdownMenuItem(text = { Text("Profil", color = Color.White) }, onClick = { topMenu = false; showProfile = true })
                        DropdownMenuItem(text = { Text(if (isBlocked) "Débloquer" else "Bloquer", color = Color.White) },
                            onClick = { topMenu = false; ChatRepo.block(conv, !isBlocked) })
                        DropdownMenuItem(text = { Text("Signaler", color = Atlas.Alert) }, onClick = { topMenu = false; confirmReport = true })
                    }
                }
            }
        }
        HorizontalDivider(color = Atlas.Line)

        // Messages (les plus récents en bas)
        val bgId = com.atlasglobe.app.settings.SettingsStore.chatBg
        val bgColor = when (bgId) { "grey" -> Color(0xFF2A2A2A); "white" -> Color.White; "blue" -> Color(0xFF0A2A5E); else -> Color.Black }
        Box(Modifier.weight(1f).fillMaxWidth().background(bgColor)) {
        if (bgId.startsWith("photo:")) { val pb by rememberLocalBitmap(bgId.removePrefix("photo:"), 1080); pb?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }
        LazyColumn(Modifier.fillMaxSize(), reverseLayout = true, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(msgs.reversed(), key = { it.id }) { m ->
                if (m.kind == "system") Text(m.text, color = Atlas.Grey, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                else Bubble(m, me, isGroup, byId[m.replyTo], onLong = { menuFor = m }, onImage = { imageFull = it })
            }
        }
        }

        // Saisie
        if (blockReason != null) {
            Column(Modifier.fillMaxWidth().background(Atlas.Card).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(blockReason, color = Atlas.Grey, fontSize = 14.sp)
                if (isBlocked) Text("Débloquer", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { ChatRepo.block(conv, false) }.padding(top = 8.dp))
            }
        } else if (recording) {
            Row(Modifier.fillMaxWidth().background(Atlas.Card).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(Atlas.Alert, androidx.compose.foundation.shape.CircleShape))
                Text("  ${dur(recorder.elapsedMs().also { tick })}", color = Color.White, fontSize = 16.sp)
                Spacer(Modifier.weight(1f))
                Text("Annuler", color = Atlas.Grey, modifier = Modifier.clickable { recorder.cancel(); recording = false }.padding(10.dp))
                Text(if (recorder.paused) "Reprendre" else "Pause", color = Color.White, modifier = Modifier.clickable { recorder.togglePause() }.padding(10.dp))
                Text("Envoyer", color = Color.Black, fontWeight = FontWeight.SemiBold, modifier = Modifier.background(Color.White, RoundedCornerShape(4.dp))
                    .clickable { recorder.finish()?.let { (p, d) -> ChatRepo.send(conv, "voice", "", p, d, replyTo?.id); replyTo = null }; recording = false }
                    .padding(horizontal = 14.dp, vertical = 8.dp))
            }
        } else {
            replyTo?.let { r -> Row(Modifier.fillMaxWidth().background(Atlas.Card).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Réponse à ${ChatRepo.nameOf(r.from)}", color = Atlas.Grey, fontSize = 12.sp)
                    Text(preview(r), color = Color.White, fontSize = 13.sp, maxLines = 1) }
                Text("Annuler", color = Atlas.Grey, modifier = Modifier.clickable { replyTo = null }.padding(8.dp)) } }
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Add, "Image", tint = Color.White, modifier = Modifier.size(32.dp).clickable {
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
                TextField(text, { text = it }, placeholder = { Text("Message", color = Atlas.Grey) }, maxLines = 5, modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                    colors = TextFieldDefaults.colors(focusedContainerColor = Atlas.Card, unfocusedContainerColor = Atlas.Card,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = Color.White,
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White), shape = RoundedCornerShape(22.dp))
                if (text.isNotBlank()) Box(Modifier.size(44.dp).background(Color.White, androidx.compose.foundation.shape.CircleShape).clickable {
                    ChatRepo.send(conv, "text", text.trim(), null, 0, replyTo?.id); text = ""; replyTo = null }, contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Envoyer", tint = Color.Black, modifier = Modifier.size(22.dp)) }
                else Box(Modifier.size(44.dp).background(Color.White, androidx.compose.foundation.shape.CircleShape).clickable { startRec() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Mic, "Note vocale", tint = Color.Black, modifier = Modifier.size(22.dp)) }
            }
        }
    }

    // Appui long : réactions + actions
    menuFor?.let { m -> Dialog(onDismissRequest = { menuFor = null }) {
        Column(Modifier.fillMaxWidth().background(Atlas.Card, RoundedCornerShape(10.dp)).padding(vertical = 8.dp)) {
            if (!m.deleted) Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                REACTIONS.forEach { e -> Text(e, fontSize = 26.sp, modifier = Modifier.clickable { ChatRepo.react(m.id, e); menuFor = null }) } }
            if (!m.deleted && blockReason == null) MenuRow("Répondre") { replyTo = m; menuFor = null }
            if (!m.deleted) MenuRow("Partager") { menuFor = null
                val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, if (m.kind == "text") m.text else "Message Atlas Globe")
                runCatching { ctx.startActivity(Intent.createChooser(i, "Partager").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            MenuRow("Supprimer", Atlas.Alert) { deleteFor = m; menuFor = null }
        } } }

    deleteFor?.let { m -> AlertDialog(onDismissRequest = { deleteFor = null }, containerColor = Atlas.Card,
        title = { Text("Supprimer le message ?", color = Color.White) },
        text = { Column {
            Text("Supprimer pour moi", color = Color.White, modifier = Modifier.fillMaxWidth().clickable { ChatRepo.deleteForMe(m.id); deleteFor = null }.padding(vertical = 12.dp))
            if (ChatRepo.canDeleteForAll(m) && !m.deleted) Text("Supprimer pour tout le monde", color = Atlas.Alert,
                modifier = Modifier.fillMaxWidth().clickable { ChatRepo.deleteForAll(m.id); deleteFor = null }.padding(vertical = 12.dp)) } },
        confirmButton = {}, dismissButton = { TextButton({ deleteFor = null }) { Text("Annuler", color = Color.White) } }) }

    imageFull?.let { p -> Dialog(onDismissRequest = { imageFull = null }) {
        val b by rememberLocalBitmap(p, 2048)
        Box(Modifier.fillMaxSize().background(Color.Black)) { b?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) } } } }

    if (showProfile) AlertDialog(onDismissRequest = { showProfile = false }, containerColor = Atlas.Card,
        title = { Text(ChatRepo.nameOf(conv), color = Color.White) },
        text = { Column { Text("ID Atlas : $conv", color = Atlas.Grey)
            ChatRepo.contact(conv)?.phone?.takeIf { it.isNotEmpty() }?.let { Text("Numéro : $it", color = Atlas.Grey) }
            ChatRepo.contact(conv)?.bio?.takeIf { it.isNotEmpty() }?.let { Text(it, color = Color.White, modifier = Modifier.padding(top = 8.dp)) }
            Text("${ChatRepo.msgs.count { it.conv == conv && it.kind != "system" && it.kind != "text" }} médias échangés", color = Atlas.Grey, modifier = Modifier.padding(top = 8.dp)) } },
        confirmButton = { TextButton({ showProfile = false }) { Text("Fermer", color = Color.White) } })

    if (confirmReport) AlertDialog(onDismissRequest = { confirmReport = false }, containerColor = Atlas.Card,
        title = { Text("Signaler ce contact ?", color = Color.White) }, text = { Text("Le signalement sera transmis à l'équipe Atlas.", color = Atlas.Grey) },
        confirmButton = { TextButton({ confirmReport = false; ChatRepo.report(conv); Toast.makeText(ctx, "Signalement enregistré", Toast.LENGTH_SHORT).show() }) { Text("Signaler", color = Atlas.Alert) } },
        dismissButton = { TextButton({ confirmReport = false }) { Text("Annuler", color = Color.White) } })
}

private fun preview(m: Msg) = if (m.deleted) "Message supprimé" else when (m.kind) { "image" -> "Photo"; "voice" -> "Note vocale"; else -> m.text }

@Composable private fun MenuRow(label: String, color: Color = Color.White, onClick: () -> Unit) =
    Text(label, color = color, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 20.dp, vertical = 14.dp))

@Composable
private fun Bubble(m: Msg, me: String, isGroup: Boolean, quoted: Msg?, onLong: () -> Unit, onImage: (String) -> Unit) {
    val mine = m.from == me
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        Column(Modifier.widthIn(max = 300.dp).background(if (mine) MINE else THEIRS, RoundedCornerShape(14.dp))
            .pointerInput(m.id) { detectTapGestures(onLongPress = { onLong() }) }.padding(10.dp)) {
            val fg = if (mine) Color.Black else Color.White; val sub = if (mine) Color(0xFF555555) else Atlas.Grey
            if (isGroup && !mine) Text(ChatRepo.nameOf(m.from), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            quoted?.let { q -> Column(Modifier.padding(bottom = 6.dp).background(if (mine) Color(0xFFE6E6E6) else Color(0xFF2C2C2C), RoundedCornerShape(6.dp)).padding(6.dp)) {
                Text(ChatRepo.nameOf(q.from), color = sub, fontSize = 11.sp); Text(preview(q), color = fg, fontSize = 12.sp, maxLines = 1) } }
            when {
                m.deleted -> Text("Message supprimé", color = sub, fontStyle = FontStyle.Italic, fontSize = 14.sp)
                m.kind == "image" && m.media != null -> { val b by rememberLocalBitmap(m.media, 640); val bm = b
                    if (bm != null) Image(bm.asImageBitmap(), null, Modifier.width(220.dp).heightIn(max = 300.dp).clickable { onImage(m.media) }, contentScale = ContentScale.Crop) }
                m.kind == "voice" && m.media != null -> { val playing = VoicePlayer.playingId == m.id
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(190.dp)) {
                        Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Lecture", tint = fg, modifier = Modifier.size(32.dp).clickable { VoicePlayer.toggle(m.id, m.media) })
                        Box(Modifier.weight(1f).height(2.dp).padding(horizontal = 6.dp).background(sub))
                        Text(dur(m.durMs), color = sub, fontSize = 12.sp) } }
                else -> Text(m.text, color = fg, fontSize = 16.sp)
            }
            Text(hhmm(m.t) + if (mine) "  ·  ${statusLabel(m.status)}" else "", color = sub, fontSize = 10.sp, modifier = Modifier.align(Alignment.End).padding(top = 2.dp))
        }
        if (m.reactions.isNotEmpty()) Row(Modifier.padding(top = 2.dp).background(Atlas.Card, RoundedCornerShape(10.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
            m.reactions.values.groupingBy { it }.eachCount().forEach { (e, n) -> Text(if (n > 1) "$e$n " else "$e ", fontSize = 13.sp) } }
    }
}
