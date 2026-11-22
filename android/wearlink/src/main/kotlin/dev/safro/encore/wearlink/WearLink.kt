package dev.safro.encore.wearlink

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What Encore's phone and watch apps say to each other over the Wearable Data Layer.
 *
 * - The watch asks with [Paths.COMMAND] (MessageClient.sendRequest): a [Command] in, a [Reply] back.
 * - While a watch has asked for updates ([Command.Subscribe]), the phone sends [Paths.STATE] (a [NowPlaying])
 *   whenever something changes, and [Paths.ART] (see [ArtFrame]) when the song does.
 * - The phone keeps its sign-in at [Paths.ACCOUNT] (a DataItem holding an [AccountInfo]) so a watch paired
 *   later can sign in to the same server without typing.
 *
 * Every payload is UTF-8 JSON except the art. Unknown fields are ignored, so either side can add fields
 * without breaking an older one; an unknown command gets [Reply.Error].
 */
object Paths {
    const val CAPABILITY_PHONE = "encore_phone"
    const val CAPABILITY_WATCH = "encore_watch"
    const val PREFIX = "/encore"
    const val COMMAND = "/encore/remote/command"
    const val STATE = "/encore/remote/state"
    const val ART = "/encore/remote/art"
    const val ACCOUNT = "/encore/account"
    /** The key in the [ACCOUNT] DataMap that holds the JSON. */
    const val ACCOUNT_KEY = "json"
}

const val PROTOCOL_VERSION = 1

@Serializable
sealed interface Command {
    /** Send me [Paths.STATE] / [Paths.ART] for the next [seconds] (the watch repeats it while the screen is on). */
    @Serializable @SerialName("subscribe") data class Subscribe(val seconds: Int = 90) : Command
    @Serializable @SerialName("state") data object State : Command
    @Serializable @SerialName("play") data object Play : Command
    @Serializable @SerialName("pause") data object Pause : Command
    @Serializable @SerialName("toggle") data object TogglePlay : Command
    @Serializable @SerialName("next") data object Next : Command
    @Serializable @SerialName("previous") data object Previous : Command
    @Serializable @SerialName("seek") data class SeekTo(val positionMs: Long) : Command
    /** Phone media volume up or down by [steps] (negative: down). */
    @Serializable @SerialName("volume") data class ChangeVolume(val steps: Int) : Command
    @Serializable @SerialName("setVolume") data class SetVolume(val level: Int) : Command
    /** Heart (or un-heart) the song playing now. */
    @Serializable @SerialName("heart") data object ToggleHeart : Command
    @Serializable @SerialName("queue") data object Queue : Command
    @Serializable @SerialName("skipTo") data class SkipTo(val queueId: Long) : Command
    /** The phone's library, as Android Auto sees it: "" is the top (Home, Playlists, Albums, Radio). */
    @Serializable @SerialName("browse") data class Browse(val parentId: String = "") : Command
    @Serializable @SerialName("playItem") data class PlayItem(val mediaId: String) : Command
    @Serializable @SerialName("search") data class PlaySearch(val query: String) : Command
}

@Serializable
data class NowPlaying(
    val mediaId: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0,
    val positionMs: Long = 0,
    /** Phone wall-clock time (ms) when [positionMs] was true. */
    val updatedAt: Long = 0,
    val speed: Float = 1f,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val starred: Boolean = false,
    val volume: Int = 0,
    val maxVolume: Int = 15,
    val queueIndex: Int = -1,
    val queueSize: Int = 0,
) {
    val isEmpty get() = mediaId.isEmpty() && title.isEmpty()

    /** Where the song is at [now] (same clock as [updatedAt]), counting on while it plays. */
    fun positionAt(now: Long): Long {
        if (!playing || buffering || updatedAt <= 0) return positionMs.coerceAtLeast(0)
        val p = positionMs + ((now - updatedAt).coerceAtLeast(0) * speed).toLong()
        return if (durationMs > 0) p.coerceIn(0, durationMs) else p.coerceAtLeast(0)
    }
}

@Serializable
data class QueueItem(val queueId: Long, val title: String, val artist: String = "", val mediaId: String = "")

@Serializable
data class BrowseItem(
    val id: String,
    val title: String,
    val subtitle: String = "",
    /** true: tapping it plays; false: it's a folder to open. */
    val playable: Boolean = false,
)

@Serializable
sealed interface Reply {
    @Serializable @SerialName("state") data class State(val nowPlaying: NowPlaying) : Reply
    @Serializable @SerialName("queue") data class Queue(val items: List<QueueItem>, val currentQueueId: Long = -1) : Reply
    @Serializable @SerialName("browse") data class Browse(val parentId: String, val items: List<BrowseItem>) : Reply
    @Serializable @SerialName("error") data class Error(val message: String) : Reply
}

/** The phone's signed-in server. Setting names match the phone's (server, user, pass, localAddress). */
@Serializable
data class AccountInfo(
    val server: String,
    val user: String,
    val pass: String,
    /** The server's home-network address ("" if none). */
    val localAddress: String = "",
    val version: Int = PROTOCOL_VERSION,
) {
    /** Never log the password. */
    override fun toString() = "AccountInfo(server=$server, user=$user, localAddress=$localAddress)"
}

object Wire {
    val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
        encodeDefaults = true
        explicitNulls = false
    }

    fun encode(c: Command): ByteArray = json.encodeToString(Command.serializer(), c).encodeToByteArray()
    fun decodeCommand(b: ByteArray): Command? = runCatching { json.decodeFromString(Command.serializer(), b.decodeToString()) }.getOrNull()

    fun encode(r: Reply): ByteArray = json.encodeToString(Reply.serializer(), r).encodeToByteArray()
    fun decodeReply(b: ByteArray?): Reply? =
        b?.let { runCatching { json.decodeFromString(Reply.serializer(), it.decodeToString()) }.getOrNull() }

    fun encode(n: NowPlaying): ByteArray = json.encodeToString(NowPlaying.serializer(), n).encodeToByteArray()
    fun decodeNowPlaying(b: ByteArray): NowPlaying? =
        runCatching { json.decodeFromString(NowPlaying.serializer(), b.decodeToString()) }.getOrNull()

    fun encode(a: AccountInfo): String = json.encodeToString(AccountInfo.serializer(), a)
    fun decodeAccount(s: String?): AccountInfo? =
        s?.let { runCatching { json.decodeFromString(AccountInfo.serializer(), it) }.getOrNull() }
            ?.takeIf { it.server.isNotBlank() && it.user.isNotBlank() }
}

/**
 * A cover sent from phone to watch: [mediaId] tells the watch which song it belongs to, so a late cover for
 * the previous song is never shown under the next one's title.
 * Layout: 2 bytes (big-endian) id length, the id in UTF-8, then the JPEG.
 */
class ArtFrame(val mediaId: String, val image: ByteArray) {
    fun encode(): ByteArray {
        val id = mediaId.encodeToByteArray()
        require(id.size <= 0xFFFF) { "id too long" }
        val out = ByteArray(2 + id.size + image.size)
        out[0] = (id.size ushr 8).toByte()
        out[1] = id.size.toByte()
        id.copyInto(out, 2)
        image.copyInto(out, 2 + id.size)
        return out
    }

    companion object {
        /** Biggest cover worth sending: watch screens are under 500 px across. */
        const val MAX_SIDE = 320

        fun decode(b: ByteArray): ArtFrame? {
            if (b.size < 2) return null
            val n = ((b[0].toInt() and 0xFF) shl 8) or (b[1].toInt() and 0xFF)
            if (b.size < 2 + n) return null
            return ArtFrame(b.copyOfRange(2, 2 + n).decodeToString(), b.copyOfRange(2 + n, b.size))
        }
    }
}
