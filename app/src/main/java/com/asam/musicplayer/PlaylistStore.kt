package com.asam.musicplayer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Playlist(
    val id: String,
    val name: String,
    val songIds: List<Long>,
    val createdAt: Long,
)

/** Playlists live in one small JSON file on the phone. */
object PlaylistStore {
    private fun file(context: Context) = File(context.filesDir, "playlists.json")

    fun load(context: Context): List<Playlist> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            val out = ArrayList<Playlist>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val ids = o.getJSONArray("songs")
                out += Playlist(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    songIds = List(ids.length()) { ids.getLong(it) },
                    createdAt = o.optLong("created", 0L),
                )
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(context: Context, playlists: List<Playlist>) {
        val arr = JSONArray()
        for (p in playlists) {
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("created", p.createdAt)
                    .put("songs", JSONArray(p.songIds)),
            )
        }
        val tmp = File(context.filesDir, "playlists.json.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(file(context))
    }
}
