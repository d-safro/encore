package dev.safro.encore.wear.subsonic

import dev.safro.encore.wear.data.Account
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.security.SecureRandom

/** Subsonic token auth: never the password itself, a fresh salt and md5(password + salt) per request. */
object Auth {
    private val rnd = SecureRandom()
    private const val ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz"

    fun newSalt(length: Int = 12): String = buildString { repeat(length) { append(ALPHABET[rnd.nextInt(ALPHABET.length)]) } }

    fun md5Hex(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun token(password: String, salt: String) = md5Hex(password + salt)
}

/** How Subsonic URLs are put together (the same as the phone: JSON, API 1.16.1). */
object SubsonicUrls {
    const val API_VERSION = "1.16.1"
    const val CLIENT = "Encore Watch"

    /** [base] is where to reach the server right now (its home-network or main address, see ServerRouter). */
    fun build(
        base: String,
        method: String,
        account: Account,
        params: List<Pair<String, String>> = emptyList(),
        salt: String = Auth.newSalt(),
    ): HttpUrl {
        val root = normalize(base).toHttpUrlOrNull() ?: throw SubsonicException(0, "That server address isn't valid")
        return root.newBuilder()
            .addPathSegment("rest")
            .addPathSegment(method)
            .addQueryParameter("u", account.user)
            .addQueryParameter("t", Auth.token(account.pass, salt))
            .addQueryParameter("s", salt)
            .addQueryParameter("v", API_VERSION)
            .addQueryParameter("c", CLIENT)
            .addQueryParameter("f", "json")
            .apply { for ((k, v) in params) addQueryParameter(k, v) }
            .build()
    }

    /** As the phone does: https:// when no scheme is given, no slash at the end. */
    fun normalize(address: String): String {
        var s = address.trim()
        if (s.isEmpty()) return s
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "https://$s"
        return s.trimEnd('/')
    }
}

/**
 * The watch's own way to the server, for playing without the phone. [base] picks the address (see
 * ServerRouter); every call is tried once there.
 */
class SubsonicClient(
    private val account: () -> Account?,
    private val base: suspend () -> String,
    private val http: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun need(): Account = account() ?: throw SubsonicException(10, "Not signed in")

    suspend fun url(method: String, vararg params: Pair<String, String>): HttpUrl =
        SubsonicUrls.build(base(), method, need(), params.toList())

    suspend fun call(method: String, vararg params: Pair<String, String>): JsonObject {
        val u = url(method, *params)
        return withContext(Dispatchers.IO) {
            http.newCall(Request.Builder().url(u).build()).execute().use { res ->
                if (res.code == 401 || res.code == 403) throw SubsonicException(40, "Wrong username or password")
                if (!res.isSuccessful) throw SubsonicException(0, "Server answered ${res.code}")
                parseResponse(res.body.string())
            }
        }
    }

    /** The `subsonic-response` object, or the server's error as an exception. */
    fun parseResponse(body: String): JsonObject {
        val r = runCatching { json.parseToJsonElement(body).jsonObject["subsonic-response"]?.jsonObject }.getOrNull()
            ?: throw SubsonicException(0, "That address doesn't answer like a Navidrome / Subsonic server")
        if (r.str("status") != "ok") {
            val e = r.obj("error")
            throw SubsonicException(e?.int("code") ?: 0, e?.str("message")?.ifEmpty { null } ?: "Request failed")
        }
        return r
    }

    suspend fun ping() { call("ping") }

    /** [type]: recent (played), newest (added), frequent, starred, random… */
    suspend fun albums(type: String = "recent", size: Int = 30): List<Album> =
        Parse.albumList(call("getAlbumList2", "type" to type, "size" to "$size"))

    suspend fun album(id: String): Album = Parse.album(call("getAlbum", "id" to id))
    suspend fun playlists(): List<Playlist> = Parse.playlists(call("getPlaylists"))
    suspend fun playlist(id: String): Playlist = Parse.playlist(call("getPlaylist", "id" to id))
    suspend fun starred(): Starred = Parse.starred(call("getStarred2"))

    suspend fun search(query: String): SearchResult =
        Parse.search(call("search3", "query" to query, "songCount" to "20", "albumCount" to "10", "artistCount" to "5"))

    suspend fun star(songId: String, on: Boolean) { call(if (on) "star" else "unstar", "id" to songId) }

    /** [submission] false: "now playing"; true: a play counted. */
    suspend fun scrobble(songId: String, submission: Boolean, timeMs: Long = System.currentTimeMillis()) {
        call("scrobble", "id" to songId, "submission" to "$submission", "time" to "$timeMs")
    }

    /** A stream at [format] ("raw": the original file) and [bitrate] kbps (0: the server's choice). */
    suspend fun streamUrl(songId: String, format: String, bitrate: Int): HttpUrl = url("stream", *streamParams(songId, format, bitrate))

    /** For downloads: the original file comes from `download` (which servers let you resume), else a transcode. */
    suspend fun downloadUrl(songId: String, format: String, bitrate: Int): HttpUrl =
        if (format == "raw") url("download", "id" to songId) else streamUrl(songId, format, bitrate)

    suspend fun coverArtUrl(coverArt: String, size: Int = 300): HttpUrl = url("getCoverArt", "id" to coverArt, "size" to "$size")

    companion object {
        fun streamParams(songId: String, format: String, bitrate: Int): Array<Pair<String, String>> = buildList {
            add("id" to songId)
            if (format == "raw") add("format" to "raw") else {
                add("format" to format)
                if (bitrate > 0) add("maxBitRate" to "$bitrate")
            }
        }.toTypedArray()
    }
}
