package com.asam.musicplayer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlin.math.floor
import kotlin.math.pow

/**
 * Applies the equalizer, bass boost and volume boost to whatever the player outputs.
 * Everything is wrapped in try/catch because some phones refuse some effects.
 */
class AudioEffects(private val prefs: SharedPreferences) {
    private var equalizer: Equalizer? = null
    private var bass: BassBoost? = null
    private var loudness: LoudnessEnhancer? = null
    private var session = -1

    fun attach(sessionId: Int) {
        if (sessionId == C.AUDIO_SESSION_ID_UNSET || sessionId == session) return
        release()
        session = sessionId
        try {
            equalizer = Equalizer(0, sessionId)
        } catch (e: Exception) {
            equalizer = null
        }
        try {
            bass = BassBoost(0, sessionId)
        } catch (e: Exception) {
            bass = null
        }
        try {
            loudness = LoudnessEnhancer(sessionId)
        } catch (e: Exception) {
            loudness = null
        }
        apply()
    }

    /** Reads the saved equalizer + bass settings and pushes them to the audio effects. */
    fun apply() {
        val enabled = prefs.getBoolean(SettingsKeys.EQ_ENABLED, false)
        try {
            val eq = equalizer
            if (eq != null) {
                if (enabled) {
                    val levels = SettingsKeys.parseLevels(prefs.getString(SettingsKeys.EQ_LEVELS, null))
                    val bands = eq.numberOfBands.toInt()
                    val range = eq.bandLevelRange
                    for (b in 0 until bands) {
                        // Our five sliders are spread across however many bands this phone has.
                        val pos = if (bands <= 1) 0f else b * 4f / (bands - 1)
                        val lo = floor(pos).toInt().coerceIn(0, 4)
                        val hi = (lo + 1).coerceAtMost(4)
                        val frac = pos - lo
                        val db = levels[lo] * (1f - frac) + levels[hi] * frac
                        val mb = (db * 100f).toInt().coerceIn(range[0].toInt(), range[1].toInt())
                        eq.setBandLevel(b.toShort(), mb.toShort())
                    }
                    eq.setEnabled(true)
                } else {
                    eq.setEnabled(false)
                }
            }
        } catch (e: Exception) {
            // Ignore: the effect is not available on this device.
        }
        try {
            val bb = bass
            if (bb != null) {
                val strength = prefs.getInt(SettingsKeys.BASS, 0)
                if (bb.strengthSupported) bb.setStrength(strength.toShort())
                bb.setEnabled(strength > 0)
            }
        } catch (e: Exception) {
            // Ignore.
        }
    }

    /** Extra loudness in millibels (used for positive ReplayGain). */
    fun setBoost(millibels: Int) {
        try {
            val l = loudness ?: return
            l.setTargetGain(millibels.coerceIn(0, 1500))
            l.setEnabled(millibels > 0)
        } catch (e: Exception) {
            // Ignore.
        }
    }

    fun release() {
        try {
            equalizer?.release()
        } catch (e: Exception) {
        }
        try {
            bass?.release()
        } catch (e: Exception) {
        }
        try {
            loudness?.release()
        } catch (e: Exception) {
        }
        equalizer = null
        bass = null
        loudness = null
        session = -1
    }
}

/** Owns the player so music keeps playing with the screen off, with notification + lock-screen controls. */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var player: ExoPlayer? = null
    private var effects: AudioEffects? = null
    private lateinit var prefs: SharedPreferences
    private lateinit var audioManager: AudioManager
    private var pausedByUnplug = false

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> applySetting(key) }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            if (!pausedByUnplug || !prefs.getBoolean(SettingsKeys.RESUME_CONNECT, false)) return
            val headset = addedDevices.any {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET
            }
            if (headset) {
                pausedByUnplug = false
                player?.play()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(SettingsKeys.PREFS, Context.MODE_PRIVATE)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val exo = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, prefs.getBoolean(SettingsKeys.FOCUS, true))
            .setHandleAudioBecomingNoisy(prefs.getBoolean(SettingsKeys.NOISY, true))
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        exo.skipSilenceEnabled = prefs.getBoolean(SettingsKeys.SKIP_SILENCE, false)
        player = exo

        val fx = AudioEffects(prefs)
        effects = fx
        fx.attach(exo.audioSessionId)

        exo.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                fx.attach(audioSessionId)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY) {
                    pausedByUnplug = true
                } else if (playWhenReady) {
                    pausedByUnplug = false
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                resetGain()
            }

            override fun onMetadata(metadata: Metadata) {
                applyReplayGain(metadata)
            }
        })

        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, exo).setSessionActivity(openApp).build()

        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        audioManager.registerAudioDeviceCallback(deviceCallback, null)
    }

    /** Reacts to settings changed in the app while music is playing. */
    private fun applySetting(key: String?) {
        val exo = player ?: return
        when (key) {
            SettingsKeys.NOISY -> exo.setHandleAudioBecomingNoisy(prefs.getBoolean(SettingsKeys.NOISY, true))
            SettingsKeys.FOCUS -> exo.setAudioAttributes(audioAttributes, prefs.getBoolean(SettingsKeys.FOCUS, true))
            SettingsKeys.SKIP_SILENCE -> exo.skipSilenceEnabled = prefs.getBoolean(SettingsKeys.SKIP_SILENCE, false)
            SettingsKeys.EQ_ENABLED, SettingsKeys.EQ_LEVELS, SettingsKeys.BASS -> effects?.apply()
            SettingsKeys.NORMALIZE -> resetGain()
            null -> {
                // Settings were reset: re-apply everything.
                exo.setHandleAudioBecomingNoisy(prefs.getBoolean(SettingsKeys.NOISY, true))
                exo.setAudioAttributes(audioAttributes, prefs.getBoolean(SettingsKeys.FOCUS, true))
                exo.skipSilenceEnabled = prefs.getBoolean(SettingsKeys.SKIP_SILENCE, false)
                effects?.apply()
                resetGain()
            }
        }
    }

    private fun resetGain() {
        player?.volume = 1f
        effects?.setBoost(0)
    }

    /** Volume normalization: uses ReplayGain tags when a song has them. */
    private fun applyReplayGain(metadata: Metadata) {
        if (!prefs.getBoolean(SettingsKeys.NORMALIZE, false)) return
        var gainDb: Float? = null
        for (i in 0 until metadata.length()) {
            val entry = metadata.get(i)
            if (entry is TextInformationFrame && entry.description.equals("REPLAYGAIN_TRACK_GAIN", ignoreCase = true)) {
                gainDb = parseGain(entry.values.firstOrNull())
            } else if (entry is VorbisComment && entry.key.equals("REPLAYGAIN_TRACK_GAIN", ignoreCase = true)) {
                gainDb = parseGain(entry.value)
            }
        }
        val g = gainDb ?: return
        if (g < 0f) {
            player?.volume = 10f.pow(g / 20f).coerceIn(0f, 1f)
            effects?.setBoost(0)
        } else {
            player?.volume = 1f
            // Cap at +6 dB so quiet songs get louder without turning into distortion.
            effects?.setBoost((g.coerceAtMost(6f) * 100f).toInt())
        }
    }

    private fun parseGain(text: String?): Float? =
        text?.trim()?.lowercase()?.removeSuffix("db")?.trim()?.toFloatOrNull()

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        try {
            audioManager.unregisterAudioDeviceCallback(deviceCallback)
        } catch (e: Exception) {
        }
        effects?.release()
        session?.run {
            player.release()
            release()
        }
        session = null
        player = null
        super.onDestroy()
    }
}
