package com.asam.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asam.musicplayer.MusicViewModel
import com.asam.musicplayer.Playlist
import com.asam.musicplayer.R

@Composable
fun PlaylistsScreen(
    vm: MusicViewModel,
    bottomPad: Dp,
    onOpenPlaylist: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var creating by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Playlists", color = Pal.Text, fontSize = 34.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            RoundIconButton(R.drawable.ph_plus_bold, "New playlist", { creating = true }, size = 52.dp, bg = Pal.Accent, tint = Pal.OnAccent)
        }
        if (vm.playlists.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(36.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No playlists yet.\nTap + to create one, or open an artist or genre and choose Save as playlist.",
                    color = Pal.Muted,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 21.sp,
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = bottomPad)) {
                itemsIndexed(vm.playlists, key = { _, p -> p.id }) { _, p ->
                    PlaylistRow(vm, p, onOpenPlaylist, Modifier.animateItem())
                }
            }
        }
    }
    if (creating) {
        NameDialog("New playlist", "", "Create", { creating = false }) { name -> vm.createPlaylist(name) }
    }
}

@Composable
private fun PlaylistRow(vm: MusicViewModel, p: Playlist, onOpen: (String) -> Unit, modifier: Modifier) {
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val songs = vm.playlistSongs(p)
    Box(modifier) {
        MediaRow(
            title = p.name,
            subtitle = "By You • ${p.songIds.size} Songs",
            highlighted = false,
            art = { GroupArt(songs, 76.dp, 22.dp) },
            trailing = { PlayCircle(false, { if (songs.isNotEmpty()) vm.playQueue(songs, 0) }) },
            onClick = { onOpen(p.id) },
            onLongClick = { menu = true },
        )
        DropdownMenu(
            expanded = menu,
            onDismissRequest = { menu = false },
            modifier = Modifier.background(Pal.PanelHigh, RoundedCornerShape(24.dp)),
        ) {
            MenuItem("Rename", R.drawable.ph_pencil_simple_bold) {
                menu = false
                renaming = true
            }
            MenuItem("Delete", R.drawable.ph_trash_bold) {
                menu = false
                deleting = true
            }
        }
    }
    if (renaming) NameDialog("Rename playlist", p.name, "Rename", { renaming = false }) { name -> vm.renamePlaylist(p.id, name) }
    if (deleting) {
        ConfirmDialog(
            title = "Delete playlist?",
            message = "\"${p.name}\" will be removed. Your songs stay on the phone.",
            confirmLabel = "Delete",
            onDismiss = { deleting = false },
        ) { vm.deletePlaylist(p.id) }
    }
}
