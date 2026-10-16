package com.asam.musicplayer.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asam.musicplayer.Accents
import com.asam.musicplayer.CrashLog
import com.asam.musicplayer.MainActivity
import com.asam.musicplayer.MusicViewModel
import com.asam.musicplayer.R
import com.asam.musicplayer.SettingsState

private fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

private val SpeedValues = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
private val ThemeKeys = listOf("system", "dark", "black", "light")
private val ScaleKeys = listOf("small", "medium", "large")

@Composable
fun SettingsScreen(
    vm: MusicViewModel,
    bottomPad: Dp,
    onOpenEqualizer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = vm.settings
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var editingName by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var crash by remember { mutableStateOf(CrashLog.read(context)) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportBackup(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) restoreUri = uri
    }

    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Settings",
            color = Pal.Text,
            fontSize = 34.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        )

        SectionTitle("Appearance")
        Group {
            ChoiceRow(
                "Theme",
                listOf("Follow system", "Dark", "Pure black", "Light"),
                ThemeKeys.indexOf(s.themeMode).coerceAtLeast(0),
            ) { s.updateThemeMode(ThemeKeys[it]) }
            RowDivider()
            ChoiceRow(
                "Interface size",
                listOf("Small", "Medium", "Large"),
                ScaleKeys.indexOf(s.uiScale).coerceAtLeast(1),
            ) { s.updateUiScale(ScaleKeys[it]) }
            RowDivider()
            AccentPicker(s)
        }

        SectionTitle("Playback")
        Group {
            ToggleRow("Pause when headphones unplug", "Stops the music if wired or Bluetooth headphones disconnect.", s.pauseOnUnplug, s::updatePauseOnUnplug)
            RowDivider()
            ToggleRow("Resume when headphones reconnect", "Continues playing after the unplug pause, once they are back.", s.resumeOnConnect, s::updateResumeOnConnect)
            RowDivider()
            ToggleRow("Pause for other audio", "Pauses or lowers the music for calls and other apps. Turn off to keep playing.", s.audioFocus, s::updateAudioFocus)
            RowDivider()
            ToggleRow("Skip silence", "Skips silent gaps inside tracks.", s.skipSilence, s::updateSkipSilence)
            RowDivider()
            ToggleRow("Normalize volume", "Evens out loudness for songs that have ReplayGain tags.", s.normalize, s::updateNormalize)
            RowDivider()
            ChoiceRow(
                "Playback speed",
                SpeedValues.map { if (it == 1f) "1x" else "${it}x" },
                SpeedValues.indexOfFirst { it == s.speed }.coerceAtLeast(2),
            ) { vm.updateSpeed(SpeedValues[it]) }
            RowDivider()
            ActionRow(
                "Equalizer and bass boost",
                if (s.eqEnabled) "On, ${s.eqPreset}" else "Off",
                onOpenEqualizer,
            )
        }

        SectionTitle("Lyrics")
        Group {
            ToggleRow("Fetch lyrics online", "Uses the internet only for lyrics. Playback stays offline.", vm.onlineLyrics, vm::updateOnlineLyrics)
            RowDivider()
            ChoiceRow("Lyrics text size", listOf("Small", "Medium", "Large"), s.lyricsSize) { s.updateLyricsSize(it) }
            RowDivider()
            ToggleRow("Blur inactive lines", "Softly blurs lines that are not being sung. Needs Android 12 or newer.", s.lyricsBlur, s::updateLyricsBlur)
            RowDivider()
            ToggleRow("Keep screen on in lyrics", "Stops the screen from sleeping while the full lyrics are open.", s.lyricsKeepScreen, s::updateLyricsKeepScreen)
            RowDivider()
            ActionRow("Clear saved lyrics", "Lyrics download again the next time you open a song.", vm::clearLyricsCache)
        }

        SectionTitle("Display")
        Group {
            ToggleRow(
                "High refresh rate",
                "Asks for 120 Hz where your phone supports it. Your phone's own display setting can still override it.",
                s.highRefresh,
            ) {
                s.updateHighRefresh(it)
                (context.findActivity() as? MainActivity)?.applyRefreshRate()
            }
        }

        SectionTitle("Data")
        Group {
            ActionRow("Back up", "Save playlists, favorites and play counts to a file.") {
                exportLauncher.launch("music-player-backup.json")
            }
            RowDivider()
            ActionRow("Restore", "Load a backup file. This replaces your current playlists, favorites and play counts.") {
                importLauncher.launch(arrayOf("*/*"))
            }
            RowDivider()
            ActionRow("Rescan library", "Look for new music on this phone.", vm::loadSongs)
        }

        SectionTitle("General")
        Group {
            ActionRow("Your name", vm.userName.ifEmpty { "Not set" }) { editingName = true }
            RowDivider()
            ActionRow("Reset all settings", "Back to the defaults. Playlists and favorites are kept.") { confirmReset = true }
            val report = crash
            if (report != null) {
                RowDivider()
                ActionRow("Copy crash report", "The app closed unexpectedly last time. Tap to copy the details.") {
                    clipboard.setText(AnnotatedString(report))
                    Toast.makeText(context, "Crash report copied", Toast.LENGTH_SHORT).show()
                }
                RowDivider()
                ActionRow("Clear crash report", "Remove the saved report.") {
                    CrashLog.clear(context)
                    crash = null
                }
            }
            RowDivider()
            ActionRow("About", "Music Player 0.4.1. Icons by Phosphor (MIT). Lyrics from LRCLIB.", null)
        }
        Spacer(Modifier.height(bottomPad))
    }

    if (editingName) {
        NameDialog("Your name", vm.userName, "Save", { editingName = false }) { vm.updateUserName(it) }
    }
    if (confirmReset) {
        ConfirmDialog(
            title = "Reset all settings?",
            message = "Theme, playback, equalizer and lyrics settings go back to their defaults.",
            confirmLabel = "Reset",
            onDismiss = { confirmReset = false },
        ) { vm.resetSettings() }
    }
    val pending = restoreUri
    if (pending != null) {
        ConfirmDialog(
            title = "Restore from backup?",
            message = "This replaces your current playlists, favorites and play counts with the ones in the file.",
            confirmLabel = "Restore",
            onDismiss = { restoreUri = null },
        ) { vm.importBackup(pending) }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = Pal.Muted,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 10.dp, top = 14.dp, bottom = 2.dp),
    )
}

@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Pal.Panel),
        content = content,
    )
}

@Composable
private fun RowDivider() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(1.dp).background(Pal.PanelHigh))
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChecked(!checked) }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Pal.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Pal.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp))
        }
        Spacer(Modifier.size(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Pal.OnAccent,
                checkedTrackColor = Pal.Accent,
                uncheckedThumbColor = Pal.Muted,
                uncheckedTrackColor = Pal.PanelHigh,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, onClick: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Pal.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Pal.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

@Composable
private fun ChoiceRow(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text(title, color = Pal.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEachIndexed { i, label -> Chip(label, i == selected) { onSelect(i) } }
        }
    }
}

@Composable
private fun AccentPicker(s: SettingsState) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text("Accent color", color = Pal.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(
            if (s.accentKey == "art") "Follows the colors of the album art" else "Used for buttons, highlights and the seek bar",
            color = Pal.Muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 3.dp),
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Accents.options.forEach { option ->
                Swatch(Brush.linearGradient(listOf(Color(option.second), Color(option.second))), s.accentKey == option.first) {
                    s.updateAccent(option.first)
                }
            }
            Swatch(
                Brush.sweepGradient(
                    listOf(Color(0xFFF472B6), Color(0xFFFB923C), Color(0xFFFACC15), Color(0xFF4ADE80), Color(0xFF60A5FA), Color(0xFFA78BFA), Color(0xFFF472B6)),
                ),
                s.accentKey == "art",
                icon = R.drawable.ph_music_notes_fill,
            ) { s.updateAccent("art") }
        }
    }
}

@Composable
private fun Swatch(brush: Brush, selected: Boolean, icon: Int = R.drawable.ph_check_bold, onClick: () -> Unit) {
    Box(
        Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(brush)
            .then(if (selected) Modifier.border(3.dp, Pal.Text, CircleShape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected || icon != R.drawable.ph_check_bold) {
            PhIcon(icon, null, Pal.OnAccent, size = 20.dp)
        }
    }
}
