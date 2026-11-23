package dev.safro.encore.wear.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dev.safro.encore.wear.Graph
import dev.safro.encore.wear.subsonic.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** What the watch's player screen shows. */
data class PlayerState(
    val song: Song? = null,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val queue: List<Song> = emptyList(),
    val index: Int = -1,
    /** Playing was asked for, but there are no headphones to play on. */
    val needsHeadphones: Boolean = false,
    val error: String? = null,
)

/** The screens' handle on PlaybackService (a Media3 MediaController). */
class PlayerConnection(private val context: Context, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()
    private var controller: MediaController? = null
    private var ticker: Job? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
    }

    suspend fun connect(): MediaController {
        controller?.let { return it }
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val c = MediaController.Builder(context, token).buildAsync().await()
        c.addListener(listener)
        controller = c
        refresh()
        return c
    }

    fun release() {
        ticker?.cancel()
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }

    /** Keeps the position moving on screen while [watching]. */
    fun tick(watching: Boolean) {
        ticker?.cancel()
        if (!watching) return
        ticker = scope.launch {
            while (isActive) {
                refresh()
                delay(500)
            }
        }
    }

    private fun refresh() {
        val c = controller ?: return
        val items = (0 until c.mediaItemCount).map { SongItems.toSong(c.getMediaItemAt(it)) }
        _state.value = PlayerState(
            song = c.currentMediaItem?.let(SongItems::toSong),
            playing = c.isPlaying,
            buffering = c.playbackState == Player.STATE_BUFFERING,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = c.duration.takeIf { it > 0 } ?: ((c.currentMediaItem?.mediaMetadata?.durationMs) ?: 0),
            hasNext = c.hasNextMediaItem(),
            hasPrevious = c.hasPreviousMediaItem() || c.currentPosition > 3000,
            queue = items,
            index = c.currentMediaItemIndex,
            needsHeadphones = c.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_UNSUITABLE_AUDIO_OUTPUT,
            error = c.playerError?.message,
        )
    }

    fun play(songs: List<Song>, start: Int = 0, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        scope.launch {
            val c = connect()
            val g = Graph.get(context)
            val list = if (shuffle) songs.shuffled() else songs
            val items: List<MediaItem> = list.map { s ->
                val art = s.coverArt.takeIf { it.isNotEmpty() }?.let { runCatching { Uri.parse(g.client.coverArtUrl(it, 300).toString()) }.getOrNull() }
                SongItems.toItem(s, art)
            }
            c.setMediaItems(items, if (shuffle) 0 else start.coerceIn(0, items.lastIndex), 0)
            c.prepare()
            c.play()
        }
    }

    private fun with(f: (MediaController) -> Unit) { scope.launch { f(connect()); refresh() } }

    fun toggle() = with { if (it.isPlaying || it.playWhenReady) it.pause() else { if (it.playbackState == Player.STATE_IDLE) it.prepare(); it.play() } }
    fun next() = with { it.seekToNextMediaItem() }
    fun previous() = with { it.seekToPrevious() }
    fun seekTo(ms: Long) = with { it.seekTo(ms) }
    fun skipBy(ms: Long) = with {
        when (val to = skipTarget(it.currentPosition.coerceAtLeast(0), ms, it.duration.coerceAtLeast(0), it.hasNextMediaItem())) {
            null -> it.seekToNextMediaItem()
            else -> it.seekTo(to)
        }
    }
    fun skipTo(index: Int) = with { it.seekTo(index, 0) }
}
