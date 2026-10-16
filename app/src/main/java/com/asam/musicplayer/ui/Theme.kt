package com.asam.musicplayer.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.asam.musicplayer.Accents
import com.asam.musicplayer.R
import com.asam.musicplayer.SettingsState

/**
 * App colors. These are Compose state, so changing the theme or accent in Settings repaints
 * the whole app immediately. [Accent] is the highlight color (lime by default).
 */
object Pal {
    var Bg by mutableStateOf(Color(0xFF0B0A12)); private set
    var Panel by mutableStateOf(Color(0xFF15151F)); private set
    var PanelHigh by mutableStateOf(Color(0xFF1E1E2B)); private set
    var Text by mutableStateOf(Color(0xFFF4F4F8)); private set
    var Muted by mutableStateOf(Color(0xFFA2A2B5)); private set
    var isLight by mutableStateOf(false); private set

    /** Highlight color. Set directly when following the album art. */
    var Accent by mutableStateOf(Color(0xFFC8E24E))
    val OnAccent = Color(0xFF12130A)

    /** Accent used as text on the page background; darker in the light theme so it stays readable. */
    val AccentText: Color get() = if (isLight) lerp(Accent, Color.Black, 0.45f) else Accent

    val Lavender = Color(0xFFD6A8F5)
    val Peach = Color(0xFFF6B79E)
    val Sky = Color(0xFF9CCBF4)
    val OnPastel = Color(0xFF1E0F35)

    fun applyTheme(mode: String, accentKey: String) {
        when (mode) {
            "light" -> {
                Bg = Color(0xFFF5F4F8)
                Panel = Color(0xFFFFFFFF)
                PanelHigh = Color(0xFFE8E7EF)
                Text = Color(0xFF15141C)
                Muted = Color(0xFF6A6A7C)
                isLight = true
            }
            "black" -> {
                Bg = Color(0xFF000000)
                Panel = Color(0xFF0E0E12)
                PanelHigh = Color(0xFF1A1A20)
                Text = Color(0xFFF4F4F8)
                Muted = Color(0xFFA2A2B5)
                isLight = false
            }
            else -> {
                Bg = Color(0xFF0B0A12)
                Panel = Color(0xFF15151F)
                PanelHigh = Color(0xFF1E1E2B)
                Text = Color(0xFFF4F4F8)
                Muted = Color(0xFFA2A2B5)
                isLight = false
            }
        }
        // "art" keeps whatever accent the album art last produced; the app updates it per song.
        Accents.colorOf(accentKey)?.let { Accent = Color(it) }
    }
}

/** The Now Playing page is always dark, whatever theme the rest of the app uses. */
object Np {
    val Bg = Color(0xFF0B0A12)
    val PanelHigh = Color(0xFF1E1E2B)
    val Text = Color(0xFFF4F4F8)
    val Muted = Color(0xFFA2A2B5)
}

@OptIn(ExperimentalTextApi::class)
val AppFont: FontFamily = FontFamily(
    listOf(400, 500, 600, 700).map { w ->
        Font(
            R.font.plus_jakarta_sans,
            weight = FontWeight(w),
            variationSettings = FontVariation.Settings(FontVariation.weight(w)),
        )
    },
)

@Composable
fun MusicTheme(settings: SettingsState, content: @Composable () -> Unit) {
    // Follow-system theme: tell the settings whenever the phone is in dark or light mode.
    val systemDark = isSystemInDarkTheme()
    SideEffect { settings.updateSystemDark(systemDark) }

    val scheme = if (Pal.isLight) {
        lightColorScheme(
            primary = Pal.Accent,
            onPrimary = Pal.OnAccent,
            background = Pal.Bg,
            onBackground = Pal.Text,
            surface = Pal.Panel,
            onSurface = Pal.Text,
            surfaceVariant = Pal.PanelHigh,
            onSurfaceVariant = Pal.Muted,
            surfaceContainer = Pal.Panel,
            surfaceContainerHigh = Pal.PanelHigh,
        )
    } else {
        darkColorScheme(
            primary = Pal.Accent,
            onPrimary = Pal.OnAccent,
            background = Pal.Bg,
            onBackground = Pal.Text,
            surface = Pal.Panel,
            onSurface = Pal.Text,
            surfaceVariant = Pal.PanelHigh,
            onSurfaceVariant = Pal.Muted,
            surfaceContainer = Pal.Panel,
            surfaceContainerHigh = Pal.PanelHigh,
        )
    }
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(12.dp),
        small = RoundedCornerShape(18.dp),
        medium = RoundedCornerShape(24.dp),
        large = RoundedCornerShape(32.dp),
        extraLarge = RoundedCornerShape(40.dp),
    )
    val base = Typography()
    val typography = Typography(
        displayLarge = base.displayLarge.copy(fontFamily = AppFont),
        displayMedium = base.displayMedium.copy(fontFamily = AppFont),
        displaySmall = base.displaySmall.copy(fontFamily = AppFont),
        headlineLarge = base.headlineLarge.copy(fontFamily = AppFont),
        headlineMedium = base.headlineMedium.copy(fontFamily = AppFont),
        headlineSmall = base.headlineSmall.copy(fontFamily = AppFont),
        titleLarge = base.titleLarge.copy(fontFamily = AppFont),
        titleMedium = base.titleMedium.copy(fontFamily = AppFont),
        titleSmall = base.titleSmall.copy(fontFamily = AppFont),
        bodyLarge = base.bodyLarge.copy(fontFamily = AppFont),
        bodyMedium = base.bodyMedium.copy(fontFamily = AppFont),
        bodySmall = base.bodySmall.copy(fontFamily = AppFont),
        labelLarge = base.labelLarge.copy(fontFamily = AppFont),
        labelMedium = base.labelMedium.copy(fontFamily = AppFont),
        labelSmall = base.labelSmall.copy(fontFamily = AppFont),
    )
    // Interface size: scaling the density shrinks (or grows) every dp and sp value in the app at once.
    val baseDensity = LocalDensity.current
    val scaled = Density(baseDensity.density * settings.uiScaleFactor, baseDensity.fontScale)
    CompositionLocalProvider(LocalDensity provides scaled) {
        MaterialTheme(colorScheme = scheme, shapes = shapes, typography = typography) {
            // Make every Text use Plus Jakarta Sans, even outside Material surfaces.
            ProvideTextStyle(TextStyle(fontFamily = AppFont), content)
        }
    }
}
