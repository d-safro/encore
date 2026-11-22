package dev.safro.encore.wear.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.safro.encore.wear.Graph
import dev.safro.encore.wear.MainActivity
import dev.safro.encore.wear.R
import dev.safro.encore.wear.data.NowPlayingStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * The watch's own player: Media3 ExoPlayer in a MediaSessionService, so it keeps playing with the screen
 * off, shows in Wear OS's media controls, and appears as an ongoing activity on the watch face.
 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private lateinit var graph: Graph
    private lateinit var resolver: SongResolver
    private val tally = PlayTally { android.os.SystemClock.elapsedRealtime() }
    private var holdingNetwork = false

    override fun onCreate() {
        super.onCreate()
        graph = Graph.get(this)
        resolver = SongResolver(
            downloaded = graph.downloads::fileFor,
            streamUrl = { id, f, b -> graph.client.streamUrl(id, f, b).toString() },
            network = graph.networks::kind,
            settings = { graph.settings.current },
        )
        val http = OkHttpDataSource.Factory(graph.http)
        val sources = ResolvingDataSource.Factory(DefaultDataSource.Factory(this, http)) { spec ->
            val id = SongItems.songId(spec.uri) ?: return@Factory spec
            // runs on the player's loading thread, never the main one
            spec.withUri(Uri.parse(runBlocking { resolver.resolve(id) }))
        }
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(sources))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true, // pause for calls and other apps
            )
            .setHandleAudioBecomingNoisy(true) // headphones gone: pause
            .setWakeMode(C.WAKE_MODE_NETWORK)
            // no headphones: don't play out of the watch; ask for headphones instead (below)
            .setSuppressPlaybackOnUnsuitableOutput(true)
            .build()
        player.addListener(listener(player))

        session = MediaSession.Builder(this, player)
            .setSessionActivity(openPlayer(this))
            .setCallback(object : MediaSession.Callback {
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>,
                ): ListenableFuture<MutableList<MediaItem>> =
                    Futures.immediateFuture(mediaItems.map(SongItems::restore).toMutableList())
            })
            .build()
        setMediaNotificationProvider(OngoingNotificationProvider(this))
    }

    private fun listener(player: ExoPlayer) = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            tally.start(mediaItem?.mediaId, player.duration.takeIf { it > 0 } ?: 0, player.isPlaying)?.let(::scrobble)
            mediaItem?.mediaId?.let { nowPlayingScrobble(it) }
            publish(player)
            holdNetwork(player)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            tally.playing(isPlaying)
            if (!isPlaying && player.playbackState == Player.STATE_ENDED) tally.finish()?.let(::scrobble)
            publish(player)
            holdNetwork(player)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) tally.durationKnown(player.duration)
            holdNetwork(player) // buffering wants it; the queue's end or an error lets it go
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = holdNetwork(player)

        override fun onPlaybackSuppressionReasonChanged(reason: Int) {
            // asked to play with no headphones connected: the output switcher, to pick some
            if (reason == Player.PLAYBACK_SUPPRESSION_REASON_UNSUITABLE_AUDIO_OUTPUT && player.playWhenReady) {
                AudioOutput.openSwitcher(this@PlaybackService)
            }
        }
    }

    /** While streaming, ask for Wi-Fi or LTE (the Bluetooth link to the phone is too slow for music). */
    private fun holdNetwork(player: Player) {
        val id = player.currentMediaItem?.mediaId
        val want = wantsNetwork(player.playWhenReady, player.playbackState, player.playerError != null) &&
            id != null && !resolver.isDownloaded(id)
        if (want == holdingNetwork) return
        holdingNetwork = want
        if (want) graph.networks.acquire() else graph.networks.release()
    }

    private fun publish(player: Player) {
        val m = player.currentMediaItem?.mediaMetadata
        graph.nowPlaying.set(
            m?.let { NowPlayingStore.Info(it.title?.toString() ?: "", it.artist?.toString() ?: "", player.isPlaying, onPhone = false) },
        )
    }

    private fun nowPlayingScrobble(id: String) {
        if (!graph.settings.current.scrobble) return
        graph.scope.launch { runCatching { graph.client.scrobble(id, submission = false) } }
    }

    /** A play that counted: to the server now, or kept until it answers (pendingScrobbles, as on the phone). */
    private fun scrobble(id: String) {
        if (!graph.settings.current.scrobble) return
        val entry = "$id@${System.currentTimeMillis()}"
        graph.scope.launch {
            val waiting = (graph.prefs.getStringSet(PENDING, emptySet()) ?: emptySet()) + entry
            val sent = mutableSetOf<String>()
            for (e in waiting.sorted()) {
                val (songId, at) = e.substringBefore('@') to (e.substringAfter('@').toLongOrNull() ?: continue)
                if (runCatching { graph.client.scrobble(songId, true, at) }.isSuccess) sent += e else break
            }
            graph.prefs.edit { putStringSet(PENDING, (waiting - sent).toList().takeLast(500).toSet()) }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        // kept only while it plays (or is about to): not at the queue's end or after an error
        if (p == null || p.mediaItemCount == 0 || !wantsNetwork(p.playWhenReady, p.playbackState, p.playerError != null)) stopSelf()
    }

    override fun onDestroy() {
        session?.run {
            tally.finish()?.let(::scrobble)
            player.release()
            release()
        }
        session = null
        if (holdingNetwork) graph.networks.release()
        graph.nowPlaying.set(null)
        super.onDestroy()
    }

    companion object {
        private const val PENDING = "pendingScrobbles"

        fun openPlayer(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_PLAYER)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

/** Playing or about to: playWhenReady stays true at the queue's end and after an error, so not just that. */
internal fun wantsNetwork(playWhenReady: Boolean, state: Int, failed: Boolean) =
    playWhenReady && !failed && state != Player.STATE_ENDED && state != Player.STATE_IDLE

/** Media3's notification, made a Wear OS ongoing activity (the Encore icon on the watch face while playing). */
private class OngoingNotificationProvider(private val context: Context) : DefaultMediaNotificationProvider(context) {
    override fun addNotificationActions(
        mediaSession: MediaSession,
        mediaButtons: ImmutableList<CommandButton>,
        builder: NotificationCompat.Builder,
        actionFactory: MediaNotification.ActionFactory,
    ): IntArray {
        val compact = super.addNotificationActions(mediaSession, mediaButtons, builder, actionFactory)
        val title = mediaSession.player.mediaMetadata.title?.toString() ?: "Encore"
        OngoingActivity.Builder(context, DEFAULT_NOTIFICATION_ID, builder)
            .setStaticIcon(R.drawable.ic_stat_encore)
            .setTouchIntent(PlaybackService.openPlayer(context))
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setStatus(Status.Builder().addTemplate(title).build())
            .build()
            .apply(context)
        return compact
    }
}
