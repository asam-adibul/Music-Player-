package com.asam.musicplayer

/** What the current file sounds like on paper: format, bit depth (lossless) or bitrate (lossy), sample rate. */
class AudioQuality(
    val format: String,
    val lossless: Boolean,
    val bitDepth: Int,
    val bitrateKbps: Int,
    val sampleRateHz: Int,
) {
    val hiRes: Boolean get() = lossless && (bitDepth >= 24 || sampleRateHz > 48_000)

    /** e.g. "FLAC • 16-bit • 44.1 kHz" or "MP3 • 320 kbps • 44.1 kHz". */
    val label: String
        get() {
            val parts = ArrayList<String>()
            parts += format
            if (lossless) {
                if (bitDepth > 0) parts += "$bitDepth-bit"
            } else if (bitrateKbps > 0) {
                parts += "$bitrateKbps kbps"
            }
            if (sampleRateHz > 0) {
                val khz = sampleRateHz / 1000.0
                parts += if (khz % 1.0 == 0.0) "${khz.toInt()} kHz" else "%.1f kHz".format(khz)
            }
            return parts.joinToString(" • ")
        }
}
