package dev.safro.encore.wear.data

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import dev.safro.encore.wear.complication.NowPlayingComplicationService
import dev.safro.encore.wear.subsonic.SubsonicClient
import dev.safro.encore.wear.tile.NowPlayingTileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Hearts given on the watch, ahead of the server's answer (put back if the server says no). */
class Hearts(private val client: SubsonicClient, private val scope: CoroutineScope) {
    private val _hearts = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val hearts: StateFlow<Map<String, Boolean>> = _hearts.asStateFlow()

    fun isStarred(songId: String, fallback: Boolean) = _hearts.value[songId] ?: fallback

    fun toggle(songId: String, wasStarred: Boolean) {
        val on = !isStarred(songId, wasStarred)
        _hearts.value += songId to on
        scope.launch {
            runCatching { client.star(songId, on) }.onFailure { _hearts.value += songId to !on }
        }
    }
}

/**
 * What's playing, for the tile and complication (which run without the player): the watch's own player,
 * or the phone's while the watch's is idle.
 */
class NowPlayingStore(private val context: Context, private val prefs: SharedPreferences) {
    data class Info(val title: String, val artist: String, val playing: Boolean, val onPhone: Boolean)

    private val _info = MutableStateFlow(read())
    val info: StateFlow<Info?> = _info.asStateFlow()

    private fun read(): Info? {
        val t = prefs.getString("npTitle", null) ?: return null
        return Info(t, prefs.getString("npArtist", "") ?: "", prefs.getBoolean("npPlaying", false), prefs.getBoolean("npPhone", false))
    }

    fun set(info: Info?) {
        val cur = _info.value
        // the phone's news doesn't cover up the watch's own player while that plays
        if (info != null && info.onPhone && cur != null && !cur.onPhone && cur.playing) return
        if (info == cur) return
        prefs.edit {
            if (info == null) { remove("npTitle"); remove("npArtist"); remove("npPlaying"); remove("npPhone") }
            else { putString("npTitle", info.title); putString("npArtist", info.artist); putBoolean("npPlaying", info.playing); putBoolean("npPhone", info.onPhone) }
        }
        _info.value = info
        refreshSurfaces()
    }

    /** Ask Wear OS to redraw the tile and complications. */
    fun refreshSurfaces() {
        runCatching { TileService.getUpdater(context).requestUpdate(NowPlayingTileService::class.java) }
        runCatching {
            ComplicationDataSourceUpdateRequester.create(context, ComponentName(context, NowPlayingComplicationService::class.java))
                .requestUpdateAll()
        }
    }
}
