package dev.safro.encore.wear.data

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
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
 * or the phone's while the watch's is idle. The phone's goes stale after a few minutes without news (it
 * only sends while the remote screen is open).
 */
class NowPlayingStore(private val context: Context, private val prefs: SharedPreferences) {
    data class Info(val title: String, val artist: String, val playing: Boolean, val onPhone: Boolean)

    private val _info = MutableStateFlow(read())
    val info: StateFlow<Info?> = _info.asStateFlow()
    private val main = Handler(Looper.getMainLooper())
    private val expire = Runnable { if (_info.value?.onPhone == true && current() == null) write(null) }

    private fun read(): Info? {
        val t = prefs.getString("npTitle", null) ?: return null
        return Info(t, prefs.getString("npArtist", "") ?: "", prefs.getBoolean("npPlaying", false), prefs.getBoolean("npPhone", false))
    }

    /** What the tile and complication show now (the phone's, if it's stale, isn't). */
    fun current(now: Long = System.currentTimeMillis()): Info? = shown(_info.value, prefs.getLong("npAt", 0), now)

    /** [fromPhone]: news from the phone (null there means the phone plays nothing). */
    fun set(info: Info?, fromPhone: Boolean = info?.onPhone == true) {
        val cur = _info.value
        if (!accepts(cur, info, fromPhone)) return
        val wasShown = current() != null
        if (fromPhone) {
            prefs.edit { putLong("npAt", System.currentTimeMillis()) }
            main.removeCallbacks(expire)
            if (info != null) main.postDelayed(expire, PHONE_STALE_MS + 1_000)
        }
        if (info == cur && wasShown) return
        write(info)
    }

    private fun write(info: Info?) {
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

    companion object {
        /** The phone's song is shown this long after its last news. */
        const val PHONE_STALE_MS = 5 * 60_000L

        /** The phone's song never covers up the watch's own while that plays, and the phone playing nothing
         *  never clears the watch's own. */
        fun accepts(cur: Info?, next: Info?, fromPhone: Boolean) =
            !fromPhone || cur == null || cur.onPhone || (next != null && !cur.playing)

        fun shown(info: Info?, phoneAt: Long, now: Long): Info? =
            if (info?.onPhone == true && now - phoneAt > PHONE_STALE_MS) null else info
    }
}
