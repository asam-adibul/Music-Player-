package com.asam.musicplayer

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.asam.musicplayer.ui.MusicTheme
import com.asam.musicplayer.ui.PlayerApp

class MainActivity : ComponentActivity() {
    private val vm: MusicViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.install(this)
        vm.settings // creating the settings applies the saved theme before anything is drawn
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        applyRefreshRate()
        setContent { MusicTheme(vm.settings) { PlayerApp(vm) } }
    }

    override fun onStop() {
        super.onStop()
        vm.persistState()
    }

    override fun onResume() {
        super.onResume()
        applyRefreshRate()
    }

    /**
     * Asks the system for the display's highest refresh rate (120 Hz on supported phones) at the
     * current resolution, or goes back to the system default when the setting is off.
     */
    @Suppress("DEPRECATION")
    fun applyRefreshRate() {
        try {
            val lp = window.attributes
            if (!vm.settings.highRefresh) {
                lp.preferredDisplayModeId = 0
                lp.preferredRefreshRate = 0f
                window.attributes = lp
                return
            }
            val disp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else windowManager.defaultDisplay
            disp ?: return
            val current = disp.mode
            val best = disp.supportedModes
                .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
                .maxByOrNull { it.refreshRate } ?: return
            lp.preferredDisplayModeId = best.modeId
            lp.preferredRefreshRate = best.refreshRate
            window.attributes = lp
        } catch (_: Exception) {
            // Not fatal: the system default refresh rate is used.
        }
    }
}
