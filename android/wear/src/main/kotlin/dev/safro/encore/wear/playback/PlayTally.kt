package dev.safro.encore.wear.playback

/**
 * How long the current song has really been heard (pauses and seeks don't count), to tell when a play counts
 * for the server's play history: half the song, or four minutes, whichever comes first (songs under 30 s
 * never count). The usual scrobbling rule, and the phone's.
 */
class PlayTally(private val clock: () -> Long) {
    var songId: String? = null
        private set
    private var durationMs = 0L
    private var heard = 0L
    private var since = -1L
    private var counted = false

    /** A new song started. Returns the previous one's id if its play counted and wasn't sent yet. */
    fun start(id: String?, durationMs: Long, playing: Boolean): String? {
        val prev = finish()
        songId = id
        this.durationMs = durationMs
        heard = 0
        counted = false
        since = if (playing && id != null) clock() else -1
        return prev
    }

    fun playing(on: Boolean) {
        if (on && since < 0 && songId != null) since = clock()
        if (!on && since >= 0) { heard += clock() - since; since = -1 }
    }

    fun durationKnown(ms: Long) { if (ms > 0) durationMs = ms }

    fun heardMs(): Long = heard + if (since >= 0) clock() - since else 0

    fun counts(): Boolean {
        if (durationMs in 1 until 30_000) return false
        val need = if (durationMs > 0) minOf(durationMs / 2, 240_000) else 240_000
        return heardMs() >= need
    }

    /** The song ended or the player stopped: its id if the play counts (once). */
    fun finish(): String? {
        playing(false)
        val id = songId ?: return null
        if (counted || !counts()) return null
        counted = true
        return id
    }
}
