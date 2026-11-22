package dev.safro.encore.wear.phone

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import dev.safro.encore.wear.Graph
import dev.safro.encore.wear.data.NowPlayingStore
import dev.safro.encore.wearlink.ArtFrame
import dev.safro.encore.wearlink.Command
import dev.safro.encore.wearlink.NowPlaying
import dev.safro.encore.wearlink.Paths
import dev.safro.encore.wearlink.Reply
import dev.safro.encore.wearlink.Wire
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** The phone, as the remote screens see it. */
data class PhoneState(
    /** null: not looked yet; false: no phone with Encore in reach. */
    val reachable: Boolean? = null,
    val nowPlaying: NowPlaying = NowPlaying(),
    val art: ImageBitmap? = null,
    val artFor: String = "",
    val error: String? = null,
    /** When [nowPlaying] arrived, on the watch's clock. */
    val receivedAt: Long = 0,
)

/**
 * Talks to Encore on the phone over the Wearable Data Layer (see wearlink): requests with
 * MessageClient.sendRequest, and state / art pushed back while the remote screen is open.
 */
class PhoneLink(private val context: Context, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(PhoneState())
    val state: StateFlow<PhoneState> = _state.asStateFlow()
    private var watching: Job? = null

    private suspend fun phoneNode(): String? = runCatching {
        val cap = Wearable.getCapabilityClient(context).getCapability(Paths.CAPABILITY_PHONE, CapabilityClient.FILTER_REACHABLE).await()
        (cap.nodes.firstOrNull { it.isNearby } ?: cap.nodes.firstOrNull())?.id
    }.getOrNull()

    /** Sends [c] to the phone; the answer (or null if the phone isn't there). */
    suspend fun send(c: Command): Reply? {
        val node = phoneNode()
        if (node == null) {
            _state.value = _state.value.copy(reachable = false)
            return null
        }
        val bytes = withTimeoutOrNull(8_000) {
            runCatching { Wearable.getMessageClient(context).sendRequest(node, Paths.COMMAND, Wire.encode(c)).await() }.getOrNull()
        }
        val r = Wire.decodeReply(bytes)
        when (r) {
            is Reply.State -> onState(r.nowPlaying)
            is Reply.Error -> _state.value = _state.value.copy(reachable = true, error = r.message)
            null -> _state.value = _state.value.copy(reachable = false)
            else -> _state.value = _state.value.copy(reachable = true, error = null)
        }
        return r
    }

    fun command(c: Command) { scope.launch { send(c) } }

    /** While the remote screen is open: ask the phone for updates, and renew that every minute. */
    fun watch(on: Boolean) {
        watching?.cancel()
        if (!on) return
        watching = scope.launch {
            while (isActive) {
                send(Command.Subscribe(90))
                delay(60_000)
            }
        }
    }

    fun onState(n: NowPlaying) {
        val s = _state.value
        _state.value = s.copy(
            reachable = true,
            nowPlaying = n,
            error = null,
            receivedAt = System.currentTimeMillis(),
            art = if (n.mediaId == s.artFor) s.art else null,
        )
        Graph.get(context).nowPlaying.set(
            if (n.isEmpty) null else NowPlayingStore.Info(n.title, n.artist, n.playing, onPhone = true),
        )
    }

    fun onArt(bytes: ByteArray) {
        val f = ArtFrame.decode(bytes) ?: return
        val bmp = runCatching { BitmapFactory.decodeByteArray(f.image, 0, f.image.size)?.asImageBitmap() }.getOrNull()
        _state.value = _state.value.copy(art = bmp, artFor = f.mediaId)
    }

    /** The phone's saved sign-in, if it has shared one (for "Use phone's sign-in"). */
    suspend fun phoneAccount() = runCatching {
        val items = Wearable.getDataClient(context)
            .getDataItems(Uri.Builder().scheme("wear").path(Paths.ACCOUNT).build()).await()
        try {
            items.firstOrNull()?.let { Wire.decodeAccount(DataMapItem.fromDataItem(it).dataMap.getString(Paths.ACCOUNT_KEY)) }
        } finally {
            items.release()
        }
    }.getOrNull()
}
