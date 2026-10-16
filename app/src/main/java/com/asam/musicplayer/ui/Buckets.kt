package com.asam.musicplayer.ui

import com.asam.musicplayer.Song

/** A group of songs: an album, an artist or a genre. */
class Bucket(val key: String, val title: String, val subtitle: String, val songs: List<Song>)

class Buckets(val albums: List<Bucket>, val artists: List<Bucket>, val genres: List<Bucket>)

fun buildBuckets(songs: List<Song>): Buckets {
    val albums = songs.groupBy { "${it.albumId}|${it.album}" }.map { entry ->
        val list = entry.value
        val first = list[0]
        Bucket("al${entry.key}", first.album, "${first.artist} • ${list.size} songs", list)
    }.sortedBy { it.title.lowercase() }
    val artists = songs.groupBy { it.artist }.map { entry ->
        Bucket("ar${entry.key}", entry.key, "${entry.value.size} songs", entry.value)
    }.sortedBy { it.title.lowercase() }
    val genres = songs.groupBy { it.genre }.map { entry ->
        Bucket("ge${entry.key}", entry.key, "${entry.value.size} songs", entry.value)
    }.sortedBy { it.title.lowercase() }
    return Buckets(albums, artists, genres)
}

/** A screen shown on top of the main tabs. */
sealed interface Detail {
    class OfBucket(val bucket: Bucket) : Detail
    class OfPlaylist(val id: String) : Detail
    data object Equalizer : Detail
}
