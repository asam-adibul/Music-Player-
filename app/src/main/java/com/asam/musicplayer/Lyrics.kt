package com.asam.musicplayer

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

data class LrcLine(val timeMs: Long, val text: String)

sealed interface LyricsUi {
    data object Idle : LyricsUi
    data object Loading : LyricsUi
    data class Synced(val lines: List<LrcLine>, val source: String) : LyricsUi
    data class Plain(val text: String, val source: String) : LyricsUi
    data class Missing(val message: String) : LyricsUi
}

object LrcParser {
    private val timeTag = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?\]""")
    private val offsetTag = Regex("""\[offset:\s*([+-]?\d+)\s*\]""", RegexOption.IGNORE_CASE)

    /** Parses standard LRC: multiple timestamps per line, [offset:] tag, ignores [ar:]/[ti:] metadata. */
    fun parse(raw: String): List<LrcLine> {
        val offset = offsetTag.find(raw)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val out = ArrayList<LrcLine>()
        for (line in raw.lineSequence()) {
            val tags = timeTag.findAll(line).toList()
            if (tags.isEmpty()) continue
            val text = line.substring(tags.last().range.last + 1).trim()
            for (m in tags) {
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val frac = m.groupValues[3]
                val ms = when (frac.length) {
                    0 -> 0L
                    1 -> frac.toLong() * 100
                    2 -> frac.toLong() * 10
                    else -> frac.take(3).toLong()
                }
                out += LrcLine(min * 60_000 + sec * 1_000 + ms - offset, text)
            }
        }
        return out.sortedBy { it.timeMs }
    }

    /** Index of the last line whose time is <= positionMs, or -1 before the first line. */
    fun indexAt(lines: List<LrcLine>, positionMs: Long): Int {
        var lo = 0
        var hi = lines.size - 1
        var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].timeMs <= positionMs) {
                ans = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return ans
    }
}

/**
 * Lyrics source order: saved copy on this phone -> LRCLIB (https://lrclib.net) when online lookup is on.
 * Anything fetched is saved locally so a song needs the network at most once.
 */
object LyricsRepository {
    private const val BASE = "https://lrclib.net/api"
    private const val USER_AGENT = "MusicPlayer/0.1 (https://github.com/asam-adibul/Music-Player)"

    private fun dir(context: Context) = File(context.filesDir, "lyrics").apply { mkdirs() }
    private fun prefs(context: Context) = context.getSharedPreferences("lyrics", Context.MODE_PRIVATE)

    fun onlineEnabled(context: Context) = prefs(context).getBoolean("online", true)
    fun setOnlineEnabled(context: Context, value: Boolean) = prefs(context).edit().putBoolean("online", value).apply()
    fun offset(context: Context, songId: Long) = prefs(context).getLong("offset_$songId", 0L)
    fun setOffset(context: Context, songId: Long, ms: Long) = prefs(context).edit().putLong("offset_$songId", ms).apply()
    fun clearCache(context: Context) {
        dir(context).listFiles()?.forEach { it.delete() }
    }

    suspend fun load(context: Context, song: Song, force: Boolean): LyricsUi = withContext(Dispatchers.IO) {
        val lrc = File(dir(context), "${song.id}.lrc")
        val txt = File(dir(context), "${song.id}.txt")
        if (!force) {
            if (lrc.exists()) {
                val lines = LrcParser.parse(lrc.readText())
                if (lines.isNotEmpty()) return@withContext LyricsUi.Synced(lines, "Saved on this phone")
            }
            if (txt.exists()) return@withContext LyricsUi.Plain(txt.readText(), "Saved on this phone")
        }
        if (!onlineEnabled(context)) {
            return@withContext LyricsUi.Missing("Online lyrics are turned off in settings.")
        }
        try {
            val found = fetch(song)
            val syncedRaw = found?.synced
            val plainRaw = found?.plain
            val synced = if (syncedRaw != null) LrcParser.parse(syncedRaw) else emptyList()
            when {
                syncedRaw != null && synced.isNotEmpty() -> {
                    lrc.writeText(syncedRaw)
                    LyricsUi.Synced(synced, "LRCLIB")
                }
                plainRaw != null -> {
                    txt.writeText(plainRaw)
                    LyricsUi.Plain(plainRaw, "LRCLIB")
                }
                else -> LyricsUi.Missing("No lyrics found for this song.")
            }
        } catch (e: IOException) {
            LyricsUi.Missing("Couldn't reach the lyrics service. Check your internet connection.")
        } catch (e: Exception) {
            LyricsUi.Missing("Something went wrong while fetching lyrics.")
        }
    }

    private class Found(val synced: String?, val plain: String?)

    private fun fetch(song: Song): Found? {
        val artist = if (song.artist == UNKNOWN_ARTIST) "" else song.artist
        val seconds = song.durationMs / 1000.0

        // 1) Exact match.
        val exact = buildString {
            append("track_name=").append(enc(song.title))
            if (artist.isNotEmpty()) append("&artist_name=").append(enc(artist))
            if (song.album.isNotBlank()) append("&album_name=").append(enc(song.album))
            append("&duration=").append(Math.round(seconds))
        }
        var plainFallback: String? = null
        val getResp = httpGet("$BASE/get?$exact")
        if (getResp != null && getResp.first == 200) {
            val o = JSONObject(getResp.second)
            val s = o.str("syncedLyrics")
            if (s != null) return Found(s, o.str("plainLyrics"))
            plainFallback = o.str("plainLyrics")
        }

        // 2) Search, then pick the synced result whose duration is closest.
        val query = "track_name=" + enc(song.title) + (if (artist.isNotEmpty()) "&artist_name=" + enc(artist) else "")
        val searchResp = httpGet("$BASE/search?$query")
        if (searchResp != null && searchResp.first == 200) {
            val arr = JSONArray(searchResp.second)
            var bestSynced: String? = null
            var bestDiff = Double.MAX_VALUE
            var bestPlain: String? = null
            var bestPlainDiff = Double.MAX_VALUE
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val diff = abs(o.optDouble("duration", -1000.0) - seconds)
                if (diff > 6.0) continue
                val s = o.str("syncedLyrics")
                if (s != null && diff < bestDiff) {
                    bestSynced = s
                    bestDiff = diff
                }
                val p = o.str("plainLyrics")
                if (p != null && diff < bestPlainDiff) {
                    bestPlain = p
                    bestPlainDiff = diff
                }
            }
            if (bestSynced != null) return Found(bestSynced, null)
            if (plainFallback == null) plainFallback = bestPlain
        }
        return if (plainFallback != null) Found(null, plainFallback) else null
    }

    private fun JSONObject.str(key: String): String? =
        if (isNull(key)) null else getString(key).takeIf { it.isNotBlank() }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun httpGet(url: String): Pair<Int, String>? {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            code to body
        } finally {
            conn.disconnect()
        }
    }
}
