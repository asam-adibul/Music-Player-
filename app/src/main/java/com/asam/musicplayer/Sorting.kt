package com.asam.musicplayer

enum class SortMode(val key: String, val label: String) {
    TITLE("title", "Title"),
    ARTIST("artist", "Artist"),
    ALBUM("album", "Album"),
    RECENT("recent", "Recently added"),
    PLAYED("played", "Most played");

    companion object {
        fun fromKey(key: String?): SortMode = entries.firstOrNull { it.key == key } ?: TITLE
    }
}

fun sortSongs(list: List<Song>, mode: SortMode, plays: Map<Long, Int>): List<Song> = when (mode) {
    SortMode.TITLE -> list.sortedBy { it.title.lowercase() }
    SortMode.ARTIST -> list.sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))
    SortMode.ALBUM -> list.sortedWith(compareBy({ it.album.lowercase() }, { it.title.lowercase() }))
    SortMode.RECENT -> list.sortedByDescending { it.dateAdded }
    SortMode.PLAYED -> list.sortedWith(compareByDescending<Song> { plays[it.id] ?: 0 }.thenBy { it.title.lowercase() })
}

/** A-Z scrubber only makes sense when the list is sorted alphabetically. */
fun SortMode.isAlphabetic() = this == SortMode.TITLE || this == SortMode.ARTIST || this == SortMode.ALBUM

fun sectionLetter(song: Song, mode: SortMode): Char {
    val text = when (mode) {
        SortMode.ARTIST -> song.artist
        SortMode.ALBUM -> song.album
        else -> song.title
    }
    val c = text.trim().firstOrNull()?.uppercaseChar() ?: '#'
    return if (c in 'A'..'Z') c else '#'
}
