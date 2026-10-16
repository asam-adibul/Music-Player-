package com.asam.musicplayer.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asam.musicplayer.MusicViewModel
import com.asam.musicplayer.R
import com.asam.musicplayer.Song
import com.asam.musicplayer.SortMode
import com.asam.musicplayer.isAlphabetic
import com.asam.musicplayer.sectionLetter
import com.asam.musicplayer.sortSongs
import kotlinx.coroutines.launch

/** "My Music": All / Liked / Albums / Artists / Genres, with sorting and an A-Z scrubber. */
@Composable
fun MusicScreen(
    vm: MusicViewModel,
    buckets: Buckets,
    chip: Int,
    onChip: (Int) -> Unit,
    onBack: () -> Unit,
    onOpenBucket: (Bucket) -> Unit,
    bottomPad: Dp,
    modifier: Modifier = Modifier,
) {
    var sortMenu by remember { mutableStateOf(false) }
    val sorted = remember(vm.songs, vm.sortMode, vm.playCounts) { sortSongs(vm.songs, vm.sortMode, vm.playCounts) }
    val liked = remember(sorted, vm.favorites) { sorted.filter { vm.favorites.contains(it.id) } }

    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIconButton(R.drawable.ph_arrow_left_bold, "Back", onBack, size = 52.dp)
            Text(
                "My Music",
                color = Pal.Text,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Box {
                RoundIconButton(R.drawable.ph_sort_ascending_bold, "Sort", { sortMenu = true }, size = 52.dp)
                DropdownMenu(
                    expanded = sortMenu,
                    onDismissRequest = { sortMenu = false },
                    modifier = Modifier.background(Pal.PanelHigh, RoundedCornerShape(24.dp)),
                ) {
                    SortMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    mode.label,
                                    color = if (mode == vm.sortMode) Pal.AccentText else Pal.Text,
                                    fontWeight = if (mode == vm.sortMode) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            },
                            leadingIcon = {
                                if (mode == vm.sortMode) PhIcon(R.drawable.ph_check_bold, null, Pal.Accent, size = 18.dp)
                                else Spacer(Modifier.width(18.dp))
                            },
                            onClick = {
                                sortMenu = false
                                vm.updateSort(mode)
                            },
                        )
                    }
                }
            }
        }

        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            listOf("All", "Liked", "Albums", "Artists", "Genres").forEachIndexed { i, label ->
                Chip(label, chip == i) { onChip(i) }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (chip) {
                0 -> SongListWithScroller(sorted, vm, bottomPad, showActions = true)
                1 -> SongListWithScroller(liked, vm, bottomPad, showActions = true, emptyText = "Tap the heart on Now Playing to add favorites.")
                2 -> BucketList(buckets.albums, vm, bottomPad, onOpenBucket)
                3 -> BucketList(buckets.artists, vm, bottomPad, onOpenBucket)
                else -> BucketList(buckets.genres, vm, bottomPad, onOpenBucket)
            }
        }
    }
}

/** Song list with Play / Shuffle pills and, when sorted A-Z, a fast-scroll letter rail. */
@Composable
fun SongListWithScroller(
    list: List<Song>,
    vm: MusicViewModel,
    bottomPad: Dp,
    showActions: Boolean,
    emptyText: String = "",
) {
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                when {
                    emptyText.isNotEmpty() -> emptyText
                    vm.songs.isEmpty() -> "No music found on this phone"
                    else -> "Nothing here yet"
                },
                color = Pal.Muted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    val mode = vm.sortMode
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val firstIndexOfLetter = remember(list, mode) {
        val map = LinkedHashMap<Char, Int>()
        if (mode.isAlphabetic()) {
            list.forEachIndexed { i, s -> map.putIfAbsent(sectionLetter(s, mode), i) }
        }
        map
    }
    val headerCount = if (showActions) 1 else 0

    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = bottomPad)) {
            if (showActions) {
                item(key = "actions") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ActionPill("Play all", R.drawable.ph_play_fill, true, Modifier.weight(1f)) { vm.playQueue(list, 0) }
                        ActionPill("Shuffle", R.drawable.ph_shuffle_bold, false, Modifier.weight(1f)) { vm.playShuffled(list) }
                    }
                }
            }
            itemsIndexed(list, key = { _, s -> s.id }) { index, song ->
                SongRow(song, vm, onClick = { vm.playQueue(list, index) }, modifier = Modifier.animateItem())
            }
        }
        if (firstIndexOfLetter.size > 1 && list.size > 25) {
            FastScroller(
                letters = firstIndexOfLetter.keys.toList(),
                onPick = { letter ->
                    val target = firstIndexOfLetter[letter] ?: 0
                    scope.launch { listState.scrollToItem(target + headerCount) }
                },
            )
        }
    }
}

@Composable
private fun BucketList(list: List<Bucket>, vm: MusicViewModel, bottomPad: Dp, onOpen: (Bucket) -> Unit) {
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("Nothing here yet", color = Pal.Muted, fontSize = 14.sp)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomPad)) {
        itemsIndexed(list, key = { _, b -> b.key }) { _, bucket ->
            MediaRow(
                title = bucket.title,
                subtitle = bucket.subtitle,
                highlighted = false,
                art = { GroupArt(bucket.songs, 68.dp, 20.dp, R.drawable.ph_disc_bold) },
                trailing = { PlayCircle(false, { vm.playQueue(bucket.songs, 0) }) },
                onClick = { onOpen(bucket) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}
