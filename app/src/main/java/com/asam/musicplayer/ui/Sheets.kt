package com.asam.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.asam.musicplayer.MusicViewModel
import com.asam.musicplayer.R
import com.asam.musicplayer.Song
import kotlinx.coroutines.delay

private val SheetShape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)
private val QueueRowHeight = 72.dp

@Composable
private fun SheetPillButton(text: String, modifier: Modifier = Modifier, accent: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (accent) Pal.Accent else Pal.PanelHigh)
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (accent) Pal.OnAccent else Pal.Text,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** The play queue in the order songs will actually play. Drag the handle to reorder (shuffle off). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(vm: MusicViewModel, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSave by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        shape = SheetShape,
        containerColor = Pal.Panel,
        contentColor = Pal.Text,
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.86f)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Queue", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Pal.Text)
                Spacer(Modifier.width(10.dp))
                Text("${vm.queue.size}", fontSize = 13.sp, color = Pal.Muted, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.weight(1f))
                SheetPillButton("Save as playlist") { showSave = true }
                Spacer(Modifier.width(8.dp))
                SheetPillButton("Clear") { vm.clearUpNext() }
            }
            Text(
                if (vm.shuffleOn) "Shuffle is on, showing the order songs will play. Turn shuffle off to reorder."
                else "Drag the handle to reorder.",
                fontSize = 12.sp,
                color = Pal.Muted,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp),
            )
            if (vm.queue.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    Text("Nothing in the queue", color = Pal.Muted, fontSize = 14.sp)
                }
            } else {
                QueueList(vm)
            }
        }
    }
    if (showSave) {
        NameDialog("Save queue as playlist", "My queue", "Save", { showSave = false }) { name ->
            vm.saveQueueAsPlaylist(name)
        }
    }
}

@Composable
private fun QueueList(vm: MusicViewModel) {
    val start = maxOf(0, vm.queue.indexOfFirst { it.index == vm.currentIndex } - 1)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = start)
    val rowPx = with(LocalDensity.current) { QueueRowHeight.toPx() }
    var draggingUid by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
        itemsIndexed(vm.queue, key = { _, e -> e.uid }) { _, entry ->
            val current = entry.index == vm.currentIndex
            val dragging = draggingUid == entry.uid
            // The dragged row follows your finger; the others glide out of the way.
            val rowModifier = if (dragging) {
                Modifier
                    .zIndex(1f)
                    .graphicsLayer {
                        translationY = dragOffset
                        scaleX = 1.02f
                        scaleY = 1.02f
                    }
            } else {
                Modifier.animateItem()
            }
            Row(
                rowModifier
                    .fillMaxWidth()
                    .height(QueueRowHeight)
                    .padding(horizontal = 12.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(if (dragging) Pal.PanelHigh else if (current) Pal.Bg else Color.Transparent)
                    .clickable { vm.playQueueIndex(entry.index) }
                    .padding(start = 8.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtThumb(entry.song, 52.dp, 16.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        entry.song.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (current) Pal.AccentText else Pal.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(entry.song.artist, fontSize = 12.sp, color = Pal.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TapIcon(R.drawable.ph_x_bold, "Remove from queue", Pal.Muted, { vm.removeFromQueue(entry.index) }, size = 16.dp, box = 40.dp)
                if (!vm.shuffleOn) {
                    Box(
                        Modifier
                            .width(44.dp)
                            .fillMaxHeight()
                            .pointerInput(entry.uid) {
                                detectDragGestures(
                                    onDragStart = {
                                        draggingUid = entry.uid
                                        dragOffset = 0f
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount.y
                                        val pos = vm.queue.indexOfFirst { it.uid == entry.uid }
                                        if (pos >= 0) {
                                            if (dragOffset > rowPx / 2 && pos < vm.queue.lastIndex) {
                                                vm.moveQueueItem(pos, pos + 1)
                                                dragOffset -= rowPx
                                            } else if (dragOffset < -rowPx / 2 && pos > 0) {
                                                vm.moveQueueItem(pos, pos - 1)
                                                dragOffset += rowPx
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        draggingUid = null
                                        dragOffset = 0f
                                    },
                                    onDragCancel = {
                                        draggingUid = null
                                        dragOffset = 0f
                                    },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        PhIcon(R.drawable.ph_dots_six_vertical_bold, "Reorder", Pal.Muted, size = 22.dp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepSheet(vm: MusicViewModel, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val remaining by produceState(0L, vm.sleepEndsAtMs) {
        while (true) {
            value = (vm.sleepEndsAtMs - System.currentTimeMillis()).coerceAtLeast(0L)
            delay(1000)
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        shape = SheetShape,
        containerColor = Pal.Panel,
        contentColor = Pal.Text,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 28.dp)) {
            Text("Sleep timer", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Pal.Text)
            val status = when {
                vm.sleepEndsAtMs > 0L -> "Music stops in ${formatTime(remaining)}"
                vm.sleepAtSongEnd -> "Music stops after this song"
                else -> "Pick when the music should stop"
            }
            Text(status, fontSize = 13.sp, color = Pal.Muted, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
            listOf(listOf(5, 10, 15), listOf(30, 45, 60)).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { minutes ->
                        SheetPillButton("$minutes min", Modifier.weight(1f)) {
                            vm.startSleepTimer(minutes)
                            onDismiss()
                        }
                    }
                }
            }
            SheetPillButton("End of this song", Modifier.fillMaxWidth()) {
                vm.sleepAfterThisSong()
                onDismiss()
            }
            if (vm.sleepActive) {
                Spacer(Modifier.height(10.dp))
                SheetPillButton("Turn off timer", Modifier.fillMaxWidth(), accent = true) {
                    vm.cancelSleep()
                    onDismiss()
                }
            }
        }
    }
}

/** Pick a playlist for one or more songs, or create a new one right here. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(vm: MusicViewModel, songs: List<Song>, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var creating by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        shape = SheetShape,
        containerColor = Pal.Panel,
        contentColor = Pal.Text,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                if (songs.size == 1) "Add to playlist" else "Add ${songs.size} songs to playlist",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = Pal.Text,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .clickable { creating = true }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(Pal.Accent),
                    contentAlignment = Alignment.Center,
                ) { PhIcon(R.drawable.ph_plus_bold, null, Pal.OnAccent, size = 24.dp) }
                Spacer(Modifier.width(14.dp))
                Text("New playlist", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Pal.Text)
            }
            LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.5f)) {
                itemsIndexed(vm.playlists, key = { _, p -> p.id }) { _, p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .clickable {
                                vm.addToPlaylist(p.id, songs)
                                onDismiss()
                            }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GroupArt(vm.playlistSongs(p), 56.dp, 18.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Pal.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${p.songIds.size} songs", fontSize = 12.sp, color = Pal.Muted)
                        }
                    }
                }
            }
        }
    }
    if (creating) {
        NameDialog("New playlist", "", "Create", { creating = false }) { name ->
            vm.createPlaylist(name, songs)
            onDismiss()
        }
    }
}

@Composable
fun SongInfoDialog(vm: MusicViewModel, song: Song, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(32.dp),
        containerColor = Pal.Panel,
        titleContentColor = Pal.Text,
        textContentColor = Pal.Muted,
        title = { Text("Song info", fontSize = 20.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoLine("Title", song.title)
                InfoLine("Artist", song.artist)
                InfoLine("Album", song.album)
                InfoLine("Genre", song.genre)
                InfoLine("Length", formatTime(song.durationMs))
                if (vm.audioInfo.isNotEmpty()) InfoLine("Format", vm.audioInfo)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = Pal.AccentText, fontWeight = FontWeight.SemiBold) } },
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column {
        Text(label, fontSize = 11.sp, color = Pal.Muted)
        Text(value, fontSize = 15.sp, color = Pal.Text)
    }
}
