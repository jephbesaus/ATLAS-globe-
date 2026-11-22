package com.atlasglobe.app.ui.screens.home

import android.widget.Toast
import com.atlasglobe.app.ui.components.Text
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atlasglobe.app.data.FeedRepo
import com.atlasglobe.app.data.ImageUtil
import com.atlasglobe.app.data.Post
import com.atlasglobe.app.ui.components.Avatar
import com.atlasglobe.app.ui.components.rememberLocalBitmap
import com.atlasglobe.app.ui.components.timeAgo
import com.atlasglobe.app.ui.theme.Atlas

@Composable
fun PostCard(post: Post, myId: String, following: Boolean, onOpenImage: () -> Unit) {
    val ctx = LocalContext.current
    val own = post.authorId == myId
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        // En-tête : photo, nom, suivre, menu
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(post.authorName, post.authorPhoto, 40.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(post.authorName, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
                    if (post.official || post.cert > 0 || (own && com.atlasglobe.app.settings.SettingsStore.certLevel > 0)) { Spacer(Modifier.width(4.dp))
                        Icon(Icons.Filled.Verified, "Compte officiel", tint = Color.White, modifier = Modifier.size(16.dp)) }
                }
                Text(timeAgo(post.createdAt) + if (!post.synced) "  ·  en attente d'envoi" else "",
                    color = Atlas.Grey, fontSize = 12.sp)
            }
            if (!own) {
                Box(Modifier.border(1.dp, if (following) Atlas.Grey else Color.White, RoundedCornerShape(4.dp))
                    .clickable { FeedRepo.toggleFollow(post.authorId) }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(if (following) "Abonné" else "Suivre", color = if (following) Atlas.Grey else Color.White, fontSize = 12.sp)
                }
            }
            Box {
                Icon(Icons.Filled.MoreVert, "Options", tint = Color.White,
                    modifier = Modifier.padding(start = 6.dp).size(26.dp).clickable { menu = true })
                DropdownMenu(menu, { menu = false }, modifier = Modifier.background(Atlas.Card)) {
                    if (own) {
                        DropdownMenuItem(text = { Text("Supprimer", color = Atlas.Alert) },
                            onClick = { menu = false; confirmDelete = true })
                    } else {
                        if (post.kind == "image" && post.mediaPath != null)
                            DropdownMenuItem(text = { Text("Télécharger", color = Color.White) }, onClick = {
                                menu = false
                                val ok = ImageUtil.saveToGallery(ctx, post.mediaPath)
                                Toast.makeText(ctx, if (ok) "Image enregistrée dans la galerie" else "Échec de l'enregistrement", Toast.LENGTH_SHORT).show()
                            })
                        DropdownMenuItem(text = { Text(if (post.saved) "Retirer des enregistrés" else "Enregistrer", color = Color.White) },
                            onClick = { menu = false; FeedRepo.toggleSave(post.id) })
                        DropdownMenuItem(text = { Text("Republier", color = Color.White) }, onClick = {
                            menu = false; FeedRepo.repost(post.id)
                            Toast.makeText(ctx, "Publication republiée", Toast.LENGTH_SHORT).show()
                        })
                    }
                }
            }
        }

        post.repostFrom?.let { Text("Republié de $it", color = Atlas.Grey, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 14.dp)) }

        // Média pleine largeur, cliquable
        if (post.kind == "image" && post.mediaPath != null) {
            val bmp by rememberLocalBitmap(post.mediaPath)
            val b = bmp
            if (b != null) Image(b.asImageBitmap(), null,
                Modifier.fillMaxWidth().aspectRatio((b.width.toFloat() / b.height).coerceIn(0.8f, 1.5f)).clickable { onOpenImage() },
                contentScale = ContentScale.Crop)
            else Box(Modifier.fillMaxWidth().aspectRatio(1f))
        }

        if (post.text.isNotBlank()) Text(post.text, color = Color.White, fontSize = if (post.kind == "text") 17.sp else 15.sp,
            lineHeight = 22.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))

        // Actions : like (coche), enregistrer
        Row(Modifier.padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (post.likedByMe) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircleOutline, "J'aime",
                tint = Color.White, modifier = Modifier.size(28.dp).clickable { FeedRepo.toggleLike(post.id) })
            if (post.likes > 0) Text("  ${post.likes}", color = Color.White, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            Icon(if (post.saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, "Enregistrer",
                tint = Color.White, modifier = Modifier.size(26.dp).clickable { FeedRepo.toggleSave(post.id) })
        }
        HorizontalDivider(color = Atlas.Line, modifier = Modifier.padding(top = 8.dp))
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false }, containerColor = Atlas.Card,
        title = { Text("Supprimer cette publication ?", color = Color.White) },
        text = { Text("Elle sera retirée définitivement.", color = Atlas.Grey) },
        confirmButton = { TextButton({ confirmDelete = false; FeedRepo.deletePost(post.id) }) { Text("Supprimer", color = Atlas.Alert) } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Annuler", color = Color.White) } })
}
