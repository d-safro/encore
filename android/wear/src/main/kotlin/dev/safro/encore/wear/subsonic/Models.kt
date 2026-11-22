package dev.safro.encore.wear.subsonic

import kotlinx.serialization.Serializable

@Serializable
data class Song(
    val id: String,
    val title: String,
    val artist: String = "",
    val album: String = "",
    val albumId: String = "",
    val coverArt: String = "",
    val durationSec: Int = 0,
    val track: Int = 0,
    /** File size on the server in bytes (0: not said). */
    val size: Long = 0,
    val suffix: String = "",
    val starred: Boolean = false,
)

@Serializable
data class Album(
    val id: String,
    val name: String,
    val artist: String = "",
    val coverArt: String = "",
    val songCount: Int = 0,
    val year: Int = 0,
    val songs: List<Song> = emptyList(),
)

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val songCount: Int = 0,
    val coverArt: String = "",
    val durationSec: Int = 0,
    val songs: List<Song> = emptyList(),
)

data class Artist(val id: String, val name: String, val coverArt: String = "")

data class SearchResult(
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
) {
    val isEmpty get() = songs.isEmpty() && albums.isEmpty() && artists.isEmpty()
}

data class Starred(val songs: List<Song> = emptyList(), val albums: List<Album> = emptyList())

class SubsonicException(val code: Int, message: String) : Exception(message)
