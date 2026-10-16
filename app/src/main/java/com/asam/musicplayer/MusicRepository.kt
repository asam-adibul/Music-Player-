package com.asam.musicplayer

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore

object MusicRepository {

    /** Reads every music file MediaStore knows about (offline, from the device index). */
    fun loadSongs(context: Context): List<Song> {
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.SIZE,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND " +
            "${MediaStore.Audio.Media.DURATION} >= 30000"
        val order = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        val genres = loadGenres(context)

        val songs = ArrayList<Song>()
        context.contentResolver.query(collection, projection, selection, null, order)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val rawArtist = c.getString(artistCol)
                val artist = if (rawArtist.isNullOrBlank() || rawArtist == "<unknown>") UNKNOWN_ARTIST else rawArtist
                songs += Song(
                    id = id,
                    title = c.getString(titleCol)?.takeIf { it.isNotBlank() } ?: "Unknown title",
                    artist = artist,
                    album = c.getString(albumCol)?.takeIf { it.isNotBlank() } ?: "Unknown album",
                    albumId = c.getLong(albumIdCol),
                    durationMs = c.getLong(durCol),
                    uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                    genre = genres[id] ?: UNKNOWN_GENRE,
                    dateAdded = c.getLong(addedCol),
                    sizeBytes = c.getLong(sizeCol),
                )
            }
        }
        return songs
    }

    /** Maps audio id -> genre name using MediaStore's genre tables (works on every Android version). */
    private fun loadGenres(context: Context): Map<Long, String> {
        val map = HashMap<Long, String>()
        try {
            val resolver = context.contentResolver
            resolver.query(
                MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME),
                null, null, null,
            )?.use { g ->
                while (g.moveToNext()) {
                    val genreId = g.getLong(0)
                    val name = g.getString(1)?.trim().orEmpty()
                    // Skip blank names and raw ID3v1 numbers like "(17)" or "17".
                    if (name.isEmpty() || name.trim('(', ')').all { it.isDigit() }) continue
                    val members = MediaStore.Audio.Genres.Members.getContentUri("external", genreId)
                    resolver.query(members, arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID), null, null, null)?.use { m ->
                        while (m.moveToNext()) map[m.getLong(0)] = name
                    }
                }
            }
        } catch (e: Exception) {
            // Some devices restrict genre queries; songs then simply show "Unknown genre".
        }
        return map
    }
}
