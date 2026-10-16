package com.asam.musicplayer.ui

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asam.musicplayer.ArtworkLoader
import com.asam.musicplayer.MusicViewModel
import com.asam.musicplayer.R
import com.asam.musicplayer.Song
import kotlinx.coroutines.delay

fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/** Phosphor icon (MIT licensed) as a tintable vector. */
@Composable
fun PhIcon(
    @DrawableRes id: Int,
    contentDescription: String?,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    Icon(painterResource(id), contentDescription, modifier.size(size), tint)
}

/** Springy "squish" when pressed. */
fun Modifier.pressScale(source: MutableInteractionSource, pressedScale: Float = 0.88f): Modifier = composed {
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(
        if (pressed) pressedScale else 1f,
        spring(dampingRatio = 0.5f, stiffness = 520f),
        label = "press",
    )
    graphicsLayer {
        scaleX = s
        scaleY = s
    }
}

/** Bare icon button (no background) with press animation and a light haptic tick. */
@Composable
fun TapIcon(
    @DrawableRes icon: Int,
    description: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    box: Dp = 48.dp,
) {
    val source = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    Box(
        modifier
            .size(box)
            .pressScale(source)
            .clip(CircleShape)
            .clickable(interactionSource = source, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        PhIcon(icon, description, tint, size = size)
    }
}

/** Dark round icon button, like the back / search / heart buttons in the design. */
@Composable
fun RoundIconButton(
    @DrawableRes icon: Int,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    iconSize: Dp = 22.dp,
    bg: Color = Pal.PanelHigh,
    tint: Color = Pal.Text,
) {
    val source = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    Box(
        modifier
            .size(size)
            .pressScale(source, 0.9f)
            .clip(CircleShape)
            .background(bg)
            .clickable(interactionSource = source, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        PhIcon(icon, description, tint, size = iconSize)
    }
}

/** Heart that pops when you like a song. */
@Composable
fun HeartButton(
    liked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bg: Color = Color(0x661E1E2B),
    idleTint: Color = Pal.Text,
) {
    val pop = remember { Animatable(1f) }
    LaunchedEffect(liked) {
        if (liked) {
            pop.snapTo(0.6f)
            pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 420f))
        }
    }
    val tint by animateColorAsState(if (liked) Pal.Accent else idleTint, tween(250), label = "heart")
    RoundIconButton(
        icon = if (liked) R.drawable.ph_heart_fill else R.drawable.ph_heart_bold,
        description = if (liked) "Remove from favorites" else "Add to favorites",
        onClick = onClick,
        modifier = modifier.graphicsLayer {
            scaleX = pop.value
            scaleY = pop.value
        },
        bg = bg,
        tint = tint,
    )
}

/** Big lime play/pause button. */
@Composable
fun PlayButton(playing: Boolean, onClick: () -> Unit, size: Dp = 84.dp) {
    val source = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    Box(
        Modifier
            .size(size)
            .pressScale(source, 0.92f)
            .clip(CircleShape)
            .background(Pal.Accent)
            .clickable(interactionSource = source, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = playing,
            transitionSpec = {
                (fadeIn(tween(180)) + scaleIn(tween(220), initialScale = 0.5f)) togetherWith
                    (fadeOut(tween(110)) + scaleOut(tween(150), targetScale = 0.5f))
            },
            label = "playPause",
        ) { p ->
            PhIcon(
                if (p) R.drawable.ph_pause_fill else R.drawable.ph_play_fill,
                if (p) "Pause" else "Play",
                Pal.OnAccent,
                size = size * 0.4f,
            )
        }
    }
}

/** Small round play button used at the end of list rows. */
@Composable
fun PlayCircle(playing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    RoundIconButton(
        icon = if (playing) R.drawable.ph_pause_fill else R.drawable.ph_play_fill,
        description = if (playing) "Pause" else "Play",
        onClick = onClick,
        modifier = modifier,
        size = size,
        iconSize = 18.dp,
        bg = Pal.PanelHigh,
        tint = Pal.Text,
    )
}

/** Small album-art thumbnail, loaded from the device on demand. */
@Composable
fun ArtThumb(song: Song, size: Dp, corner: Dp = 18.dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bmp by produceState<Bitmap?>(ArtworkLoader.cached(song, 160), song.artKey) {
        value = ArtworkLoader.load(context, song, 160)
    }
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(Pal.PanelHigh),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) {
            val image = remember(b) { b.asImageBitmap() }
            Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            PhIcon(R.drawable.ph_music_notes_fill, null, Pal.Muted, size = size * 0.42f)
        }
    }
}

/** Art for a playlist / album / artist: the first song's cover, or an icon when empty. */
@Composable
fun GroupArt(songs: List<Song>, size: Dp, corner: Dp = 20.dp, @DrawableRes emptyIcon: Int = R.drawable.ph_playlist_bold) {
    if (songs.isNotEmpty()) {
        ArtThumb(songs.first(), size, corner)
    } else {
        Box(
            Modifier.size(size).clip(RoundedCornerShape(corner)).background(Pal.PanelHigh),
            contentAlignment = Alignment.Center,
        ) {
            PhIcon(emptyIcon, null, Pal.Muted, size = size * 0.42f)
        }
    }
}

/** Big album art with a scale + fade transition between songs. */
@Composable
fun ArtImage(bitmap: Bitmap?, modifier: Modifier = Modifier, shape: Shape = CircleShape) {
    Box(
        modifier
            .clip(shape)
            .background(Pal.PanelHigh),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = bitmap,
            transitionSpec = {
                (fadeIn(tween(420)) + scaleIn(tween(420), initialScale = 1.08f)) togetherWith fadeOut(tween(300))
            },
            label = "art",
        ) { b ->
            if (b != null) {
                val image = remember(b) { b.asImageBitmap() }
                Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    PhIcon(R.drawable.ph_music_notes_fill, null, Pal.Muted, size = 56.dp)
                }
            }
        }
    }
}

/**
 * Thin lime seek bar that swells while you drag. [position] is read in the draw phase,
 * so frame-by-frame updates only redraw the bar instead of recomposing the screen.
 */
@Composable
fun SeekBar(
    position: () -> Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val grow by animateFloatAsState(
        if (dragFraction != null) 1f else 0f,
        spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "grow",
    )
    Box(
        modifier
            .height(32.dp)
            .padding(horizontal = 4.dp)
            .pointerInput(duration) {
                detectTapGestures { offset ->
                    onSeek((offset.x / size.width * duration).toLong())
                }
            }
            .pointerInput(duration) {
                detectHorizontalDragGestures(
                    onDragStart = { dragFraction = (it.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = {
                        dragFraction?.let { onSeek((it * duration).toLong()) }
                        dragFraction = null
                    },
                    onDragCancel = { dragFraction = null },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    },
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val h = (3.dp + 3.dp * grow).toPx()
            val cy = size.height / 2
            val f = (dragFraction ?: if (duration > 0) position().toFloat() / duration else 0f).coerceIn(0f, 1f)
            val corner = CornerRadius(h / 2)
            drawRoundRect(Color(0x33FFFFFF), Offset(0f, cy - h / 2), Size(size.width, h), corner)
            drawRoundRect(Pal.Accent, Offset(0f, cy - h / 2), Size(size.width * f, h), corner)
            drawCircle(Pal.Accent, radius = (7.dp + 3.dp * grow).toPx(), center = Offset(size.width * f, cy))
        }
    }
}

/** Soft fade at the top and bottom edge of a scrolling area (used for lyrics). */
fun Modifier.fadingEdges(top: Dp, bottom: Dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val h = size.height
        val t = (top.toPx() / h).coerceIn(0f, 0.45f)
        val b = (bottom.toPx() / h).coerceIn(0f, 0.45f)
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                t to Color.Black,
                1f - b to Color.Black,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

/** Pill-shaped filter chip. Selected = lime. */
@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val bg by animateColorAsState(if (selected) Pal.Accent else Pal.PanelHigh, tween(250), label = "chipBg")
    val fg by animateColorAsState(if (selected) Pal.OnAccent else Pal.Text, tween(250), label = "chipFg")
    Text(
        label,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        color = fg,
        maxLines = 1,
        modifier = Modifier
            .pressScale(source, 0.93f)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
    )
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Pal.Text, fontSize = 24.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(
                action,
                color = Pal.Muted,
                fontSize = 15.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onAction).padding(8.dp),
            )
        }
    }
}

/** The list row from the design: rounded art, title, "By X • N songs", and a trailing control. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaRow(
    title: String,
    subtitle: String,
    highlighted: Boolean,
    art: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(if (highlighted) Pal.Panel else Color.Transparent)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick?.let { cb ->
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        cb()
                    }
                },
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        art()
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (highlighted) Pal.AccentText else Pal.Text,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                color = Pal.Muted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        trailing()
        Spacer(Modifier.width(4.dp))
    }
}

@Composable
fun MenuItem(label: String, @DrawableRes icon: Int, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, color = Pal.Text, fontSize = 15.sp) },
        leadingIcon = { PhIcon(icon, null, Pal.Text, size = 20.dp) },
        onClick = onClick,
    )
}

/** A song in a list, with a long-press menu (play next, queue, playlist, like). */
@Composable
fun SongRow(
    song: Song,
    vm: MusicViewModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    extraLabel: String? = null,
    onExtra: (() -> Unit)? = null,
) {
    var menu by remember { mutableStateOf(false) }
    var addSheet by remember { mutableStateOf(false) }
    val current = vm.currentSong?.id == song.id
    val liked = vm.favorites.contains(song.id)
    Box(modifier) {
        MediaRow(
            title = song.title,
            subtitle = "By ${song.artist} • ${formatTime(song.durationMs)}",
            highlighted = current,
            art = { ArtThumb(song, 68.dp, 20.dp) },
            trailing = {
                PlayCircle(
                    playing = current && vm.isPlaying,
                    onClick = { if (current) vm.togglePlay() else onClick() },
                )
            },
            onClick = onClick,
            onLongClick = { menu = true },
        )
        DropdownMenu(
            expanded = menu,
            onDismissRequest = { menu = false },
            modifier = Modifier.background(Pal.PanelHigh, RoundedCornerShape(24.dp)),
        ) {
            MenuItem("Play next", R.drawable.ph_skip_forward_fill) {
                menu = false
                vm.playNext(song)
            }
            MenuItem("Add to queue", R.drawable.ph_list_plus_bold) {
                menu = false
                vm.addToQueue(song)
            }
            MenuItem("Add to playlist", R.drawable.ph_playlist_bold) {
                menu = false
                addSheet = true
            }
            MenuItem(if (liked) "Remove from favorites" else "Add to favorites", if (liked) R.drawable.ph_heart_fill else R.drawable.ph_heart_bold) {
                menu = false
                vm.toggleFavorite(song)
            }
            if (extraLabel != null && onExtra != null) {
                MenuItem(extraLabel, R.drawable.ph_trash_bold) {
                    menu = false
                    onExtra()
                }
            }
        }
    }
    if (addSheet) AddToPlaylistSheet(vm, listOf(song)) { addSheet = false }
}

/** Dark rounded text field used for search and dialogs. */
@Composable
fun PillTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        placeholder = { Text(placeholder, fontSize = 15.sp, color = Pal.Muted) },
        shape = RoundedCornerShape(30.dp),
        colors = TextFieldDefaults.colors(
            focusedTextColor = Pal.Text,
            unfocusedTextColor = Pal.Text,
            cursorColor = Pal.Accent,
            focusedContainerColor = Pal.PanelHigh,
            unfocusedContainerColor = Pal.PanelHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(32.dp),
        containerColor = Pal.Panel,
        titleContentColor = Pal.Text,
        textContentColor = Pal.Text,
        title = { Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold) },
        text = { PillTextField(text, { text = it }, "Name") },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(text)
                onDismiss()
            }) { Text(confirmLabel, color = Pal.AccentText, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Pal.Muted) } },
    )
}

/** Play / Shuffle / extra action pills shown above a song list. */
@Composable
fun ActionPill(
    label: String,
    @DrawableRes icon: Int,
    filled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    Row(
        modifier
            .pressScale(source, 0.94f)
            .clip(RoundedCornerShape(50))
            .background(if (filled) Pal.Accent else Pal.PanelHigh)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(vertical = 13.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PhIcon(icon, null, if (filled) Pal.OnAccent else Pal.Accent, size = 18.dp)
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (filled) Pal.OnAccent else Pal.Text, maxLines = 1)
    }
}

/** A-Z scrubber on the right edge. Drag or tap a letter to jump. */
@Composable
fun FastScroller(letters: List<Char>, onPick: (Char) -> Unit, modifier: Modifier = Modifier) {
    var height by remember { mutableIntStateOf(0) }
    var active by remember { mutableStateOf<Char?>(null) }
    val pick: (Float) -> Unit = { y ->
        if (height > 0) {
            val i = (y / height * letters.size).toInt().coerceIn(0, letters.lastIndex)
            active = letters[i]
            onPick(letters[i])
        }
    }
    LaunchedEffect(active) {
        if (active != null) {
            delay(800)
            active = null
        }
    }
    Box(modifier.fillMaxSize()) {
        active?.let { letter ->
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Pal.Accent),
                contentAlignment = Alignment.Center,
            ) {
                Text("$letter", color = Pal.OnAccent, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            }
        }
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Pal.Panel.copy(alpha = 0.88f))
                .padding(vertical = 6.dp)
                .onSizeChanged { height = it.height }
                .pointerInput(letters) { detectTapGestures { pick(it.y) } }
                .pointerInput(letters) {
                    detectVerticalDragGestures(
                        onDragStart = { pick(it.y) },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            pick(change.position.y)
                        },
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            letters.forEach { l ->
                Text(
                    "$l",
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    color = if (l == active) Pal.Accent else Pal.Muted,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 5.dp),
                )
            }
        }
    }
}

/** Compact player floating above the navigation bar. */
@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    position: () -> Long,
    duration: Long,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(RoundedCornerShape(34.dp))
            .background(Pal.PanelHigh)
            .clickable(onClick = onOpen),
    ) {
        Row(
            Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtThumb(song, 50.dp, 25.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, color = Pal.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = Pal.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TapIcon(R.drawable.ph_skip_forward_fill, "Next", Pal.Text, onNext, size = 22.dp, box = 44.dp)
            RoundIconButton(
                icon = if (isPlaying) R.drawable.ph_pause_fill else R.drawable.ph_play_fill,
                description = if (isPlaying) "Pause" else "Play",
                onClick = onToggle,
                size = 46.dp,
                iconSize = 20.dp,
                bg = Pal.Accent,
                tint = Pal.OnAccent,
            )
        }
        Canvas(Modifier.align(Alignment.BottomStart).padding(horizontal = 30.dp).fillMaxWidth().height(2.dp)) {
            val f = if (duration > 0) (position().toFloat() / duration).coerceIn(0f, 1f) else 0f
            drawRect(Pal.Accent, size = Size(size.width * f, size.height))
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(32.dp),
        containerColor = Pal.Panel,
        titleContentColor = Pal.Text,
        textContentColor = Pal.Muted,
        title = { Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold) },
        text = { Text(message, fontSize = 14.sp) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text(confirmLabel, color = Pal.AccentText, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Pal.Muted) } },
    )
}
