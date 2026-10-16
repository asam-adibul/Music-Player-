package com.asam.musicplayer.ui

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.asam.musicplayer.ArtResult
import com.asam.musicplayer.MusicViewModel
import com.asam.musicplayer.R
import com.asam.musicplayer.Song
import kotlinx.coroutines.launch

private val GlassButton = Color(0x661E1E2B)

/**
 * Now Playing, following the reference design: a backdrop tinted from the album art, a big round
 * cover, a three-line lyrics teaser, seek bar and shuffle / previous / play / next / queue.
 */
@Composable
fun NowPlayingScreen(
    vm: MusicViewModel,
    song: Song,
    art: ArtResult?,
    lyricsOpen: Boolean,
    active: Boolean,
    onToggleLyrics: () -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showQueue by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var showPlaylist by remember { mutableStateOf(false) }
    val tint by animateColorAsState(art?.tint ?: Np.PanelHigh, tween(700), label = "tint")

    val view = LocalView.current
    val keepAwake = lyricsOpen && active && vm.settings.lyricsKeepScreen
    DisposableEffect(keepAwake) {
        view.keepScreenOn = keepAwake
        onDispose { view.keepScreenOn = false }
    }

    // Lyrics load once Now Playing is on screen (they are cached after the first fetch).
    LaunchedEffect(song.id, active) { if (active) vm.ensureLyrics() }

    Box(modifier.background(Np.Bg)) {
        // Backdrop: art color fading into the dark base, with a soft blurred cover on Android 12+.
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to tint, 0.62f to Np.Bg, 1f to Np.Bg)))
        if (Build.VERSION.SDK_INT >= 31 && art != null) {
            val image = remember(art.bitmap) { art.bitmap.asImageBitmap() }
            Image(
                image,
                null,
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f)
                    .graphicsLayer { alpha = 0.55f }
                    .blur(70.dp, BlurredEdgeTreatment.Unbounded),
                contentScale = ContentScale.Crop,
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0f to Color.Transparent, 0.3f to Color.Transparent, 0.6f to Np.Bg),
                ),
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp),
        ) {
            // ---- header ----
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton(R.drawable.ph_arrow_left_bold, "Back", onCollapse, bg = GlassButton, tint = Np.Text)
                Text(
                    "Now Playing",
                    color = Np.Text,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                HeartButton(vm.favorites.contains(song.id), { vm.toggleFavorite(song) }, idleTint = Np.Text)
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(
                    targetState = lyricsOpen,
                    transitionSpec = { fadeIn(tween(340)) togetherWith fadeOut(tween(220)) },
                    label = "mode",
                ) { open ->
                    if (open) LyricsMode(vm, song, art, onToggleLyrics) else ArtMode(vm, song, art, onToggleLyrics)
                }
            }

            // ---- seek bar ----
            SeekBar(
                position = { vm.positionMs },
                duration = vm.durationMs,
                onSeek = vm::seekTo,
                modifier = Modifier.fillMaxWidth(),
            )
            val elapsedSec by remember { derivedStateOf { vm.positionMs / 1000 } }
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(elapsedSec * 1000), color = Np.Muted, fontSize = 12.sp)
                Text("-" + formatTime(vm.durationMs - elapsedSec * 1000), color = Np.Muted, fontSize = 12.sp)
            }

            // ---- transport ----
            val shuffleTint by animateColorAsState(if (vm.shuffleOn) Pal.Accent else Np.Text, tween(250), label = "shuffle")
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TapIcon(R.drawable.ph_shuffle_bold, "Shuffle", shuffleTint, vm::toggleShuffle, size = 26.dp, box = 56.dp)
                RoundIconButton(R.drawable.ph_skip_back_fill, "Previous", vm::previous, size = 64.dp, iconSize = 24.dp, bg = GlassButton, tint = Np.Text)
                PlayButton(vm.isPlaying, vm::togglePlay, size = 86.dp)
                RoundIconButton(R.drawable.ph_skip_forward_fill, "Next", vm::next, size = 64.dp, iconSize = 24.dp, bg = GlassButton, tint = Np.Text)
                TapIcon(R.drawable.ph_queue_bold, "Queue", Np.Text, { showQueue = true }, size = 26.dp, box = 56.dp)
            }

            // ---- quality: plain text, e.g. "MP3 • 128 kbps • 48 kHz" ----
            vm.quality?.let { q ->
                Text(
                    (if (q.hiRes) "Hi-Res • " else "") + q.label,
                    color = Np.Muted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }

            // ---- extras: repeat, sleep timer, lyrics, more ----
            val repeatOn = vm.repeatMode != Player.REPEAT_MODE_OFF
            val repeatTint by animateColorAsState(if (repeatOn) Pal.Accent else Np.Muted, tween(250), label = "repeat")
            val sleepTint by animateColorAsState(if (vm.sleepActive) Pal.Accent else Np.Muted, tween(250), label = "sleep")
            val lyricsTint by animateColorAsState(if (lyricsOpen) Pal.Accent else Np.Muted, tween(250), label = "lyricsTint")
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TapIcon(
                    if (vm.repeatMode == Player.REPEAT_MODE_ONE) R.drawable.ph_repeat_once_bold else R.drawable.ph_repeat_bold,
                    "Repeat", repeatTint, vm::cycleRepeat, size = 22.dp, box = 46.dp,
                )
                TapIcon(R.drawable.ph_moon_stars_bold, "Sleep timer", sleepTint, { showSleep = true }, size = 22.dp, box = 46.dp)
                TapIcon(R.drawable.ph_chat_centered_text_bold, "Lyrics", lyricsTint, onToggleLyrics, size = 22.dp, box = 46.dp)
                Box {
                    TapIcon(R.drawable.ph_dots_three_bold, "More", Np.Muted, { showMore = true }, size = 22.dp, box = 46.dp)
                    DropdownMenu(
                        expanded = showMore,
                        onDismissRequest = { showMore = false },
                        modifier = Modifier.background(Np.PanelHigh, RoundedCornerShape(24.dp)),
                    ) {
                        MenuItem("Go to album", R.drawable.ph_disc_bold) {
                            showMore = false
                            vm.goToAlbum(song)
                            onCollapse()
                        }
                        MenuItem("Go to artist", R.drawable.ph_microphone_stage_bold) {
                            showMore = false
                            vm.goToArtist(song)
                            onCollapse()
                        }
                        MenuItem("Add to playlist", R.drawable.ph_playlist_bold) {
                            showMore = false
                            showPlaylist = true
                        }
                        MenuItem("Song info", R.drawable.ph_info_bold) {
                            showMore = false
                            showInfo = true
                        }
                    }
                }
            }
        }
    }

    if (showQueue) QueueSheet(vm) { showQueue = false }
    if (showSleep) SleepSheet(vm) { showSleep = false }
    if (showInfo) SongInfoDialog(vm, song) { showInfo = false }
    if (showPlaylist) AddToPlaylistSheet(vm, listOf(song)) { showPlaylist = false }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtMode(vm: MusicViewModel, song: Song, art: ArtResult?, onOpenLyrics: () -> Unit) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    // The cover "breathes": full size while playing, pulled back when paused.
    val artScale by animateFloatAsState(
        if (vm.isPlaying) 1f else 0.86f,
        spring(dampingRatio = 0.65f, stiffness = 220f),
        label = "artScale",
    )

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 8.dp)
                .pointerInput(song.id) {
                    // Drag the cover left or right to skip; it follows your finger and tilts.
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val x = offsetX.value
                                if (x < -150f) vm.next() else if (x > 150f) vm.previous()
                                offsetX.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 300f))
                            }
                        },
                        onDragCancel = { scope.launch { offsetX.animateTo(0f) } },
                        onHorizontalDrag = { change, delta ->
                            change.consume()
                            scope.launch { offsetX.snapTo(offsetX.value + delta * 0.7f) }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            ArtImage(
                art?.bitmap,
                Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f, matchHeightConstraintsFirst = true)
                    .graphicsLayer {
                        scaleX = artScale
                        scaleY = artScale
                        translationX = offsetX.value
                        rotationZ = offsetX.value / 45f
                    },
                CircleShape,
            )
        }
        AnimatedContent(
            targetState = song,
            transitionSpec = {
                (fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 3 }) togetherWith
                    (fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 3 })
            },
            label = "meta",
        ) { s ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    s.title,
                    color = Np.Text,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().basicMarquee(),
                )
                Text(
                    s.artist,
                    color = Np.Muted,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        LyricsPreview(vm, onOpenLyrics, Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun LyricsMode(vm: MusicViewModel, song: Song, art: ArtResult?, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(30.dp))
                .clickable(onClick = onClose)
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtImage(art?.bitmap, Modifier.size(52.dp), CircleShape)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, color = Np.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = Np.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            PhIcon(R.drawable.ph_caret_down_bold, "Close lyrics", Np.Muted, size = 20.dp)
            Spacer(Modifier.width(10.dp))
        }
        Box(Modifier.weight(1f).fillMaxWidth()) { LyricsPanel(vm) }
        LyricsSyncRow(vm)
    }
}
