package dev.safro.encore

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import dev.safro.encore.wearlink.ArtFrame
import dev.safro.encore.wearlink.BrowseItem
import dev.safro.encore.wearlink.NowPlaying
import dev.safro.encore.wearlink.Paths
import dev.safro.encore.wearlink.QueueItem
import dev.safro.encore.wearlink.RemoteDispatcher
import dev.safro.encore.wearlink.RemoteTarget
import dev.safro.encore.wearlink.Reply
import dev.safro.encore.wearlink.Wire
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Encore on a paired watch asks here (Wearable Data Layer, see android/wearlink): play, pause, skip, seek,
 * volume, heart, the queue, and the library as Android Auto sees it. It all goes to Encore's own media
 * session, so the phone does exactly what its notification or a car would make it do; nothing in Dart had
 * to change for it.
 */
class WearRemoteService : WearableListenerService() {
    override fun onRequest(nodeId: String, path: String, request: ByteArray): Task<ByteArray>? {
        if (path != Paths.COMMAND) return null
        val answer = TaskCompletionSource<ByteArray>()
        PhoneRemote.get(applicationContext).scope.launch {
            val r = try {
                RemoteDispatcher.handle(nodeId, request, PhoneRemote.get(applicationContext))
            } catch (e: Exception) {
                Wire.encode(Reply.Error(e.message ?: "Encore on the phone couldn't do that"))
            }
            answer.setResult(r)
        }
        return answer.task
    }
}

/**
 * The phone's player as the watch sees it: a MediaBrowser connection to Encore's own AudioService (which
 * starts Encore in the background if it wasn't running). While a watch has subscribed, every change is sent
 * to it, with the cover when the song changes.
 */
class PhoneRemote private constructor(private val context: Context) : RemoteTarget {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val main = Handler(Looper.getMainLooper())
    private var browser: MediaBrowserCompat? = null
    private var controller: MediaControllerCompat? = null
    private val subscribers = mutableMapOf<String, Long>() // watch node -> until (elapsedRealtime)
    private val artSent = mutableMapOf<String, String>() // watch node -> the song whose cover it has
    private var lastSent: NowPlaying? = null
    private var lastSentAt = 0L // elapsedRealtime
    private var sendLater = false

    private val callback = object : MediaControllerCompat.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) = pushState(withArt = true)
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) = pushState(withArt = false)
        override fun onSessionDestroyed() { disconnect() }
    }

    private suspend fun connected(): MediaControllerCompat = withContext(Dispatchers.Main) {
        controller?.takeIf { browser?.isConnected == true }?.let { return@withContext it }
        withTimeout(10_000) {
            suspendCancellableCoroutine { cont ->
                lateinit var b: MediaBrowserCompat
                b = MediaBrowserCompat(
                    context,
                    ComponentName(context, "com.ryanheise.audioservice.AudioService"),
                    object : MediaBrowserCompat.ConnectionCallback() {
                        override fun onConnected() {
                            val c = MediaControllerCompat(context, b.sessionToken)
                            c.registerCallback(callback, main)
                            browser = b
                            controller = c
                            if (cont.isActive) cont.resume(c)
                        }
                        override fun onConnectionFailed() {
                            if (cont.isActive) cont.resumeWithException(IllegalStateException("Encore's player isn't available"))
                        }
                        override fun onConnectionSuspended() = disconnect()
                    },
                    null,
                )
                b.connect()
                cont.invokeOnCancellation { b.disconnect() }
            }
        }
    }

    private fun disconnect() {
        controller?.unregisterCallback(callback)
        controller = null
        browser?.disconnect()
        browser = null
    }

    private val controls get() = controller?.transportControls

    override suspend fun nowPlaying(): NowPlaying = state(connected())

    private fun state(c: MediaControllerCompat): NowPlaying {
        val m = c.metadata
        val p = c.playbackState
        val now = SystemClock.elapsedRealtime()
        val speed = p?.playbackSpeed?.takeIf { it > 0f } ?: 1f
        val playing = p?.state == PlaybackStateCompat.STATE_PLAYING
        // the session's position is on the phone's uptime clock; the watch counts on from wall-clock time
        val pos = p?.let { if (playing && it.lastPositionUpdateTime > 0) it.position + ((now - it.lastPositionUpdateTime) * speed).toLong() else it.position } ?: 0
        val queue = c.queue.orEmpty()
        val info = c.playbackInfo
        return NowPlaying(
            mediaId = m?.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID) ?: "",
            title = m?.getString(MediaMetadataCompat.METADATA_KEY_TITLE) ?: m?.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE) ?: "",
            artist = m?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST) ?: m?.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE) ?: "",
            album = m?.getString(MediaMetadataCompat.METADATA_KEY_ALBUM) ?: "",
            durationMs = m?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)?.coerceAtLeast(0) ?: 0,
            positionMs = pos.coerceAtLeast(0),
            updatedAt = System.currentTimeMillis(),
            speed = speed,
            playing = playing,
            buffering = p?.state == PlaybackStateCompat.STATE_BUFFERING || p?.state == PlaybackStateCompat.STATE_CONNECTING,
            // Encore's heart button says "Unfavourite" while the song is hearted (playback/handler.dart)
            starred = p?.customActions?.firstOrNull { it.action == "toggleStar" }?.name?.toString() == "Unfavourite",
            volume = info?.currentVolume ?: 0,
            maxVolume = info?.maxVolume ?: 15,
            queueIndex = queue.indexOfFirst { it.queueId == p?.activeQueueItemId },
            queueSize = queue.size,
        )
    }

    override suspend fun play() { connected(); controls?.play() }
    override suspend fun pause() { connected(); controls?.pause() }
    override suspend fun next() { connected(); controls?.skipToNext() }
    override suspend fun previous() { connected(); controls?.skipToPrevious() }
    override suspend fun seekTo(positionMs: Long) { connected(); controls?.seekTo(positionMs) }

    override suspend fun changeVolume(steps: Int) {
        val c = connected()
        // the session's volume: the phone's media volume, or the speaker's while casting
        val dir = if (steps > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        repeat(kotlin.math.abs(steps).coerceAtMost(10)) { c.adjustVolume(dir, 0) }
    }

    override suspend fun setVolume(level: Int) { connected().setVolumeTo(level, 0) }

    override suspend fun toggleHeart() { connected(); controls?.sendCustomAction("toggleStar", null) }

    override suspend fun queue(): Reply.Queue {
        val c = connected()
        val items = c.queue.orEmpty().map {
            QueueItem(it.queueId, it.description.title?.toString() ?: "", it.description.subtitle?.toString() ?: "", it.description.mediaId ?: "")
        }
        return Reply.Queue(items, c.playbackState?.activeQueueItemId ?: -1)
    }

    override suspend fun skipTo(queueId: Long) { connected(); controls?.skipToQueueItem(queueId) }

    override suspend fun browse(parentId: String): List<BrowseItem> {
        connected()
        return withContext(Dispatchers.Main) {
            val b = browser ?: return@withContext emptyList()
            val id = parentId.ifEmpty { b.root }
            withTimeout(15_000) {
                suspendCancellableCoroutine { cont ->
                    val sub = object : MediaBrowserCompat.SubscriptionCallback() {
                        override fun onChildrenLoaded(parentId: String, children: MutableList<MediaBrowserCompat.MediaItem>) {
                            b.unsubscribe(id, this)
                            if (cont.isActive) cont.resume(children.map {
                                BrowseItem(it.mediaId ?: "", it.description.title?.toString() ?: "", it.description.subtitle?.toString() ?: "", it.isPlayable)
                            })
                        }
                        override fun onError(parentId: String) {
                            b.unsubscribe(id, this)
                            if (cont.isActive) cont.resume(emptyList())
                        }
                    }
                    b.subscribe(id, sub)
                }
            }
        }
    }

    override suspend fun playItem(mediaId: String) { connected(); controls?.playFromMediaId(mediaId, null) }
    override suspend fun playSearch(query: String) { connected(); controls?.playFromSearch(query, null) }

    override fun subscribe(nodeId: String, seconds: Int, fresh: Boolean) {
        main.post {
            subscribers[nodeId] = SystemClock.elapsedRealtime() + seconds * 1000L
            // the watch has no cover for this song: send it again (otherwise once per song)
            if (fresh) artSent.remove(nodeId)
            lastSent = null // the state goes out now, whatever it is
            scope.launch { runCatching { connected() }; pushState(withArt = true) }
        }
    }

    private fun watchers(): List<String> {
        val now = SystemClock.elapsedRealtime()
        subscribers.entries.removeAll { it.value < now }
        artSent.keys.retainAll(subscribers.keys)
        return subscribers.keys.toList()
    }

    /**
     * Sends the state to the watches (main thread). The session changes for every new buffered position too:
     * only real changes go out (see NowPlaying.differsFrom), at most one a second; the cover once per song
     * per watch.
     */
    private fun pushState(withArt: Boolean) {
        val c = controller ?: return
        val nodes = watchers()
        if (nodes.isEmpty()) return
        val n = state(c)
        val messages = Wearable.getMessageClient(context)
        if (withArt && n.mediaId.isNotEmpty()) {
            val need = nodes.filter { artSent[it] != n.mediaId }
            if (need.isNotEmpty()) {
                for (node in need) artSent[node] = n.mediaId
                val meta = c.metadata
                scope.launch(Dispatchers.IO) {
                    val jpeg = cover(meta)
                    if (jpeg == null) { // not loaded yet: tried again when the song's details change
                        withContext(Dispatchers.Main) { for (node in need) if (artSent[node] == n.mediaId) artSent.remove(node) }
                        return@launch
                    }
                    val frame = ArtFrame(n.mediaId, jpeg).encode()
                    for (node in need) messages.sendMessage(node, Paths.ART, frame)
                }
            }
        }
        if (!n.differsFrom(lastSent)) return
        val wait = lastSentAt + MIN_GAP_MS - SystemClock.elapsedRealtime()
        if (wait > 0) {
            if (!sendLater) {
                sendLater = true
                main.postDelayed({ sendLater = false; pushState(withArt = false) }, wait)
            }
            return
        }
        lastSent = n
        lastSentAt = SystemClock.elapsedRealtime()
        val bytes = Wire.encode(n)
        for (node in nodes) messages.sendMessage(node, Paths.STATE, bytes)
    }

    /** The song's cover, small enough for a watch (Data Layer messages are kept under 100 KB). */
    private fun cover(m: MediaMetadataCompat?): ByteArray? {
        m ?: return null
        val bmp = m.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART)
            ?: m.getBitmap(MediaMetadataCompat.METADATA_KEY_ART)
            ?: m.getBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON)
            ?: listOf(
                MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI,
                MediaMetadataCompat.METADATA_KEY_ART_URI,
                MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI,
            ).firstNotNullOfOrNull { k -> m.getString(k)?.takeIf { it.isNotEmpty() }?.let(::load) }
            ?: return null
        val side = ArtFrame.MAX_SIDE
        val scale = minOf(1f, side.toFloat() / maxOf(bmp.width, bmp.height))
        val small = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
        return ByteArrayOutputStream().use { out ->
            small.compress(Bitmap.CompressFormat.JPEG, 80, out)
            out.toByteArray()
        }
    }

    private fun load(uri: String): Bitmap? = runCatching {
        val u = Uri.parse(uri)
        when (u.scheme) {
            "http", "https" -> (URL(uri).openConnection() as HttpURLConnection).run {
                connectTimeout = 5000
                readTimeout = 5000
                try { inputStream.use { BitmapFactory.decodeStream(it) } } finally { disconnect() }
            }
            "file" -> BitmapFactory.decodeFile(u.path)
            else -> context.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it) }
        }
    }.getOrNull()

    companion object {
        private const val MIN_GAP_MS = 1_000L
        @Volatile private var instance: PhoneRemote? = null
        fun get(context: Context): PhoneRemote =
            instance ?: synchronized(this) { instance ?: PhoneRemote(context.applicationContext).also { instance = it } }
    }
}
