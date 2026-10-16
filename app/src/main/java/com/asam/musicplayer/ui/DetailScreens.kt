package com.asam.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asam.musicplayer.MusicViewModel
import com.asam.musicplayer.R
import com.asam.musicplayer.Song

@Composable
private fun DetailHeader(
    title: String,
    subtitle: String,
    songs: List<Song>,
    onBack: () -> Unit,
    menu: @Composable () -> Unit = {},
) {
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 22.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(R.drawable.ph_arrow_left_bold, "Back", onBack, size = 52.dp)
            Spacer(Modifier.weight(1f))
            menu()
        }
        Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            GroupArt(songs, 112.dp, 30.dp, R.drawable.ph_disc_bold)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Pal.Text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = Pal.Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * An album, artist or genre. This is where you turn a filter into a playlist:
 * "Save as playlist" copies every song shown here into a new playlist.
 */
@Composable
fun BucketDetailScreen(vm: MusicViewModel, bucket: Bucket, onBack: () -> Unit, bottomPad: Dp, modifier: Modifier = Modifier) {
    var saving by remember { mutableStateOf(false) }
    var addSheet by remember { mutableStateOf(false) }
    val songs = remember(bucket) { bucket.songs.sortedBy { it.title.lowercase() } }
    Column(modifier.fillMaxSize()) {
        DetailHeader(bucket.title, bucket.subtitle, songs, onBack)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = bottomPad)) {
            item(key = "actions") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ActionPill("Play", R.drawable.ph_play_fill, true, Modifier.weight(1f)) { vm.playQueue(songs, 0) }
                        ActionPill("Shuffle", R.drawable.ph_shuffle_bold, false, Modifier.weight(1f)) { vm.playShuffled(songs) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ActionPill("Save as playlist", R.drawable.ph_list_plus_bold, false, Modifier.weight(1f)) { saving = true }
                        ActionPill("Add to playlist", R.drawable.ph_playlist_bold, false, Modifier.weight(1f)) { addSheet = true }
                    }
                }
            }
            itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
                SongRow(song, vm, onClick = { vm.playQueue(songs, index) }, modifier = Modifier.animateItem())
            }
        }
    }
    if (saving) {
        NameDialog("Save as playlist", bucket.title, "Save", { saving = false }) { name ->
            vm.createPlaylist(name, songs)
        }
    }
    if (addSheet) AddToPlaylistSheet(vm, songs) { addSheet = false }
}

@Composable
fun PlaylistDetailScreen(vm: MusicViewModel, playlistId: String, onBack: () -> Unit, bottomPad: Dp, modifier: Modifier = Modifier) {
    val playlist = vm.playlists.firstOrNull { it.id == playlistId }
    if (playlist == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Playlist not found", color = Pal.Muted, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
        return
    }
    val songs = vm.playlistSongs(playlist)
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {
        DetailHeader(
            title = playlist.name,
            subtitle = "By You • ${songs.size} Songs",
            songs = songs,
            onBack = onBack,
            menu = {
                Box {
                    RoundIconButton(R.drawable.ph_dots_three_bold, "More", { menu = true }, size = 52.dp)
                    DropdownMenu(
                        expanded = menu,
                        onDismissRequest = { menu = false },
                        modifier = Modifier.background(Pal.PanelHigh, RoundedCornerShape(24.dp)),
                    ) {
                        MenuItem("Rename", R.drawable.ph_pencil_simple_bold) {
                            menu = false
                            renaming = true
                        }
                        MenuItem("Delete playlist", R.drawable.ph_trash_bold) {
                            menu = false
                            deleting = true
                        }
                    }
                }
            },
        )
        if (songs.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "This playlist is empty. Long-press any song and choose Add to playlist.",
                    color = Pal.Muted,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = bottomPad)) {
                item(key = "actions") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ActionPill("Play", R.drawable.ph_play_fill, true, Modifier.weight(1f)) { vm.playQueue(songs, 0) }
                        ActionPill("Shuffle", R.drawable.ph_shuffle_bold, false, Modifier.weight(1f)) { vm.playShuffled(songs) }
                    }
                }
                itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
                    SongRow(
                        song, vm,
                        onClick = { vm.playQueue(songs, index) },
                        modifier = Modifier.animateItem(),
                        extraLabel = "Remove from playlist",
                        onExtra = { vm.removeFromPlaylist(playlist.id, song.id) },
                    )
                }
            }
        }
    }
    if (renaming) {
        NameDialog("Rename playlist", playlist.name, "Rename", { renaming = false }) { name ->
            vm.renamePlaylist(playlist.id, name)
        }
    }
    if (deleting) {
        ConfirmDialog(
            title = "Delete playlist?",
            message = "\"${playlist.name}\" will be removed. Your songs stay on the phone.",
            confirmLabel = "Delete",
            onDismiss = { deleting = false },
        ) {
            vm.deletePlaylist(playlist.id)
            onBack()
        }
    }
}
