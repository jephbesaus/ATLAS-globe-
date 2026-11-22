package com.atlasglobe.app.ui.screens.home

import androidx.compose.foundation.background
import com.atlasglobe.app.ui.components.Text
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atlasglobe.app.data.FeedRepo
import com.atlasglobe.app.data.Profile
import com.atlasglobe.app.data.rememberOnline
import com.atlasglobe.app.ui.components.Avatar
import com.atlasglobe.app.ui.theme.Atlas

@Composable
fun HomeScreen(me: Profile, onOpenStory: (String) -> Unit, onOpenImage: (String) -> Unit, onAddStory: () -> Unit) {
    val online by rememberOnline()
    var tab by remember { mutableIntStateOf(0) }
    val posts = FeedRepo.posts
    val stories = FeedRepo.stories
    val following = FeedRepo.following
    val groups = remember(stories) { stories.groupBy { it.authorId } }
    val myName = "${me.prenom} ${me.nom}".trim()

    LazyColumn(Modifier.fillMaxSize().background(Atlas.Black)) {
        // Stories : une personne = un cercle
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    val mine = groups[me.atlasId]
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(68.dp)) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            Box(Modifier.clickable { if (mine != null) onOpenStory(me.atlasId) else onAddStory() }
                                .border(2.dp, if (mine != null) Color.White else Atlas.Line, CircleShape).padding(3.dp)) {
                                Avatar(myName, me.photoPath, 56.dp)
                            }
                            Box(Modifier.size(22.dp).background(Color.White, CircleShape).clickable { onAddStory() }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Add, "Ajouter une story", tint = Color.Black, modifier = Modifier.size(16.dp))
                            }
                        }
                        Text("Votre story", color = Color.White, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                items(groups.filterKeys { it != me.atlasId }.entries.toList()) { (id, list) ->
                    val first = list.first(); val allViewed = list.all { it.viewed }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(68.dp)) {
                        Box(Modifier.clickable { onOpenStory(id) }
                            .border(2.dp, if (allViewed) Atlas.Grey else Color.White, CircleShape).padding(3.dp)) {
                            Avatar(first.authorName, first.authorPhoto, 56.dp)
                        }
                        Text(first.authorName.substringBefore(" "), color = Color.White, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        // Onglets Publication | Vidéo
        item {
            Row(Modifier.fillMaxWidth()) {
                listOf("Publication", "Vidéo").forEachIndexed { i, t ->
                    Column(Modifier.weight(1f).clickable { tab = i }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(t, color = if (tab == i) Color.White else Atlas.Grey, fontWeight = if (tab == i) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 15.sp, modifier = Modifier.padding(vertical = 10.dp))
                        Box(Modifier.fillMaxWidth().height(if (tab == i) 2.dp else 1.dp).background(if (tab == i) Color.White else Atlas.Line))
                    }
                }
            }
            if (!online) Text("Pas de connexion. Vous voyez les publications déjà enregistrées. Les vidéos et les nouvelles publications demandent des mégas.",
                color = Atlas.Grey, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().background(Atlas.Card).padding(12.dp))
        }
        if (tab == 0) {
            items(posts.filter { it.kind != "video" }, key = { it.id }) { p ->
                PostCard(p, me.atlasId, p.authorId in following) { onOpenImage(p.id) }
            }
        } else {
            val vids = posts.filter { it.kind == "video" }
            if (vids.isEmpty()) item {
                Text(if (online) "Aucune vidéo pour le moment." else "Les vidéos demandent une connexion Internet.",
                    color = Atlas.Grey, fontSize = 14.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(40.dp))
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
