package dev.safro.encore.wear.phone

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import dev.safro.encore.wear.Graph
import dev.safro.encore.wearlink.Paths
import dev.safro.encore.wearlink.Wire

/** What the phone sends the watch: its sign-in (DataItem), and now-playing state and covers (messages). */
class WatchListenerService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        val g = Graph.get(this)
        for (e in events) {
            if (e.type != DataEvent.TYPE_CHANGED || e.dataItem.uri.path != Paths.ACCOUNT) continue
            val info = Wire.decodeAccount(DataMapItem.fromDataItem(e.dataItem).dataMap.getString(Paths.ACCOUNT_KEY)) ?: continue
            g.accounts.offerFromPhone(info)
        }
    }

    override fun onMessageReceived(event: MessageEvent) {
        val g = Graph.get(this)
        when (event.path) {
            Paths.STATE -> Wire.decodeNowPlaying(event.data)?.let(g.phone::onState)
            Paths.ART -> g.phone.onArt(event.data)
        }
    }
}
