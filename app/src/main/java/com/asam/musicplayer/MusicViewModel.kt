package com.asam.musicplayer

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/** One row of the play queue. [index] is its position in the player's playlist. */
class QueueEntry(val uid: String, val index: Int, val song: Song)

class MusicViewModel(private val app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("library", Context.MODE_PRIVATE)

    /** All user settings (theme, playback, equalizer, lyrics, display). */
    val settings = SettingsState(app)

    var songs by mutableStateOf<List<Song>>(emptyList()); private set
    var currentSong by mutableStateOf<Song?>(null); private set
    var isPlaying by mutableStateOf(false); private set
    var durationMs by mutableLongStateOf(0L); private set
    var positionMs by mutableLongStateOf(0L); private set

    var shuffleOn by mutableStateOf(false); private set
    var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF); private set
    var queue by mutableStateOf<List<QueueEntry>>(emptyList()); private set
    var currentIndex by mutableIntStateOf(-1); private set
    var audioInfo by mutableStateOf(""); private set
    var quality by mutableStateOf<AudioQuality?>(null); private set

    var favorites by mutableStateOf(loadFavorites()); private set
    var playlists by mutableStateOf(PlaylistStore.load(app)); private set
    var playCounts by mutableStateOf(loadPlayCounts()); private set
    var sortMode by mutableStateOf(SortMode.fromKey(prefs.getString("sort", null))); private set
    var userName by mutableStateOf(prefs.getString("name", "Adibul") ?: "Adibul"); private set

    var sleepEndsAtMs by mutableLongStateOf(0L); private set
    var sleepAtSongEnd by mutableStateOf(false); private set
    val sleepActive: Boolean get() = sleepEndsAtMs > 0L || sleepAtSongEnd

    var pendingTarget by mutableStateOf<String?>(null); private set

    var lyrics by mutableStateOf<LyricsUi>(LyricsUi.Idle); private set
    var lyricOffsetMs by mutableLongStateOf(0L); private set
    var onlineLyrics by mutableStateOf(LyricsRepository.onlineEnabled(app)); private set

    private var byId: Map<Long, Song> = emptyMap()
    private var controller: MediaController? = null
    private var lyricsSongId = -1L
    private var reloadJob: Job? = null
    private var sleepJob: Job? = null
    private var uidCounter = 0L
    private var restored = false
    private var lastCountedUid: String? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            val rebuild = events.containsAny(
                Player.EVENT_TIMELINE_CHANGED,
                Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
            )
            syncState(timelineChanged = rebuild)
            if (events.containsAny(Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                persistState()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val naturalEnd = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
            if (sleepAtSongEnd && naturalEnd) {
                controller?.pause()
                cancelSleep()
            }
        }
    }

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            reloadJob?.cancel()
            reloadJob = viewModelScope.launch {
                delay(1500)
                loadSongs()
            }
        }
    }

    init {
        app.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            controller = future.get().also {
                it.addListener(listener)
                it.setPlaybackSpeed(settings.speed)
            }
            syncState(timelineChanged = true)
            maybeRestore()
        }, ContextCompat.getMainExecutor(app))
    }

    fun loadSongs() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { MusicRepository.loadSongs(app) }
            songs = list
            byId = list.associateBy { it.id }
            syncState(timelineChanged = true)
            maybeRestore()
        }
    }

    private fun songOf(mediaId: String): Song? = byId[mediaId.substringBefore('#').toLongOrNull() ?: -1L]

    private fun syncState(timelineChanged: Boolean = false) {
        val c = controller ?: return
        if (timelineChanged) rebuildQueue(c)
        val song = c.currentMediaItem?.mediaId?.let { songOf(it) }
        if (song?.id != currentSong?.id) {
            currentSong = song
            lyrics = LyricsUi.Idle
            lyricsSongId = -1L
            lyricOffsetMs = song?.let { LyricsRepository.offset(app, it.id) } ?: 0L
        }
        currentIndex = c.currentMediaItemIndex
        isPlaying = c.isPlaying
        shuffleOn = c.shuffleModeEnabled
        repeatMode = c.repeatMode
        durationMs = c.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: song?.durationMs ?: 0L
        positionMs = c.currentPosition
        quality = buildQuality(c, song)
        audioInfo = quality?.label.orEmpty()

        // Count a play once per queue entry, when it actually starts playing.
        val uid = c.currentMediaItem?.mediaId
        if (c.isPlaying && song != null && uid != lastCountedUid) {
            lastCountedUid = uid
            val updated = (playCounts[song.id] ?: 0) + 1
            playCounts = playCounts + (song.id to updated)
            prefs.edit().putInt("pc_${song.id}", updated).apply()
        }
    }

    /** Queue rows in the order they will actually play (respects shuffle). */
    private fun rebuildQueue(c: MediaController) {
        val count = c.mediaItemCount
        val order = ArrayList<Int>(count)
        val timeline = c.currentTimeline
        if (!timeline.isEmpty) {
            val shuffle = c.shuffleModeEnabled
            var i = timeline.getFirstWindowIndex(shuffle)
            while (i != C.INDEX_UNSET && order.size <= count) {
                order += i
                i = timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, shuffle)
            }
        }
        if (order.size != count) {
            order.clear()
            for (i in 0 until count) order += i
        }
        val list = ArrayList<QueueEntry>(count)
        for (i in order) {
            val item = c.getMediaItemAt(i)
            val song = songOf(item.mediaId) ?: continue
            list += QueueEntry(item.mediaId, i, song)
        }
        queue = list
    }

    @OptIn(UnstableApi::class)
    private fun buildQuality(c: MediaController, song: Song?): AudioQuality? {
        for (group in c.currentTracks.groups) {
            if (group.type == C.TRACK_TYPE_AUDIO && group.isSelected && group.length > 0) {
                val f = group.getTrackFormat(0)
                val mime = f.sampleMimeType ?: return null
                val lossless = mime == "audio/flac" || mime == "audio/alac" || mime == "audio/raw"
                var depth = pcmBits(f.pcmEncoding)
                if (depth <= 0 && mime == "audio/flac") depth = flacBitDepth(f.initializationData)
                var kbps = if (f.bitrate > 0) (f.bitrate / 1000.0).roundToInt() else 0
                if (kbps <= 0 && song != null && song.durationMs > 0 && song.sizeBytes > 0) {
                    kbps = (song.sizeBytes * 8.0 / (song.durationMs / 1000.0) / 1000.0).roundToInt()
                }
                return AudioQuality(formatName(mime) ?: mime, lossless, depth, kbps, f.sampleRate.coerceAtLeast(0))
            }
        }
        return null
    }

    private fun pcmBits(encoding: Int): Int = when (encoding) {
        C.ENCODING_PCM_8BIT -> 8
        C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN -> 16
        C.ENCODING_PCM_24BIT -> 24
        C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 32
        else -> 0
    }

    /** Reads bits-per-sample from a FLAC STREAMINFO block when the player did not report it. */
    private fun flacBitDepth(data: List<ByteArray>): Int {
        return try {
            val b = data.firstOrNull() ?: return 0
            val hasMarker = b.size >= 4 && b[0] == 'f'.code.toByte() && b[1] == 'L'.code.toByte() &&
                b[2] == 'a'.code.toByte() && b[3] == 'C'.code.toByte()
            val start = if (hasMarker) 8 else 0
            if (b.size < start + 14) {
                0
            } else {
                val bits = (((b[start + 12].toInt() and 0x01) shl 4) or ((b[start + 13].toInt() and 0xF0) shr 4)) + 1
                if (bits in 8..32) bits else 0
            }
        } catch (e: Exception) {
            0
        }
    }

    private fun formatName(mime: String?): String? = when (mime) {
        null -> null
        "audio/mpeg" -> "MP3"
        "audio/flac" -> "FLAC"
        "audio/mp4a-latm" -> "AAC"
        "audio/opus" -> "Opus"
        "audio/vorbis" -> "Vorbis"
        "audio/raw" -> "WAV"
        "audio/alac" -> "ALAC"
        else -> mime.substringAfter('/').uppercase()
    }

    // ---- resume where you left off ----

    /** Saves the queue, position, shuffle and repeat so the next launch can pick up where you stopped. */
    fun persistState() {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return
        val ids = JSONArray()
        for (i in 0 until c.mediaItemCount) {
            val id = c.getMediaItemAt(i).mediaId.substringBefore('#').toLongOrNull() ?: continue
            ids.put(id)
        }
        val o = JSONObject()
            .put("ids", ids)
            .put("index", c.currentMediaItemIndex)
            .put("pos", c.currentPosition)
            .put("shuffle", c.shuffleModeEnabled)
            .put("repeat", c.repeatMode)
        prefs.edit().putString("resume", o.toString()).apply()
    }

    private fun maybeRestore() {
        val c = controller ?: return
        if (restored || songs.isEmpty()) return
        restored = true
        if (c.mediaItemCount > 0) return // the playback service is still alive with a queue
        val raw = prefs.getString("resume", null) ?: return
        try {
            val o = JSONObject(raw)
            val ids = o.getJSONArray("ids")
            val oldIndex = o.optInt("index", 0)
            val items = ArrayList<MediaItem>()
            var newIndex = 0
            for (i in 0 until ids.length()) {
                if (i == oldIndex) newIndex = items.size
                val song = byId[ids.getLong(i)] ?: continue
                items += entryItem(song)
            }
            if (items.isEmpty()) return
            c.shuffleModeEnabled = o.optBoolean("shuffle", false)
            c.repeatMode = o.optInt("repeat", Player.REPEAT_MODE_OFF)
            c.setMediaItems(items, newIndex.coerceIn(0, items.size - 1), o.optLong("pos", 0L))
            c.prepare()
        } catch (e: Exception) {
            // Corrupt resume data is ignored.
        }
    }

    // ---- playback ----

    private fun entryItem(song: Song): MediaItem = song.toMediaItem("${song.id}#${uidCounter++}")

    fun playQueue(list: List<Song>, index: Int) {
        val c = controller ?: return
        c.setMediaItems(list.map { entryItem(it) }, index, 0L)
        c.prepare()
        c.play()
    }

    fun playShuffled(list: List<Song>) {
        val c = controller ?: return
        if (list.isEmpty()) return
        c.shuffleModeEnabled = true
        c.setMediaItems(list.map { entryItem(it) }, Random.nextInt(list.size), 0L)
        c.prepare()
        c.play()
    }

    fun playNext(song: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            playQueue(listOf(song), 0)
            return
        }
        c.addMediaItem(c.currentMediaItemIndex + 1, entryItem(song))
        toast("Playing next")
    }

    fun addToQueue(song: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            playQueue(listOf(song), 0)
            return
        }
        c.addMediaItem(entryItem(song))
        toast("Added to queue")
    }

    fun playQueueIndex(mediaIndex: Int) {
        controller?.let {
            it.seekToDefaultPosition(mediaIndex)
            it.play()
        }
    }

    fun removeFromQueue(mediaIndex: Int) {
        controller?.removeMediaItem(mediaIndex)
    }

    fun clearUpNext() {
        val c = controller ?: return
        val from = c.currentMediaItemIndex + 1
        if (from < c.mediaItemCount) c.removeMediaItems(from, c.mediaItemCount)
    }

    /** Drag-to-reorder. Only allowed with shuffle off, where queue position equals playlist position. */
    fun moveQueueItem(from: Int, to: Int) {
        val c = controller ?: return
        if (shuffleOn || from == to || from !in queue.indices || to !in queue.indices) return
        c.moveMediaItem(queue[from].index, queue[to].index)
        val list = queue.toMutableList()
        val moved = list.removeAt(from)
        list.add(to, moved)
        queue = list.mapIndexed { i, e -> QueueEntry(e.uid, i, e.song) }
    }

    fun togglePlay() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun previous() {
        controller?.let { if (it.currentPosition > 3000) it.seekTo(0) else it.seekToPreviousMediaItem() }
    }

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms.coerceAtLeast(0))
        positionMs = ms.coerceAtLeast(0)
    }

    /** Called every frame while Now Playing is visible, so the seek bar and lyrics move smoothly at 120 Hz. */
    fun tick() {
        controller?.let { positionMs = it.currentPosition }
    }

    fun updateSpeed(value: Float) {
        settings.updateSpeed(value)
        controller?.setPlaybackSpeed(value)
    }

    fun resetSettings() {
        settings.resetAll()
        controller?.setPlaybackSpeed(1f)
        toast("Settings reset")
    }

    // ---- library preferences ----

    fun updateSort(mode: SortMode) {
        sortMode = mode
        prefs.edit().putString("sort", mode.key).apply()
    }

    fun updateUserName(name: String) {
        val clean = name.trim()
        userName = clean
        prefs.edit().putString("name", clean).apply()
    }

    private fun loadPlayCounts(): Map<Long, Int> {
        val out = HashMap<Long, Int>()
        for (entry in prefs.all.entries) {
            val v = entry.value
            if (entry.key.startsWith("pc_") && v is Int) {
                val id = entry.key.removePrefix("pc_").toLongOrNull() ?: continue
                out[id] = v
            }
        }
        return out
    }

    // ---- favorites ----

    private fun loadFavorites(): Set<Long> =
        (prefs.getStringSet("favorites", emptySet()) ?: emptySet()).mapNotNull { it.toLongOrNull() }.toSet()

    fun toggleFavorite(song: Song) {
        val updated = if (favorites.contains(song.id)) favorites - song.id else favorites + song.id
        favorites = updated
        prefs.edit().putStringSet("favorites", updated.map { it.toString() }.toSet()).apply()
    }

    // ---- playlists ----

    private fun commitPlaylists(list: List<Playlist>) {
        playlists = list
        viewModelScope.launch(Dispatchers.IO) { PlaylistStore.save(app, list) }
    }

    fun createPlaylist(name: String, initial: List<Song> = emptyList()) {
        val clean = name.trim().ifEmpty { "New playlist" }
        val p = Playlist(UUID.randomUUID().toString(), clean, initial.map { it.id }.distinct(), System.currentTimeMillis())
        commitPlaylists(playlists + p)
        toast(if (initial.isEmpty()) "Playlist created" else "Playlist saved with ${initial.size} songs")
    }

    fun renamePlaylist(id: String, name: String) {
        val clean = name.trim().ifEmpty { return }
        commitPlaylists(playlists.map { if (it.id == id) it.copy(name = clean) else it })
    }

    fun deletePlaylist(id: String) {
        commitPlaylists(playlists.filter { it.id != id })
    }

    fun addToPlaylist(id: String, add: List<Song>) {
        val p = playlists.firstOrNull { it.id == id } ?: return
        val merged = (p.songIds + add.map { it.id }).distinct()
        val added = merged.size - p.songIds.size
        commitPlaylists(playlists.map { if (it.id == id) it.copy(songIds = merged) else it })
        toast(if (added == 0) "Already in ${p.name}" else "Added $added to ${p.name}")
    }

    fun removeFromPlaylist(id: String, songId: Long) {
        commitPlaylists(playlists.map { if (it.id == id) it.copy(songIds = it.songIds.filter { s -> s != songId }) else it })
    }

    fun playlistSongs(p: Playlist): List<Song> = p.songIds.mapNotNull { byId[it] }

    fun saveQueueAsPlaylist(name: String) {
        createPlaylist(name, queue.sortedBy { it.index }.map { it.song })
    }

    // ---- backup and restore (playlists, favorites, play counts) ----

    private fun songRef(s: Song): JSONObject =
        JSONObject().put("id", s.id).put("title", s.title).put("artist", s.artist).put("dur", s.durationMs / 1000)

    private fun buildBackupJson(): String {
        val root = JSONObject().put("app", "MusicPlayer").put("version", 1)
        val pls = JSONArray()
        for (p in playlists) {
            val arr = JSONArray()
            for (id in p.songIds) {
                val song = byId[id] ?: continue
                arr.put(songRef(song))
            }
            pls.put(JSONObject().put("id", p.id).put("name", p.name).put("songs", arr))
        }
        root.put("playlists", pls)
        val fav = JSONArray()
        for (id in favorites) {
            val song = byId[id] ?: continue
            fav.put(songRef(song))
        }
        root.put("favorites", fav)
        val plays = JSONArray()
        for (entry in playCounts.entries) {
            val song = byId[entry.key] ?: continue
            plays.put(songRef(song).put("count", entry.value))
        }
        root.put("plays", plays)
        return root.toString(2)
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = try {
                val json = buildBackupJson()
                app.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray()) } != null
            } catch (e: Exception) {
                false
            }
            withContext(Dispatchers.Main) { toast(if (ok) "Backup saved" else "Couldn't save the backup") }
        }
    }

    /** Replaces playlists, favorites and play counts with the ones in the file. Songs are matched by id, then by title and artist. */
    fun importBackup(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val text = app.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: throw IllegalStateException("Empty file")
                val root = JSONObject(text)
                val byTitle = songs.groupBy { it.title.lowercase() }

                fun resolve(ref: JSONObject): Song? {
                    val title = ref.optString("title")
                    val artist = ref.optString("artist")
                    val dur = ref.optLong("dur", 0L)
                    val direct = byId[ref.optLong("id", -1L)]
                    if (direct != null && direct.title.equals(title, true)) return direct
                    val candidates = byTitle[title.lowercase()].orEmpty().filter { it.artist.equals(artist, true) }
                    val best = candidates.minByOrNull { abs(it.durationMs / 1000 - dur) }
                    return if (best != null && abs(best.durationMs / 1000 - dur) <= 3) best else candidates.firstOrNull()
                }

                val newPlaylists = ArrayList<Playlist>()
                val pa = root.optJSONArray("playlists")
                if (pa != null) {
                    for (i in 0 until pa.length()) {
                        val o = pa.getJSONObject(i)
                        val ids = ArrayList<Long>()
                        val sa = o.optJSONArray("songs")
                        if (sa != null) {
                            for (j in 0 until sa.length()) {
                                val song = resolve(sa.getJSONObject(j)) ?: continue
                                ids += song.id
                            }
                        }
                        newPlaylists += Playlist(
                            o.optString("id", UUID.randomUUID().toString()),
                            o.optString("name", "Playlist"),
                            ids.distinct(),
                            System.currentTimeMillis(),
                        )
                    }
                }
                val newFavorites = HashSet<Long>()
                val fa = root.optJSONArray("favorites")
                if (fa != null) {
                    for (i in 0 until fa.length()) {
                        val song = resolve(fa.getJSONObject(i)) ?: continue
                        newFavorites += song.id
                    }
                }
                val newCounts = HashMap<Long, Int>()
                val ca = root.optJSONArray("plays")
                if (ca != null) {
                    for (i in 0 until ca.length()) {
                        val o = ca.getJSONObject(i)
                        val song = resolve(o) ?: continue
                        newCounts[song.id] = o.optInt("count", 0)
                    }
                }
                withContext(Dispatchers.Main) {
                    commitPlaylists(newPlaylists)
                    favorites = newFavorites
                    val editor = prefs.edit()
                    editor.putStringSet("favorites", newFavorites.map { it.toString() }.toSet())
                    for (key in prefs.all.keys) if (key.startsWith("pc_")) editor.remove(key)
                    for (entry in newCounts.entries) editor.putInt("pc_${entry.key}", entry.value)
                    editor.apply()
                    playCounts = newCounts
                    toast("Restored ${newPlaylists.size} playlists and ${newFavorites.size} favorites")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { toast("That file isn't a valid backup") }
            }
        }
    }

    // ---- sleep timer ----

    fun startSleepTimer(minutes: Int) {
        cancelSleep()
        val ms = minutes * 60_000L
        sleepEndsAtMs = System.currentTimeMillis() + ms
        sleepJob = viewModelScope.launch {
            delay(ms)
            controller?.pause()
            sleepEndsAtMs = 0L
        }
        toast("Sleep timer: $minutes min")
    }

    fun sleepAfterThisSong() {
        cancelSleep()
        sleepAtSongEnd = true
        toast("Will stop after this song")
    }

    fun cancelSleep() {
        sleepJob?.cancel()
        sleepJob = null
        sleepEndsAtMs = 0L
        sleepAtSongEnd = false
    }

    // ---- navigation helpers ----

    fun goToAlbum(song: Song) {
        pendingTarget = "al${song.albumId}|${song.album}"
    }

    fun goToArtist(song: Song) {
        pendingTarget = "ar${song.artist}"
    }

    fun consumeTarget() {
        pendingTarget = null
    }

    private fun toast(text: String) = Toast.makeText(app, text, Toast.LENGTH_SHORT).show()

    // ---- lyrics ----

    fun ensureLyrics(force: Boolean = false) {
        val song = currentSong ?: return
        if (!force && lyricsSongId == song.id && lyrics !is LyricsUi.Idle) return
        lyricsSongId = song.id
        lyrics = LyricsUi.Loading
        viewModelScope.launch {
            val result = LyricsRepository.load(app, song, force)
            if (currentSong?.id == song.id) lyrics = result
        }
    }

    fun retryLyrics() = ensureLyrics(force = true)

    fun nudgeLyricOffset(deltaMs: Long) {
        val song = currentSong ?: return
        lyricOffsetMs += deltaMs
        LyricsRepository.setOffset(app, song.id, lyricOffsetMs)
    }

    fun updateOnlineLyrics(enabled: Boolean) {
        onlineLyrics = enabled
        LyricsRepository.setOnlineEnabled(app, enabled)
    }

    fun clearLyricsCache() {
        LyricsRepository.clearCache(app)
        lyrics = LyricsUi.Idle
        lyricsSongId = -1L
    }

    override fun onCleared() {
        app.contentResolver.unregisterContentObserver(observer)
        controller?.let {
            it.removeListener(listener)
            it.release()
        }
        super.onCleared()
    }
}
