package com.asam.musicplayer.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asam.musicplayer.LrcLine
import com.asam.musicplayer.LrcParser
import com.asam.musicplayer.LyricsUi
import com.asam.musicplayer.MusicViewModel
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.min

@Composable
fun LyricsPanel(vm: MusicViewModel, modifier: Modifier = Modifier) {
    when (val l = vm.lyrics) {
        LyricsUi.Idle, LyricsUi.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LoadingDots(Np.Muted)
        }
        is LyricsUi.Missing -> Column(
            modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(l.message, color = Np.Muted, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
            TextButton(onClick = vm::retryLyrics) { Text("Try again", color = Pal.Accent, fontWeight = FontWeight.SemiBold) }
        }
        is LyricsUi.Plain -> Column(
            modifier
                .fillMaxSize()
                .fadingEdges(top = 24.dp, bottom = 40.dp)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 20.dp, horizontal = 6.dp),
        ) {
            Text(l.text, color = Np.Text, fontSize = 20.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold)
        }
        is LyricsUi.Synced -> SyncedLyrics(vm, l.lines, modifier)
    }
}

@Composable
private fun LoadingDots(color: Color) {
    val transition = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        repeat(3) { i ->
            val a by transition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(560, delayMillis = i * 170, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$i",
            )
            Box(
                Modifier
                    .size(10.dp)
                    .graphicsLayer { alpha = a }
                    .background(color, CircleShape),
            )
        }
    }
}

/**
 * Apple Music style: big bold lines, the active line sharp and full-size, the rest dimmed,
 * smaller and progressively blurred. The list glides so the active line sits near the top.
 */
@Composable
private fun SyncedLyrics(vm: MusicViewModel, lines: List<LrcLine>, modifier: Modifier) {
    val listState = rememberLazyListState()
    val current by remember(lines) {
        derivedStateOf { LrcParser.indexAt(lines, vm.positionMs + vm.lyricOffsetMs) }
    }
    var lastUserScroll by remember { mutableLongStateOf(0L) }
    var userActive by remember { mutableStateOf(false) }
    LaunchedEffect(lastUserScroll) {
        if (lastUserScroll != 0L) {
            userActive = true
            delay(3000)
            userActive = false
        }
    }
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) lastUserScroll = System.currentTimeMillis()
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(current, userActive) {
        if (userActive || current < 0) return@LaunchedEffect
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == current }
        if (info != null) {
            listState.animateScrollBy(info.offset.toFloat(), tween(720, easing = FastOutSlowInEasing))
        } else {
            listState.animateScrollToItem(current)
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().fadingEdges(top = 28.dp, bottom = 56.dp)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().nestedScroll(connection),
            contentPadding = PaddingValues(top = maxHeight * 0.16f, bottom = maxHeight * 0.7f),
        ) {
            itemsIndexed(lines, key = { index, _ -> index }) { index, line ->
                LyricLine(
                    text = line.text,
                    active = index == current,
                    distance = abs(index - current),
                    userActive = userActive,
                    scale = vm.settings.lyricsScale,
                    blurOn = vm.settings.lyricsBlur,
                ) {
                    userActive = false
                    lastUserScroll = 0L
                    vm.seekTo(line.timeMs - vm.lyricOffsetMs)
                }
            }
        }
    }
}

@Composable
private fun LyricLine(
    text: String,
    active: Boolean,
    distance: Int,
    userActive: Boolean,
    scale: Float,
    blurOn: Boolean,
    onClick: () -> Unit,
) {
    val a by animateFloatAsState(
        when {
            active -> 1f
            userActive -> 0.75f
            else -> 0.32f
        },
        tween(380),
        label = "alpha",
    )
    val sc by animateFloatAsState(
        if (active) 1f else 0.9f,
        spring(dampingRatio = 0.7f, stiffness = 260f),
        label = "scale",
    )
    val blur by animateDpAsState(
        if (active || userActive || !blurOn) 0.dp else (min(distance, 4) * 1.5f).dp,
        tween(420),
        label = "blur",
    )
    Text(
        text = text.ifBlank { "• • •" },
        color = Np.Text,
        fontSize = (27f * scale).sp,
        lineHeight = (34f * scale).sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .graphicsLayer {
                alpha = a
                scaleX = sc
                scaleY = sc
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
            .blur(blur, BlurredEdgeTreatment.Unbounded)
            .padding(vertical = 9.dp, horizontal = 8.dp),
    )
}

/** Manual sync nudge for songs whose timing is a little off. */
@Composable
fun LyricsSyncRow(vm: MusicViewModel) {
    val l = vm.lyrics
    if (l !is LyricsUi.Synced) return
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SyncPill("−0.1s") { vm.nudgeLyricOffset(-100) }
        Text(
            "  Sync %+.1fs · %s  ".format(vm.lyricOffsetMs / 1000.0, l.source),
            color = Np.Muted,
            fontSize = 11.sp,
        )
        SyncPill("+0.1s") { vm.nudgeLyricOffset(100) }
    }
}

@Composable
private fun SyncPill(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = Np.Text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Np.PanelHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

/** One-line lyrics teaser (the line being sung right now) shown on Now Playing. Tap to open the full view. */
@Composable
fun LyricsPreview(vm: MusicViewModel, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(78.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when (val l = vm.lyrics) {
            is LyricsUi.Synced -> {
                val index by remember(l.lines) {
                    derivedStateOf { LrcParser.indexAt(l.lines, vm.positionMs + vm.lyricOffsetMs) }
                }
                AnimatedContent(
                    targetState = index,
                    transitionSpec = {
                        (fadeIn(tween(320)) + slideInVertically(tween(320)) { it / 3 }) togetherWith
                            (fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it / 3 })
                    },
                    label = "lyricsPreview",
                ) { i ->
                    Text(
                        text = (l.lines.getOrNull(i)?.text ?: "").ifBlank { "• • •" },
                        color = Np.Text,
                        fontSize = 20.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    )
                }
            }
            is LyricsUi.Plain -> Text("Tap to read lyrics", color = Np.Muted, fontSize = 15.sp)
            is LyricsUi.Missing -> Text("No lyrics found", color = Np.Muted, fontSize = 15.sp)
            else -> LoadingDots(Np.Muted)
        }
    }
}
