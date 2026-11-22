package dev.safro.encore.wear.tile

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.expression.VersionBuilders
import androidx.wear.tiles.renderer.TileRenderer
import com.github.takahirom.roborazzi.captureRoboImage
import dev.safro.encore.wear.data.NowPlayingStore
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.Executor

/** The tile, drawn with Wear OS's own tile renderer, in its three states (screenshots/tile_*.png). */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w227dp-h227dp-small-notlong-round-watch-xhdpi-keyshidden-nonav")
class TileScreenshotTest {
    private fun device(context: Context) = DeviceParametersBuilders.DeviceParameters.Builder()
        .setScreenWidthDp(227)
        .setScreenHeightDp(227)
        .setScreenDensity(context.resources.displayMetrics.density)
        .setScreenShape(DeviceParametersBuilders.SCREEN_SHAPE_ROUND)
        .setDevicePlatform(DeviceParametersBuilders.DEVICE_PLATFORM_WEAR_OS)
        .setRendererSchemaVersion(VersionBuilders.VersionInfo.Builder().setMajor(1).setMinor(500).build())
        .build()

    private fun shot(name: String, info: NowPlayingStore.Info?, playlist: String, expect: List<String>) {
        ActivityScenario.launch(ComponentActivity::class.java).use { sc ->
            sc.onActivity { activity ->
                val root = FrameLayout(activity).apply { setBackgroundColor(Color.BLACK) }
                activity.setContentView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                val element = TileLayout.layout(activity, device(activity), info, playlist)
                val layout = LayoutElementBuilders.Layout.Builder().setRoot(element).build()
                val renderer = TileRenderer(activity, Executor { it.run() }) {}
                renderer.inflateAsync(layout, ResourceBuilders.Resources.Builder().setVersion("1").build(), root)
                ShadowLooper.idleMainLooper()
                // the edge button's label is sized to fit by the watch's renderer, which Robolectric can't
                // measure (it draws nothing), so check the words are there as well
                val texts = mutableListOf<String>()
                collect(root, texts)
                assertEquals(expect, texts)
                (root as View).captureRoboImage("screenshots/tile_$name.png")
            }
        }
    }

    private fun collect(v: View, into: MutableList<String>) {
        if (v is TextView) into += v.text.toString()
        if (v is ViewGroup) for (i in 0 until v.childCount) collect(v.getChildAt(i), into)
    }

    @Test fun playingOnWatch() = shot(
        "playing", NowPlayingStore.Info("Blue in Green", "Miles Davis", playing = true, onPhone = false), "",
        listOf("Encore", "Blue in Green", "Miles Davis", "Pause"),
    )

    @Test fun playingOnPhone() = shot(
        "phone", NowPlayingStore.Info("So What", "Miles Davis", playing = true, onPhone = true), "",
        listOf("Encore · phone", "So What", "Miles Davis", "Open"),
    )

    @Test fun quickPlay() = shot("quickplay", null, "Running", listOf("Encore", "Running", "Shuffle"))

    @Test fun noPlaylistYet() = shot("choose", null, "", listOf("Encore", "Pick a playlist to play from here", "Choose"))
}
