package dev.safro.encore.wear

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.wear.ambient.AmbientLifecycleObserver
import dev.safro.encore.wear.playback.PlayerConnection
import dev.safro.encore.wear.playback.Volume
import dev.safro.encore.wear.ui.Routes
import dev.safro.encore.wear.ui.WearApp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var player: PlayerConnection
    private var ambient by mutableStateOf(false)
    private var open by mutableStateOf<String?>(null)

    private val ambientObserver by lazy {
        AmbientLifecycleObserver(this, object : AmbientLifecycleObserver.AmbientLifecycleCallback {
            override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) { ambient = true }
            override fun onExitAmbient() { ambient = false }
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = Graph.get(this)
        player = PlayerConnection(this, lifecycleScope)
        val volume = Volume(this)
        lifecycle.addObserver(ambientObserver)
        handle(intent)
        setContent { WearApp(graph, player, volume, ambient, open) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** From the tile, complication or media notification. */
    private fun handle(intent: Intent?) {
        val g = Graph.get(this)
        when (intent?.getStringExtra(EXTRA_OPEN)) {
            OPEN_PLAYER -> open = Routes.PLAYER
            OPEN_REMOTE -> open = Routes.REMOTE
            OPEN_TILE_PLAYLIST -> {
                val id = g.settings.current.tilePlaylistId
                if (id.isEmpty()) open = Routes.TILE_PLAYLIST
                else {
                    open = Routes.PLAYER
                    lifecycleScope.launch {
                        val songs = runCatching { g.client.playlist(id).songs }.getOrNull()
                            ?: g.downloads.collection("playlist:$id")?.let { c -> g.downloads.current.songsOf(c).map { it.song } }
                        if (!songs.isNullOrEmpty()) player.play(songs, shuffle = true)
                    }
                }
            }
            OPEN_TOGGLE -> {
                open = Routes.PLAYER
                player.toggle()
            }
        }
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_OPEN = "open"
        const val OPEN_PLAYER = "player"
        const val OPEN_REMOTE = "remote"
        const val OPEN_TILE_PLAYLIST = "tilePlaylist"
        const val OPEN_TOGGLE = "toggle"
    }
}
