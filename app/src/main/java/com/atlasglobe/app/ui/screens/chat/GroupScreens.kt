package com.atlasglobe.app.ui.screens.chat

import android.widget.Toast
import com.atlasglobe.app.ui.components.Text
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
import com.atlasglobe.app.ui.components.Avatar
import com.atlasglobe.app.ui.screens.register.LineField
import com.atlasglobe.app.ui.screens.register.WhiteButton
import com.atlasglobe.app.ui.theme.Atlas
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
private fun SelectRow(name: String, sub: String, checked: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onToggle() }.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(name, null, 40.dp); Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Text(name, color = Color.White, fontSize = 16.sp); Text(sub, color = Atlas.Grey, fontSize = 12.sp) }
        Checkbox(checked, { onToggle() }, colors = CheckboxDefaults.colors(checkedColor = Color.White, checkmarkColor = Color.Black, uncheckedColor = Atlas.Grey))
    }
}

/** Création d'un groupe : nom, photo, membres choisis dans les contacts. */
@Composable
fun GroupCreateScreen(onCreated: (String) -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf("") }; var desc by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<String?>(null) }
    val sel = remember { mutableStateListOf<String>() }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) photo = ImageUtil.saveScaled(ctx, u, 512, File(ctx.filesDir, "media/${UUID.randomUUID()}.jpg"), 80) }
    LazyColumn(Modifier.fillMaxSize().background(Atlas.Black).statusBarsPadding().navigationBarsPadding().imePadding()) {
        item {
            Text("Nouveau groupe", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp))
            Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.clickable { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Avatar(name.ifBlank { "Groupe" }, photo, 90.dp) }
                Text("Touchez pour choisir la photo", color = Atlas.Grey, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(12.dp)); LineField(name, { name = it.take(40) }, "Nom du groupe")
                Spacer(Modifier.height(8.dp)); LineField(desc, { desc = it.take(200) }, "Description (facultatif)")
            }
            Text("Membres (${sel.size})", color = Atlas.Grey, fontSize = 13.sp, modifier = Modifier.padding(20.dp))
            if (ChatRepo.contacts.isEmpty()) Text("Ajoutez d'abord des contacts Atlas.", color = Atlas.Grey, modifier = Modifier.padding(horizontal = 20.dp))
        }
        items(ChatRepo.contacts, key = { it.id }) { c -> SelectRow(c.name, c.id, c.id in sel) { if (c.id in sel) sel.remove(c.id) else sel.add(c.id) } }
        item { Box(Modifier.padding(20.dp)) { WhiteButton("Créer le groupe", name.isNotBlank() && sel.isNotEmpty()) {
            onCreated(ChatRepo.createGroup(name, desc, photo, sel.toList())) } } }
    }
}

@Composable
private fun ActRow(label: String, color: Color = Color.White, onClick: () -> Unit) =
    Text(label, color = color, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp))

private fun hhmm(t: Long) = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(t))

/** Profil du groupe : membres, administrateurs, verrouillage, suspension, bannissement. */
@Composable
fun GroupInfoScreen(gid: String, onLeft: () -> Unit) {
    val ctx = LocalContext.current
    val g = ChatRepo.group(gid)
    if (g == null) { LaunchedEffect(Unit) { onLeft() }; return }
    val me = ChatRepo.myId(); val admin = ChatRepo.isAdmin(g, me); val inGroup = me in g.members
    var editing by remember { mutableStateOf(false) }; var adding by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<String?>(null) }; var confirmLeave by remember { mutableStateOf(false) }
    var banUser by remember { mutableStateOf<String?>(null) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) ImageUtil.saveScaled(ctx, u, 512, File(ctx.filesDir, "media/${UUID.randomUUID()}.jpg"), 80)?.let { ChatRepo.editGroup(gid, g.name, g.desc, it) } }
    val members = g.members.keys.sortedWith(compareBy({ ChatRepo.isAdmin(g, it).not() }, { ChatRepo.nameOf(it) }))

    LazyColumn(Modifier.fillMaxSize().background(Atlas.Black).statusBarsPadding().navigationBarsPadding()) {
        item {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.clickable(enabled = admin) { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Avatar(g.name, g.photo, 110.dp) }
                if (admin) Text("Modifier la photo", color = Atlas.Grey, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                Text(g.name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
                Text("Groupe · ${g.members.size} membres", color = Atlas.Grey, fontSize = 13.sp)
                if (g.desc.isNotBlank()) Text(g.desc, color = Color.White, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
                if (admin) Text("Modifier le nom et la description", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 12.dp).clickable { editing = true }.padding(8.dp))
            }
            HorizontalDivider(color = Atlas.Line)
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Conversation verrouillée", color = Color.White, fontSize = 16.sp)
                    Text("Seuls les administrateurs peuvent écrire", color = Atlas.Grey, fontSize = 12.sp) }
                Switch(g.locked, { if (admin) ChatRepo.changeLocked(gid, it) }, enabled = admin,
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = Color.White, uncheckedThumbColor = Atlas.Grey, uncheckedTrackColor = Atlas.Card, uncheckedBorderColor = Atlas.Grey))
            }
            HorizontalDivider(color = Atlas.Line)
            if (admin) { Text("Ajouter des membres", color = Color.White, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().clickable { adding = true }.padding(20.dp)); HorizontalDivider(color = Atlas.Line) }
            Text("Membres", color = Atlas.Grey, fontSize = 13.sp, modifier = Modifier.padding(20.dp))
        }
        items(members, key = { it }) { u ->
            val susp = ChatRepo.suspendedUntil(g, u)
            Row(Modifier.fillMaxWidth().clickable(enabled = admin && u != me && u != g.creator) { target = u }.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(ChatRepo.nameOf(u), null, 42.dp); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(ChatRepo.nameOf(u), color = Color.White, fontSize = 16.sp)
                    if (susp > 0) Text("Suspendu jusqu'à ${hhmm(susp)}", color = Atlas.Alert, fontSize = 12.sp) }
                if (ChatRepo.isAdmin(g, u)) Text(if (u == g.creator) "Créateur" else "Admin", color = Color.White, fontSize = 11.sp,
                    modifier = Modifier.border(1.dp, Atlas.Grey, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
            }
            HorizontalDivider(color = Atlas.Line)
        }
        if (admin && g.banned.isNotEmpty()) {
            item { Text("Bannis", color = Atlas.Grey, fontSize = 13.sp, modifier = Modifier.padding(20.dp)) }
            items(g.banned.toList(), key = { "b$it" }) { u ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(ChatRepo.nameOf(u), color = Color.White, modifier = Modifier.weight(1f))
                    Text("Autoriser à nouveau", color = Color.White, fontSize = 13.sp, modifier = Modifier.clickable { ChatRepo.unban(gid, u) }) }
                HorizontalDivider(color = Atlas.Line) }
        }
        if (inGroup) item { Text("Quitter le groupe", color = Atlas.Alert, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().clickable { confirmLeave = true }.padding(20.dp)) }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (editing) { var n by remember { mutableStateOf(g.name) }; var d by remember { mutableStateOf(g.desc) }
        AlertDialog(onDismissRequest = { editing = false }, containerColor = Atlas.Card, title = { Text("Profil du groupe", color = Color.White) },
            text = { Column { LineField(n, { n = it.take(40) }, "Nom"); Spacer(Modifier.height(8.dp)); LineField(d, { d = it.take(200) }, "Description") } },
            confirmButton = { TextButton({ ChatRepo.editGroup(gid, n, d, null); editing = false }) { Text("Enregistrer", color = Color.White) } },
            dismissButton = { TextButton({ editing = false }) { Text("Annuler", color = Atlas.Grey) } }) }

    if (adding) { val sel = remember { mutableStateListOf<String>() }
        val cands = ChatRepo.contacts.filter { it.id !in g.members && it.id !in g.banned }
        AlertDialog(onDismissRequest = { adding = false }, containerColor = Atlas.Card, title = { Text("Ajouter des membres", color = Color.White) },
            text = { if (cands.isEmpty()) Text("Aucun contact à ajouter.", color = Atlas.Grey) else LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(cands, key = { it.id }) { c -> SelectRow(c.name, c.id, c.id in sel) { if (c.id in sel) sel.remove(c.id) else sel.add(c.id) } } } },
            confirmButton = { TextButton({ ChatRepo.addMembers(gid, sel.toList()); adding = false }, enabled = sel.isNotEmpty()) { Text("Ajouter", color = Color.White) } },
            dismissButton = { TextButton({ adding = false }) { Text("Annuler", color = Atlas.Grey) } }) }

    target?.let { u -> val susp = ChatRepo.suspendedUntil(g, u)
        AlertDialog(onDismissRequest = { target = null }, containerColor = Atlas.Card, title = { Text(ChatRepo.nameOf(u), color = Color.White) },
            text = { Column {
                val isAdm = ChatRepo.isAdmin(g, u)
                ActRow(if (isAdm) "Retirer le rôle d'administrateur" else "Nommer administrateur") { ChatRepo.setRole(gid, u, !isAdm); target = null }
                if (susp > 0) ActRow("Lever la suspension") { ChatRepo.unsuspend(gid, u); target = null }
                else listOf(5 to "Suspendre 5 minutes", 30 to "Suspendre 30 minutes", 60 to "Suspendre 1 heure", 1440 to "Suspendre 24 heures").forEach { (m, l) ->
                    ActRow(l) { ChatRepo.suspend(gid, u, m); target = null } }
                ActRow("Retirer du groupe", Atlas.Alert) { ChatRepo.removeMember(gid, u, false); target = null }
                ActRow("Bannir du groupe", Atlas.Alert) { banUser = u; target = null }
            } },
            confirmButton = {}, dismissButton = { TextButton({ target = null }) { Text("Fermer", color = Atlas.Grey) } }) }

    banUser?.let { u -> AlertDialog(onDismissRequest = { banUser = null }, containerColor = Atlas.Card,
        title = { Text("Bannir ${ChatRepo.nameOf(u)} ?", color = Color.White) }, text = { Text("Il sera retiré et ne pourra plus être ajouté sans votre autorisation.", color = Atlas.Grey) },
        confirmButton = { TextButton({ ChatRepo.removeMember(gid, u, true); banUser = null }) { Text("Bannir", color = Atlas.Alert) } },
        dismissButton = { TextButton({ banUser = null }) { Text("Annuler", color = Color.White) } }) }

    if (confirmLeave) AlertDialog(onDismissRequest = { confirmLeave = false }, containerColor = Atlas.Card,
        title = { Text("Quitter le groupe ?", color = Color.White) },
        confirmButton = { TextButton({ confirmLeave = false; ChatRepo.leaveGroup(gid); Toast.makeText(ctx, "Vous avez quitté le groupe", Toast.LENGTH_SHORT).show(); onLeft() }) { Text("Quitter", color = Atlas.Alert) } },
        dismissButton = { TextButton({ confirmLeave = false }) { Text("Annuler", color = Color.White) } })
}
