package dev.safro.encore.wear.subsonic

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Servers differ: ids can be numbers or strings, a list of one can come as a bare object, and fields go
// missing. These read what's there and never throw.

internal fun JsonObject.str(key: String): String = when (val v = this[key]) {
    is JsonPrimitive -> if (v is JsonNull) "" else v.content
    else -> ""
}

internal fun JsonObject.int(key: String): Int = str(key).toDoubleOrNull()?.toInt() ?: 0
internal fun JsonObject.long(key: String): Long = str(key).toDoubleOrNull()?.toLong() ?: 0
internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.list(key: String): List<JsonObject> = when (val v = this[key]) {
    is JsonArray -> v.filterIsInstance<JsonObject>()
    is JsonObject -> listOf(v)
    else -> emptyList()
}

object Parse {
    fun song(j: JsonObject) = Song(
        id = j.str("id"),
        title = j.str("title").ifEmpty { j.str("name") },
        artist = j.str("artist").ifEmpty { j.str("displayArtist") },
        album = j.str("album"),
        albumId = j.str("albumId"),
        coverArt = j.str("coverArt"),
        durationSec = j.int("duration"),
        track = j.int("track"),
        size = j.long("size"),
        suffix = j.str("suffix"),
        starred = j.str("starred").isNotEmpty(),
    )

    private fun songs(j: JsonObject?, key: String = "song") =
        j?.list(key).orEmpty().map(::song).filter { it.id.isNotEmpty() }

    fun albumOf(j: JsonObject) = Album(
        id = j.str("id"),
        name = j.str("name").ifEmpty { j.str("title") },
        artist = j.str("artist"),
        coverArt = j.str("coverArt"),
        songCount = j.int("songCount"),
        year = j.int("year"),
        songs = songs(j),
    )

    fun playlistOf(j: JsonObject) = Playlist(
        id = j.str("id"),
        name = j.str("name"),
        songCount = j.int("songCount"),
        coverArt = j.str("coverArt"),
        durationSec = j.int("duration"),
        songs = songs(j, "entry"),
    )

    fun albumList(r: JsonObject): List<Album> =
        (r.obj("albumList2") ?: r.obj("albumList"))?.list("album").orEmpty().map(::albumOf).filter { it.id.isNotEmpty() }

    fun album(r: JsonObject): Album = r.obj("album")?.let(::albumOf) ?: throw SubsonicException(70, "Album not found")

    fun playlists(r: JsonObject): List<Playlist> =
        r.obj("playlists")?.list("playlist").orEmpty().map(::playlistOf).filter { it.id.isNotEmpty() }

    fun playlist(r: JsonObject): Playlist = r.obj("playlist")?.let(::playlistOf) ?: throw SubsonicException(70, "Playlist not found")

    fun starred(r: JsonObject): Starred {
        val s = r.obj("starred2") ?: r.obj("starred")
        return Starred(songs(s), s?.list("album").orEmpty().map(::albumOf))
    }

    fun search(r: JsonObject): SearchResult {
        val s = r.obj("searchResult3") ?: r.obj("searchResult2") ?: return SearchResult()
        return SearchResult(
            songs = songs(s),
            albums = s.list("album").map(::albumOf),
            artists = s.list("artist").map { Artist(it.str("id"), it.str("name"), it.str("coverArt")) },
        )
    }
}
