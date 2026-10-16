package com.asam.musicplayer.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.asam.musicplayer.ArtResult
import com.asam.musicplayer.ArtworkLoader
import com.asam.musicplayer.MusicViewModel
import com.asam.musicplayer.R
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun Context.activity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

private fun audioPermission() =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

private fun hasAudioPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, audioPermission()) == PackageManager.PERMISSION_GRANTED

private fun requestedPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
    else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

private class NavItem(@DrawableRes val icon: Int, val label: String)

private val navItems = listOf(
    NavItem(R.drawable.ph_house_bold, "Home"),
    NavItem(R.drawable.ph_music_notes_bold, "Music"),
    NavItem(R.drawable.ph_magnifying_glass_bold, "Search"),
    NavItem(R.drawable.ph_playlist_bold, "Playlists"),
    NavItem(R.drawable.ph_gear_six_bold, "Settings"),
)

private data class ScreenKey(val tab: Int, val detail: Detail?)

/** Floating pill navigation bar: the selected tab becomes a lime circle. */
@Composable
private fun NavBar(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(36.dp))
            .background(Pal.Panel.copy(alpha = 0.96f))
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        navItems.forEachIndexed { i, item ->
            val isSelected = i == selected
            val bg by animateColorAsState(if (isSelected) Pal.Accent else Color.Transparent, tween(260), label = "navBg")
            val tint by animateColorAsState(if (isSelected) Pal.OnAccent else Pal.Muted, tween(260), label = "navTint")
            val source = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .size(56.dp)
                    .pressScale(source, 0.9f)
                    .clip(CircleShape)
                    .background(bg)
                    .clickable(interactionSource = source, indication = null) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                PhIcon(item.icon, item.label, tint, size = 24.dp)
            }
        }
    }
}

@Composable
fun PlayerApp(vm: MusicViewModel) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasAudioPermission(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasAudioPermission(context)
        if (granted) vm.loadSongs()
    }
    LaunchedEffect(Unit) {
        if (granted) vm.loadSongs() else launcher.launch(requestedPermissions())
    }
    if (!granted) {
        PermissionGate { launcher.launch(requestedPermissions()) }
        return
    }

    val song = vm.currentSong
    val art by produceState<ArtResult?>(null, song?.id) {
        value = if (song == null) null else ArtworkLoader.loadHero(context, song)
    }

    // Accent that follows the album art when that option is chosen in Settings.
    val artAccent by animateColorAsState(art?.accent ?: Color(0xFFC8E24E), tween(600), label = "artAccent")
    SideEffect {
        if (vm.settings.accentKey == "art") Pal.Accent = artAccent
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var musicChip by rememberSaveable { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<Detail?>(null) }
    var lyricsOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val buckets = remember(vm.songs) { buildBuckets(vm.songs) }

    // "Go to album / artist" from Now Playing.
    LaunchedEffect(vm.pendingTarget, buckets) {
        val target = vm.pendingTarget
        if (target != null) {
            val bucket = (buckets.albums + buckets.artists).firstOrNull { it.key == target }
            if (bucket != null) {
                tab = 1
                detail = Detail.OfBucket(bucket)
                vm.consumeTarget()
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Pal.Bg)) {
        val heightPx = with(LocalDensity.current) { maxHeight.toPx() }
        // 0 = Now Playing hidden, 1 = Now Playing fully open.
        val expand = remember { Animatable(0f) }
        val sheetSpring = remember { spring<Float>(dampingRatio = 1f, stiffness = 380f) }
        var dragTotal by remember { mutableFloatStateOf(0f) }
        val nowPlayingOpen by remember { derivedStateOf { expand.value > 0.5f } }

        val dragState = rememberDraggableState { delta ->
            dragTotal += delta
            if (!lyricsOpen) {
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    expand.snapTo((expand.value - delta / heightPx).coerceIn(0f, 1f))
                }
            }
        }
        fun settle(velocity: Float) {
            scope.launch {
                val e = expand.value
                when {
                    lyricsOpen -> if (dragTotal > 120f || velocity > 900f) lyricsOpen = false
                    e >= 0.97f && (dragTotal < -120f || velocity < -900f) -> lyricsOpen = true
                    velocity > 900f -> expand.animateTo(0f, sheetSpring)
                    velocity < -900f -> expand.animateTo(1f, sheetSpring)
                    e > 0.5f -> expand.animateTo(1f, sheetSpring)
                    else -> expand.animateTo(0f, sheetSpring)
                }
            }
        }

        // Drive the position every frame (120 Hz on supported phones) only while Now Playing is on screen.
        LaunchedEffect(vm.isPlaying) {
            vm.tick()
            while (vm.isPlaying) {
                if (expand.value > 0.05f) withFrameNanos { } else delay(250)
                vm.tick()
            }
        }

        // Status / navigation bar icons: dark on the light theme, but Now Playing is always dark.
        val view = LocalView.current
        SideEffect {
            val window = view.context.activity()?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                val lightBars = Pal.isLight && !nowPlayingOpen
                controller.isAppearanceLightStatusBars = lightBars
                controller.isAppearanceLightNavigationBars = lightBars
            }
        }

        BackHandler(enabled = detail != null || tab != 0) {
            if (detail != null) detail = null else tab = 0
        }
        BackHandler(enabled = song != null && (nowPlayingOpen || lyricsOpen)) {
            if (lyricsOpen) lyricsOpen = false else scope.launch { expand.animateTo(0f, sheetSpring) }
        }

        val bottomPad = navBottom + 12.dp + 72.dp + 16.dp + (if (song != null) 80.dp else 0.dp)

        AnimatedContent(
            targetState = ScreenKey(tab, detail),
            transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(160)) },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Depth: the page recedes while Now Playing slides over it.
                    val open = expand.value.coerceIn(0f, 1f)
                    val scale = 1f - 0.06f * open
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - 0.45f * open
                },
            label = "screen",
        ) { key ->
            val d = key.detail
            when {
                d is Detail.OfBucket -> BucketDetailScreen(vm, d.bucket, { detail = null }, bottomPad)
                d is Detail.OfPlaylist -> PlaylistDetailScreen(vm, d.id, { detail = null }, bottomPad)
                d is Detail.Equalizer -> EqualizerScreen(vm, { detail = null }, bottomPad)
                key.tab == 0 -> HomeScreen(
                    vm = vm,
                    bottomPad = bottomPad,
                    onOpenSettings = { tab = 4 },
                    onSearch = { tab = 2 },
                    onLiked = {
                        musicChip = 1
                        tab = 1
                    },
                    onSeeAllPlaylists = { tab = 3 },
                    onOpenPlaylist = { id ->
                        tab = 3
                        detail = Detail.OfPlaylist(id)
                    },
                )
                key.tab == 1 -> MusicScreen(
                    vm = vm,
                    buckets = buckets,
                    chip = musicChip,
                    onChip = { musicChip = it },
                    onBack = { tab = 0 },
                    onOpenBucket = { detail = Detail.OfBucket(it) },
                    bottomPad = bottomPad,
                )
                key.tab == 2 -> SearchScreen(vm, bottomPad)
                key.tab == 3 -> PlaylistsScreen(vm, bottomPad, onOpenPlaylist = { detail = Detail.OfPlaylist(it) })
                else -> SettingsScreen(vm, bottomPad, onOpenEqualizer = { detail = Detail.Equalizer })
            }
        }

        // Mini player + navigation bar, floating at the bottom.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                .graphicsLayer { alpha = (1f - expand.value).coerceIn(0f, 1f) },
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (song != null) {
                MiniPlayer(
                    song = song,
                    isPlaying = vm.isPlaying,
                    position = { vm.positionMs },
                    duration = vm.durationMs,
                    onToggle = vm::togglePlay,
                    onNext = vm::next,
                    onOpen = { scope.launch { expand.animateTo(1f, sheetSpring) } },
                    modifier = Modifier.draggable(
                        state = dragState,
                        orientation = Orientation.Vertical,
                        onDragStarted = { dragTotal = 0f },
                        onDragStopped = { settle(it) },
                    ),
                )
            }
            NavBar(tab, { tab = it; detail = null })
        }

        if (song != null) {
            NowPlayingScreen(
                vm = vm,
                song = song,
                art = art,
                lyricsOpen = lyricsOpen,
                active = nowPlayingOpen,
                onToggleLyrics = { lyricsOpen = !lyricsOpen },
                onCollapse = {
                    lyricsOpen = false
                    scope.launch { expand.animateTo(0f, sheetSpring) }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Clamp: a spring can overshoot 1.0, and a negative corner size crashes Compose.
                        val open = expand.value.coerceIn(0f, 1f)
                        translationY = (1f - open) * heightPx
                        // Rounded top corners while it slides, square when fully open.
                        val r = (1f - open) * 40.dp.toPx()
                        shape = RoundedCornerShape(topStart = r, topEnd = r, bottomEnd = 0f, bottomStart = 0f)
                        clip = true
                    }
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Vertical,
                        onDragStarted = { dragTotal = 0f },
                        onDragStopped = { settle(it) },
                    ),
            )
        }
    }
}

@Composable
private fun PermissionGate(onAllow: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Pal.Bg).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Allow access to your music", color = Pal.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "The app only reads audio files stored on this phone. Playback works fully offline.",
            color = Pal.Muted,
            fontSize = 14.sp,
            modifier = Modifier.padding(vertical = 14.dp),
        )
        Button(
            onClick = onAllow,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = Pal.Accent, contentColor = Pal.OnAccent),
        ) { Text("Allow access", fontWeight = FontWeight.SemiBold) }
    }
}
