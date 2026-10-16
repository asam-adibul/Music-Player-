package com.asam.musicplayer

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.asam.musicplayer.ui.Pal

/** Preference keys shared by the app and the playback service (same "settings" file). */
object SettingsKeys {
    const val PREFS = "settings"
    const val THEME = "theme_mode"
    const val ACCENT = "accent"
    const val NOISY = "pause_on_unplug"
    const val RESUME_CONNECT = "resume_on_connect"
    const val FOCUS = "audio_focus"
    const val SPEED = "speed"
    const val SKIP_SILENCE = "skip_silence"
    const val NORMALIZE = "normalize"
    const val EQ_ENABLED = "eq_enabled"
    const val EQ_LEVELS = "eq_levels"
    const val EQ_PRESET = "eq_preset"
    const val BASS = "bass_strength"
    const val LYRICS_SIZE = "lyrics_size"
    const val LYRICS_BLUR = "lyrics_blur"
    const val LYRICS_AWAKE = "lyrics_keep_screen"
    const val HIGH_REFRESH = "high_refresh"
    const val UI_SCALE = "ui_scale"

    val FLAT = listOf(0f, 0f, 0f, 0f, 0f)

    fun parseLevels(raw: String?): List<Float> {
        val parts = raw?.split(',')?.mapNotNull { it.trim().toFloatOrNull() } ?: return FLAT
        return if (parts.size == 5) parts else FLAT
    }
}

/** Built-in equalizer presets for the five bands (dB): 60 Hz, 230 Hz, 910 Hz, 3.6 kHz, 14 kHz. */
object EqPresets {
    val names = listOf("Flat", "Bass boost", "Treble boost", "Vocal", "Rock", "Pop", "Hip-hop", "Classical", "Electronic")

    fun levels(name: String): List<Float> = when (name) {
        "Bass boost" -> listOf(6f, 4f, 0f, 0f, 0f)
        "Treble boost" -> listOf(0f, 0f, 0f, 4f, 6f)
        "Vocal" -> listOf(-3f, -1f, 4f, 3f, -1f)
        "Rock" -> listOf(5f, 3f, -2f, 3f, 5f)
        "Pop" -> listOf(-1f, 2f, 4f, 2f, -1f)
        "Hip-hop" -> listOf(6f, 4f, 0f, 1f, 3f)
        "Classical" -> listOf(0f, 0f, 0f, -3f, -4f)
        "Electronic" -> listOf(5f, 3f, 0f, 3f, 5f)
        else -> SettingsKeys.FLAT
    }
}

/** Accent colors the user can pick. The key "art" means "follow the album art". */
object Accents {
    val options: List<Pair<String, Long>> = listOf(
        "lime" to 0xFFC8E24EL,
        "green" to 0xFF4ADE80L,
        "teal" to 0xFF2DD4BFL,
        "blue" to 0xFF60A5FAL,
        "purple" to 0xFFA78BFAL,
        "pink" to 0xFFF472B6L,
        "orange" to 0xFFFB923CL,
        "red" to 0xFFF87171L,
        "yellow" to 0xFFFACC15L,
    )

    fun colorOf(key: String): Long? = options.firstOrNull { it.first == key }?.second
}

/** All user settings. Writing one saves it right away, and the playback service reacts to the saved values. */
class SettingsState(context: Context) {
    private val prefs = context.getSharedPreferences(SettingsKeys.PREFS, Context.MODE_PRIVATE)

    /** "system", "dark", "black" or "light". */
    var themeMode by mutableStateOf("dark"); private set
    /** Interface size: "small", "medium" (default) or "large". */
    var uiScale by mutableStateOf("medium"); private set
    var accentKey by mutableStateOf("lime"); private set
    var pauseOnUnplug by mutableStateOf(true); private set
    var resumeOnConnect by mutableStateOf(false); private set
    var audioFocus by mutableStateOf(true); private set
    var speed by mutableFloatStateOf(1f); private set
    var skipSilence by mutableStateOf(false); private set
    var normalize by mutableStateOf(false); private set
    var eqEnabled by mutableStateOf(false); private set
    var eqLevels by mutableStateOf(SettingsKeys.FLAT); private set
    var eqPreset by mutableStateOf("Flat"); private set
    var bassStrength by mutableIntStateOf(0); private set
    var lyricsSize by mutableIntStateOf(1); private set
    var lyricsBlur by mutableStateOf(true); private set
    var lyricsKeepScreen by mutableStateOf(false); private set
    var highRefresh by mutableStateOf(true); private set

    private var systemDark: Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    init {
        load()
    }

    /** Multiplier applied to every size in the app. Large is the original size. */
    val uiScaleFactor: Float get() = when (uiScale) {
        "large" -> 1f
        "small" -> 0.78f
        else -> 0.88f
    }

    /** Called by the theme whenever the phone switches between light and dark mode. */
    fun updateSystemDark(dark: Boolean) {
        if (systemDark != dark) {
            systemDark = dark
            reapplyTheme()
        }
    }

    private fun reapplyTheme() {
        val resolved = if (themeMode == "system") (if (systemDark) "dark" else "light") else themeMode
        Pal.applyTheme(resolved, accentKey)
    }

    val lyricsScale: Float get() = when (lyricsSize) {
        0 -> 0.85f
        2 -> 1.2f
        else -> 1f
    }

    private fun load() {
        themeMode = prefs.getString(SettingsKeys.THEME, "dark") ?: "dark"
        accentKey = prefs.getString(SettingsKeys.ACCENT, "lime") ?: "lime"
        pauseOnUnplug = prefs.getBoolean(SettingsKeys.NOISY, true)
        resumeOnConnect = prefs.getBoolean(SettingsKeys.RESUME_CONNECT, false)
        audioFocus = prefs.getBoolean(SettingsKeys.FOCUS, true)
        speed = prefs.getFloat(SettingsKeys.SPEED, 1f)
        skipSilence = prefs.getBoolean(SettingsKeys.SKIP_SILENCE, false)
        normalize = prefs.getBoolean(SettingsKeys.NORMALIZE, false)
        eqEnabled = prefs.getBoolean(SettingsKeys.EQ_ENABLED, false)
        eqLevels = SettingsKeys.parseLevels(prefs.getString(SettingsKeys.EQ_LEVELS, null))
        eqPreset = prefs.getString(SettingsKeys.EQ_PRESET, "Flat") ?: "Flat"
        bassStrength = prefs.getInt(SettingsKeys.BASS, 0)
        lyricsSize = prefs.getInt(SettingsKeys.LYRICS_SIZE, 1)
        lyricsBlur = prefs.getBoolean(SettingsKeys.LYRICS_BLUR, true)
        lyricsKeepScreen = prefs.getBoolean(SettingsKeys.LYRICS_AWAKE, false)
        highRefresh = prefs.getBoolean(SettingsKeys.HIGH_REFRESH, true)
        uiScale = prefs.getString(SettingsKeys.UI_SCALE, "medium") ?: "medium"
        reapplyTheme()
    }

    // ---- appearance ----

    fun updateThemeMode(value: String) {
        themeMode = value
        prefs.edit().putString(SettingsKeys.THEME, value).apply()
        reapplyTheme()
    }

    fun updateUiScale(value: String) {
        uiScale = value
        prefs.edit().putString(SettingsKeys.UI_SCALE, value).apply()
    }

    fun updateAccent(key: String) {
        accentKey = key
        prefs.edit().putString(SettingsKeys.ACCENT, key).apply()
        reapplyTheme()
    }

    // ---- playback ----

    fun updatePauseOnUnplug(value: Boolean) {
        pauseOnUnplug = value
        prefs.edit().putBoolean(SettingsKeys.NOISY, value).apply()
    }

    fun updateResumeOnConnect(value: Boolean) {
        resumeOnConnect = value
        prefs.edit().putBoolean(SettingsKeys.RESUME_CONNECT, value).apply()
    }

    fun updateAudioFocus(value: Boolean) {
        audioFocus = value
        prefs.edit().putBoolean(SettingsKeys.FOCUS, value).apply()
    }

    fun updateSpeed(value: Float) {
        speed = value
        prefs.edit().putFloat(SettingsKeys.SPEED, value).apply()
    }

    fun updateSkipSilence(value: Boolean) {
        skipSilence = value
        prefs.edit().putBoolean(SettingsKeys.SKIP_SILENCE, value).apply()
    }

    fun updateNormalize(value: Boolean) {
        normalize = value
        prefs.edit().putBoolean(SettingsKeys.NORMALIZE, value).apply()
    }

    // ---- equalizer ----

    fun updateEqEnabled(value: Boolean) {
        eqEnabled = value
        prefs.edit().putBoolean(SettingsKeys.EQ_ENABLED, value).apply()
    }

    fun updateEqBand(index: Int, value: Float) {
        val next = eqLevels.toMutableList()
        next[index] = value
        eqLevels = next
        eqPreset = "Custom"
        eqEnabled = true
        prefs.edit()
            .putString(SettingsKeys.EQ_LEVELS, next.joinToString(","))
            .putString(SettingsKeys.EQ_PRESET, "Custom")
            .putBoolean(SettingsKeys.EQ_ENABLED, true)
            .apply()
    }

    fun applyEqPreset(name: String) {
        val levels = EqPresets.levels(name)
        eqLevels = levels
        eqPreset = name
        eqEnabled = true
        prefs.edit()
            .putString(SettingsKeys.EQ_LEVELS, levels.joinToString(","))
            .putString(SettingsKeys.EQ_PRESET, name)
            .putBoolean(SettingsKeys.EQ_ENABLED, true)
            .apply()
    }

    fun updateBass(value: Int) {
        bassStrength = value
        prefs.edit().putInt(SettingsKeys.BASS, value).apply()
    }

    // ---- lyrics ----

    fun updateLyricsSize(value: Int) {
        lyricsSize = value
        prefs.edit().putInt(SettingsKeys.LYRICS_SIZE, value).apply()
    }

    fun updateLyricsBlur(value: Boolean) {
        lyricsBlur = value
        prefs.edit().putBoolean(SettingsKeys.LYRICS_BLUR, value).apply()
    }

    fun updateLyricsKeepScreen(value: Boolean) {
        lyricsKeepScreen = value
        prefs.edit().putBoolean(SettingsKeys.LYRICS_AWAKE, value).apply()
    }

    // ---- display ----

    fun updateHighRefresh(value: Boolean) {
        highRefresh = value
        prefs.edit().putBoolean(SettingsKeys.HIGH_REFRESH, value).apply()
    }

    /** Back to the defaults. Playlists, favorites and play counts are not touched. */
    fun resetAll() {
        prefs.edit().clear().apply()
        load()
    }
}
