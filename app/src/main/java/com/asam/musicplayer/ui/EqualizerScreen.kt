package com.asam.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asam.musicplayer.EqPresets
import com.asam.musicplayer.MusicViewModel
import com.asam.musicplayer.R

private val BandLabels = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")

/** Five-band equalizer with presets, plus a bass boost slider. Changes apply live. */
@Composable
fun EqualizerScreen(vm: MusicViewModel, onBack: () -> Unit, bottomPad: Dp, modifier: Modifier = Modifier) {
    val s = vm.settings
    val sliderColors = SliderDefaults.colors(
        thumbColor = Pal.Accent,
        activeTrackColor = Pal.Accent,
        inactiveTrackColor = Pal.PanelHigh,
    )
    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIconButton(R.drawable.ph_arrow_left_bold, "Back", onBack, size = 52.dp)
            Text(
                "Equalizer",
                color = Pal.Text,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = s.eqEnabled,
                onCheckedChange = s::updateEqEnabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Pal.OnAccent,
                    checkedTrackColor = Pal.Accent,
                    uncheckedThumbColor = Pal.Muted,
                    uncheckedTrackColor = Pal.PanelHigh,
                    uncheckedBorderColor = Color.Transparent,
                ),
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Presets",
                color = Pal.Muted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 6.dp),
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EqPresets.names.forEach { name ->
                    Chip(name, s.eqPreset == name) { s.applyEqPreset(name) }
                }
                if (s.eqPreset == "Custom") Chip("Custom", true) {}
            }

            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Pal.Panel).padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                BandLabels.forEachIndexed { i, label ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, color = Pal.Muted, fontSize = 13.sp, modifier = Modifier.width(64.dp))
                        Slider(
                            value = s.eqLevels[i],
                            onValueChange = { s.updateEqBand(i, it) },
                            valueRange = -12f..12f,
                            colors = sliderColors,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "%+.0f dB".format(s.eqLevels[i]),
                            color = Pal.Text,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(56.dp),
                        )
                    }
                }
            }

            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Pal.Panel).padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Bass boost", color = Pal.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("${s.bassStrength / 10}%", color = Pal.Muted, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                }
                Slider(
                    value = s.bassStrength.toFloat(),
                    onValueChange = { s.updateBass(it.toInt()) },
                    valueRange = 0f..1000f,
                    colors = sliderColors,
                )
            }

            Text(
                "Effects apply to the music that is playing. Some phones support fewer bands or no bass boost, and the app adapts to what your phone offers.",
                color = Pal.Muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            Spacer(Modifier.height(bottomPad))
        }
    }
}
