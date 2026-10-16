package com.asam.musicplayer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asam.musicplayer.MusicViewModel
import com.asam.musicplayer.R

@Composable
fun SearchScreen(vm: MusicViewModel, bottomPad: Dp, modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val q = query.trim()
    val results = remember(vm.songs, q) {
        if (q.isEmpty()) emptyList()
        else vm.songs.filter {
            it.title.contains(q, true) || it.artist.contains(q, true) ||
                it.album.contains(q, true) || it.genre.contains(q, true)
        }.sortedBy { it.title.lowercase() }
    }

    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            "Search",
            color = Pal.Text,
            fontSize = 34.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 12.dp),
        )
        PillTextField(query, { query = it }, "Songs, artists, albums, genres", Modifier.padding(horizontal = 22.dp, vertical = 4.dp))
        when {
            q.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth().padding(36.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Search your library. Then save the results as a playlist.",
                    color = Pal.Muted,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }
            results.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth().padding(36.dp), contentAlignment = Alignment.Center) {
                Text("Nothing matches \"$q\"", color = Pal.Muted, fontSize = 14.sp, textAlign = TextAlign.Center)
            }
            else -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 8.dp, bottom = bottomPad)) {
                item(key = "actions") {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 6.dp)) {
                        ActionPill("Save ${results.size} results as playlist", R.drawable.ph_list_plus_bold, false, Modifier.weight(1f)) {
                            saving = true
                        }
                    }
                }
                itemsIndexed(results, key = { _, s -> s.id }) { index, song ->
                    SongRow(song, vm, onClick = { vm.playQueue(results, index) }, modifier = Modifier.animateItem())
                }
            }
        }
    }
    if (saving) {
        NameDialog("Save results as playlist", q, "Save", { saving = false }) { name ->
            vm.createPlaylist(name, results)
        }
    }
}
