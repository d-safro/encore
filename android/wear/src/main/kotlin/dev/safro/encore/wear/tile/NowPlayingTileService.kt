package dev.safro.encore.wear.tile

import android.content.ComponentName
import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.layout.column
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.safro.encore.wear.Graph
import dev.safro.encore.wear.MainActivity
import dev.safro.encore.wear.data.NowPlayingStore

/**
 * The Encore tile: what's playing (on the watch, or on the phone) with play / pause; with nothing playing,
 * one tap plays your chosen playlist shuffled (Settings → Tile playlist).
 */
class NowPlayingTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val g = Graph.get(this)
        val layout = TileLayout.layout(this, requestParams.deviceConfiguration, g.nowPlaying.info.value, g.settings.current.tilePlaylistName)
        return Futures.immediateFuture(
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES)
                .setFreshnessIntervalMillis(0) // redrawn when the song changes (NowPlayingStore asks)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
                .build(),
        )
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(ResourceBuilders.Resources.Builder().setVersion(RESOURCES).build())

    companion object {
        private const val RESOURCES = "1"
    }
}

/** The tile's layout, apart from the service so it can be checked in tests. */
object TileLayout {
    fun open(context: Context, what: String) = clickable(
        ActionBuilders.launchAction(
            ComponentName(context, MainActivity::class.java),
            mapOf(MainActivity.EXTRA_OPEN to ActionBuilders.stringExtra(what)),
        ),
        what,
    )

    fun layout(context: Context, device: DeviceParameters, info: NowPlayingStore.Info?, playlist: String): LayoutElementBuilders.LayoutElement =
        materialScope(context, device) {
            if (info != null) {
                val target = if (info.onPhone) MainActivity.OPEN_REMOTE else MainActivity.OPEN_TOGGLE
                primaryLayout(
                    titleSlot = { text((if (info.onPhone) "Encore · phone" else "Encore").layoutString) },
                    mainSlot = {
                        column(
                            text(info.title.layoutString, typography = Typography.TITLE_MEDIUM, maxLines = 2),
                            text(info.artist.layoutString, typography = Typography.BODY_SMALL, maxLines = 1),
                        )
                    },
                    bottomSlot = {
                        textEdgeButton(
                            onClick = open(context, if (info.onPhone) MainActivity.OPEN_REMOTE else target),
                            labelContent = { text((if (info.onPhone) "Open" else if (info.playing) "Pause" else "Play").layoutString, color = colorScheme.onPrimary) },
                        )
                    },
                    onClick = open(context, if (info.onPhone) MainActivity.OPEN_REMOTE else MainActivity.OPEN_PLAYER),
                )
            } else {
                primaryLayout(
                    titleSlot = { text("Encore".layoutString) },
                    mainSlot = {
                        text(
                            (if (playlist.isEmpty()) "Pick a playlist to play from here" else playlist).layoutString,
                            typography = Typography.TITLE_MEDIUM,
                            maxLines = 3,
                        )
                    },
                    bottomSlot = {
                        textEdgeButton(
                            onClick = open(context, MainActivity.OPEN_TILE_PLAYLIST),
                            labelContent = { text((if (playlist.isEmpty()) "Choose" else "Shuffle").layoutString, color = colorScheme.onPrimary) },
                        )
                    },
                )
            }
        }
}
