package com.asam.musicplayer

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

const val UNKNOWN_ARTIST = "Unknown artist"
const val UNKNOWN_GENRE = "Unknown genre"

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val uri: Uri,
    val genre: String = UNKNOWN_GENRE,
    val dateAdded: Long = 0L,
    val sizeBytes: Long = 0L,
) {
    /** Songs of one album share a thumbnail cache entry. */
    val artKey: String get() = if (albumId > 0) "a$albumId" else "s$id"

    /** [mediaId] is unique per queue entry ("songId#n") so the same song can be queued twice. */
    fun toMediaItem(mediaId: String = id.toString()): MediaItem = MediaItem.Builder()
        .setMediaId(mediaId)
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .build()
        )
        .build()
}
