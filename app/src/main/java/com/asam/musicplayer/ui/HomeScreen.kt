package com.asam.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asam.musicplayer.MusicViewModel
import com.asam.musicplayer.R
import com.asam.musicplayer.Song

private class MixCard(
    val title: String,
    val description: String,
    val color: Color,
    val songs: List<Song>,
    val shuffled: Boolean,
)

/** Home: greeting, filter chips, curated mixes, your playlists and recently added songs. */
@Composable
fun HomeScreen(
    vm: MusicViewModel,
    bottomPad: Dp,
    onOpenSettings: () -> Unit,
    onSearch: () -> Unit,
    onLiked: () -> Unit,
    onSeeAllPlaylists: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var chip by rememberSaveable { mutableIntStateOf(0) }
    val recent = remember(vm.songs) { vm.songs.sortedByDescending { it.dateAdded } }
    val top = remember(vm.songs, vm.playCounts) {
        vm.songs.filter { (vm.playCounts[it.id] ?: 0) > 0 }.sortedByDescending { vm.playCounts[it.id] ?: 0 }
    }
    val liked = remember(vm.songs, vm.favorites) { vm.songs.filter { vm.favorites.contains(it.id) } }
    val mixes = remember(vm.songs, recent, top, liked) {
        listOf(
            MixCard("Shuffle all", "Your whole library, shuffled.", Pal.Lavender, vm.songs, true),
            MixCard("Recently added", "Fresh songs on your phone.", Pal.Accent, recent.take(50), false),
            MixCard("Most played", "What you keep coming back to.", Pal.Peach, top.take(50), false),
            MixCard("Liked songs", "Everything you hearted.", Pal.Sky, liked, false),
        ).filter { it.songs.isNotEmpty() }
    }

    Box(modifier.fillMaxSize()) {
        // The purple glow in the top-left corner.
        Box(
            Modifier
                .fillMaxWidth()
                .height(360.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x99B14BE0), Color(0x553A2A9C), Color.Transparent),
                        center = Offset(0f, 0f),
                        radius = 1100f,
                    ),
                ),
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomPad)) {
            item {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(start = 22.dp, end = 22.dp, top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(54.dp).clip(CircleShape).background(Pal.Accent).clickable(onClick = onOpenSettings),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            vm.userName.take(1).uppercase().ifEmpty { "♪" },
                            color = Pal.OnAccent,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    RoundIconButton(R.drawable.ph_magnifying_glass_bold, "Search", onSearch, size = 52.dp)
                    Spacer(Modifier.width(12.dp))
                    RoundIconButton(R.drawable.ph_heart_bold, "Liked songs", onLiked, size = 52.dp)
                }
            }
            item {
                Text(
                    if (vm.userName.isEmpty()) "Hi there" else "Hi, ${vm.userName}",
                    color = Pal.Text,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 18.dp),
                )
            }
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 22.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    listOf("All", "New", "Most played", "Liked").forEachIndexed { i, label ->
                        Chip(label, chip == i) { chip = i }
                    }
                }
            }

            if (chip == 0) {
                if (mixes.isNotEmpty()) {
                    item { SectionHeader("Curated & trending") }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 22.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            items(mixes) { card ->
                                MixCardView(card) {
                                    if (card.shuffled) vm.playShuffled(card.songs) else vm.playQueue(card.songs, 0)
                                }
                            }
                        }
                    }
                }
                item { SectionHeader("Your playlists", "See all", onSeeAllPlaylists) }
                if (vm.playlists.isEmpty()) {
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 22.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .background(Pal.Panel)
                                .clickable(onClick = onSeeAllPlaylists)
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RoundIconButton(R.drawable.ph_plus_bold, "New playlist", onSeeAllPlaylists, bg = Pal.Accent, tint = Pal.OnAccent)
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text("Create your first playlist", color = Pal.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                Text("Save songs, queues or a whole artist.", color = Pal.Muted, fontSize = 13.sp)
                            }
                        }
                    }
                } else {
                    items(vm.playlists.take(4), key = { it.id }) { p ->
                        val list = vm.playlistSongs(p)
                        MediaRow(
                            title = p.name,
                            subtitle = "By You • ${p.songIds.size} Songs",
                            highlighted = false,
                            art = { GroupArt(list, 76.dp, 22.dp) },
                            trailing = { PlayCircle(false, { if (list.isNotEmpty()) vm.playQueue(list, 0) }) },
                            onClick = { onOpenPlaylist(p.id) },
                        )
                    }
                }
                item { SectionHeader("Recently added") }
                items(recent.take(8), key = { "r${it.id}" }) { s ->
                    SongRow(s, vm, onClick = { vm.playQueue(recent.take(50), recent.indexOf(s)) })
                }
            } else {
                val list = when (chip) {
                    1 -> recent.take(60)
                    2 -> top.take(60)
                    else -> liked
                }
                val heading = when (chip) {
                    1 -> "New"
                    2 -> "Most played"
                    else -> "Liked"
                }
                item { SectionHeader(heading) }
                if (list.isEmpty()) {
                    item {
                        Text(
                            when (chip) {
                                2 -> "Play some music and your favorites will show up here."
                                else -> "Nothing here yet."
                            },
                            color = Pal.Muted,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                        )
                    }
                }
                items(list, key = { "c${it.id}" }) { s ->
                    SongRow(s, vm, onClick = { vm.playQueue(list, list.indexOf(s)) })
                }
            }
        }
    }
}

@Composable
private fun MixCardView(card: MixCard, onPlay: () -> Unit) {
    Box(
        Modifier
            .width(300.dp)
            .height(208.dp)
            .clip(RoundedCornerShape(38.dp))
            .background(card.color),
    ) {
        Column(Modifier.fillMaxHeight().fillMaxWidth(0.62f).padding(start = 24.dp, top = 24.dp, bottom = 22.dp)) {
            Text(card.title, color = Pal.OnPastel, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
            Text(
                card.description,
                color = Pal.OnPastel,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton(R.drawable.ph_play_fill, "Play ${card.title}", onPlay, size = 58.dp, bg = Pal.OnPastel, tint = Color.White)
                Spacer(Modifier.width(10.dp))
                Text("${card.songs.size} songs", color = Pal.OnPastel, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
        ArtThumb(
            card.songs.first(),
            124.dp,
            32.dp,
            Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 18.dp),
        )
    }
}
