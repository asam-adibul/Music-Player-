package com.asam.musicplayer

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** Loads album art from the device only (embedded tags / album folder). Never touches the network. */
object ArtworkLoader {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val noArt = ConcurrentHashMap.newKeySet<String>()
    private val gate = Semaphore(4)

    // Big images are keyed per song; thumbnails are shared per album.
    private fun key(song: Song, size: Int) =
        if (size > 300) "s${song.id}@$size" else "${song.artKey}@$size"

    fun cached(song: Song, size: Int): Bitmap? = cache.get(key(song, size))

    suspend fun load(context: Context, song: Song, size: Int): Bitmap? {
        val k = key(song, size)
        val hit = cache.get(k)
        if (hit != null) return hit
        val missKey = "${song.id}@$size"
        if (noArt.contains(missKey)) return null
        return withContext(Dispatchers.IO) {
            gate.withPermit {
                cache.get(k) ?: run {
                    val bmp = decode(context, song, size)
                    if (bmp != null) cache.put(k, bmp) else noArt.add(missKey)
                    bmp
                }
            }
        }
    }

    private fun decode(context: Context, song: Song, size: Int): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= 29) {
            context.contentResolver.loadThumbnail(song.uri, Size(size, size), null)
        } else {
            val art = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), song.albumId)
            context.contentResolver.openInputStream(art)?.use { BitmapFactory.decodeStream(it) }
        }
    } catch (e: Exception) {
        null
    }

    suspend fun loadHero(context: Context, song: Song): ArtResult? {
        val bmp = load(context, song, 800) ?: return null
        val colors = withContext(Dispatchers.Default) { ArtPalette.colorsOf(bmp) }
        return ArtResult(bmp, colors.first, colors.second)
    }
}

/** The big album art plus two colors pulled from it: a dark tint for the backdrop and a bright accent. */
class ArtResult(val bitmap: Bitmap, val tint: Color, val accent: Color)

object ArtPalette {
    /** Returns (backdrop tint, accent). Grayscale art keeps a neutral tint and the default accent. */
    fun colorsOf(bitmap: Bitmap): Pair<Color, Color> {
        val p = Palette.from(bitmap).maximumColorCount(16).resizeBitmapArea(160 * 160).generate()
        val swatch = p.vibrantSwatch ?: p.dominantSwatch ?: p.mutedSwatch
            ?: return Pair(Color(0xFF3A3550), Color(0xFFC8E24E))
        val hsl = swatch.hsl
        val neutral = hsl[1] < 0.12f
        val tintSat = if (hsl[1] < 0.08f) hsl[1] else hsl[1].coerceIn(0.25f, 0.6f)
        val tint = Color(ColorUtils.HSLToColor(floatArrayOf(hsl[0], tintSat, 0.36f)))
        val accent = if (neutral) {
            Color(0xFFC8E24E)
        } else {
            Color(ColorUtils.HSLToColor(floatArrayOf(hsl[0], hsl[1].coerceIn(0.55f, 0.9f), 0.66f)))
        }
        return Pair(tint, accent)
    }
}
