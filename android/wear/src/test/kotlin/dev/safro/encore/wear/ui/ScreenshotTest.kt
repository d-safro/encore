package dev.safro.encore.wear.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.TimeSource
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import dev.safro.encore.wear.subsonic.Album
import dev.safro.encore.wear.subsonic.Playlist
import dev.safro.encore.wear.subsonic.SearchResult
import dev.safro.encore.wear.subsonic.Song
import dev.safro.encore.wearlink.BrowseItem
import dev.safro.encore.wearlink.QueueItem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every screen, drawn by Robolectric and saved by Roborazzi to android/wear/screenshots/: on a round
 * 454 px watch and a square one, awake and in ambient mode. Data is made up (example.com, public albums).
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
abstract class ScreenshotTest(private val shape: String) {
    private val fixedTime = object : TimeSource {
        @Composable override fun currentTime() = "10:09"
    }

    /** Made-up covers: a gradient per album, so layouts show where pictures go. */
    private val fakeArt = ArtSource { coverArt, size -> art(coverArt, size) }

    private fun art(key: String, size: Int): ImageBitmap {
        val palette = listOf(0xFF3949AB, 0xFFD81B60, 0xFF00897B, 0xFFF4511E, 0xFF8E24AA, 0xFF7CB342)
        val h = key.hashCode().let { if (it < 0) -it else it }
        val a = palette[h % palette.size].toInt()
        val b = palette[(h / 7 + 2) % palette.size].toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawRect(0f, 0f, size.toFloat(), size.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), a, b, Shader.TileMode.CLAMP)
        })
        return bmp.asImageBitmap()
    }

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    /**
     * Draws [content] awake, then in ambient mode. The clock only moves when told (endless animations such
     * as the scrolling title and spinners would otherwise never let the screen settle).
     */
    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        var ambient by mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalArt provides fakeArt) {
                EncoreTheme(ambient) {
                    Frame { EncoreScaffold(timeSource = fixedTime) { content() } }
                }
            }
        }
        for (mode in listOf("light", "ambient")) {
            ambient = mode == "ambient" // switched in place, as the watch does
            repeat(4) {
                compose.waitForIdle()
                compose.mainClock.advanceTimeBy(500)
            }
            compose.onRoot().captureRoboImage("screenshots/${name}_${shape}_$mode.png", RoborazziOptions())
        }
    }

    /** The screen's edge: outside a round screen is grey, so anything cut off by it shows. */
    @Composable
    private fun Frame(content: @Composable () -> Unit) {
        val round = LocalConfiguration.current.isScreenRound
        Box(Modifier.fillMaxSize().background(Color(0xFF6B6B6B))) {
            val m = if (round) Modifier.fillMaxSize().clip(CircleShape).background(Color.Black).border(1.dp, Color(0xFF444444), CircleShape)
            else Modifier.fillMaxSize().background(Color.Black)
            Box(m) { content() }
        }
    }

    private val kindOfBlue = Album("al-1", "Kind of Blue", "Miles Davis", "al-1", 5, 1959)
    private val songs = listOf(
        Song("s-1", "So What", "Miles Davis", "Kind of Blue", coverArt = "al-1", durationSec = 562),
        Song("s-2", "Freddie Freeloader", "Miles Davis", coverArt = "al-1", durationSec = 589),
        Song("s-3", "Blue in Green", "Miles Davis", coverArt = "al-1", durationSec = 337),
        Song("s-4", "All Blues", "Miles Davis", coverArt = "al-1", durationSec = 693),
        Song("s-5", "Flamenco Sketches", "Miles Davis", coverArt = "al-1", durationSec = 566),
    )
    private val playing = NowPlayingUi(
        title = "Blue in Green", artist = "Miles Davis", coverArt = "al-1", playing = true, progress = 0.42f,
        starred = true, volume = 0.6f, output = "Pixel Buds Pro",
    )
    private val watchActions = PlayerActions(onOutput = {}, onQueue = {})
    private val phoneActions = PlayerActions(onQueue = {}, onLibrary = {})

    @Test fun home() = shot("home") {
        HomeScreen(HomeUi(watchPlaying = "Blue in Green · Miles Davis", phonePlaying = null, phoneReachable = true, downloads = 2)) {}
    }

    @Test fun player() = shot("player") { PlayerScreen(playing, watchActions) }

    @Test fun playerVolume() = shot("player_volume") { PlayerScreen(playing.copy(playing = false), watchActions, showVolume = true) }

    @Test fun playerNoHeadphones() = shot("player_headphones") {
        PlayerScreen(playing.copy(output = null, needsHeadphones = true, playing = false), watchActions)
    }

    @Test fun playerEmpty() = shot("player_empty") { PlayerScreen(NowPlayingUi(), watchActions) }

    @Test fun remote() = shot("remote") {
        PlayerScreen(
            NowPlayingUi(title = "A Love Supreme, Pt. I – Acknowledgement", artist = "John Coltrane", coverArt = "al-2",
                playing = true, progress = 0.15f, onPhone = true),
            phoneActions,
        )
    }

    @Test fun remoteNotConnected() = shot("remote_offline") {
        PlayerScreen(NowPlayingUi(onPhone = true, message = "Phone not connected"), phoneActions)
    }

    @Test fun albums() = shot("albums") {
        AlbumsScreen("Recent albums", Load.Ok(listOf(
            kindOfBlue,
            Album("al-2", "A Love Supreme", "John Coltrane", "al-2"),
            Album("al-3", "Mingus Ah Um", "Charles Mingus", "al-3"),
            Album("al-4", "Time Out", "The Dave Brubeck Quartet", "al-4"),
        ))) {}
    }

    @Test fun albumsLoading() = shot("albums_loading") { AlbumsScreen("Recent albums", Load.Loading) {} }

    @Test fun albumsOffline() = shot("albums_offline") { AlbumsScreen("Recent albums", Load.Failed("Can't reach the server")) {} }

    @Test fun playlists() = shot("playlists") {
        PlaylistsScreen(Load.Ok(listOf(
            Playlist("p1", "Running", 42, "p1"),
            Playlist("p2", "Late night jazz", 18, "p2"),
            Playlist("p3", "Road trip with a rather long playlist name", 120, "p3"),
        ))) {}
    }

    @Test fun album() = shot("album") {
        SongsScreen("Kind of Blue", "Miles Davis", "al-1", Load.Ok(songs), KeepUi(kept = true, done = 3, total = 5), { _, _ -> }, onKeep = {}, playingId = "s-3")
    }

    @Test fun favourites() = shot("favourites") {
        SongsScreen("Favourites", null, null, Load.Ok(songs.take(3)), null, { _, _ -> })
    }

    @Test fun search() = shot("search") {
        SearchScreen("blue", Load.Ok(SearchResult(songs = songs.take(2), albums = listOf(kindOfBlue))), {}, { _, _ -> }, {})
    }

    @Test fun downloads() = shot("downloads") {
        DownloadsScreen(
            listOf(
                DownloadRow("album:al-1", "Kind of Blue", "al-1", "5 songs"),
                DownloadRow("playlist:p1", "Running", "p1", "12 of 42"),
                DownloadRow("playlist:p3", "Road trip", "p3", "Storage cap reached"),
            ),
            usedMb = 1410, capMb = 2048,
        ) {}
    }

    @Test fun downloadsEmpty() = shot("downloads_empty") { DownloadsScreen(emptyList(), 0, 2048) {} }

    @Test fun settings() = shot("settings") {
        SettingsScreen(
            SettingsUi("https://music.example.com", "alice", fromPhone = true, localAddress = "http://music.home.example.com:4533",
                onHomeAddress = true, tilePlaylist = "Running", version = "1.9.0"),
            SettingsActions(),
        )
    }

    @Test fun storageCap() = shot("storage_cap") { CapScreen(2048, listOf(512, 1024, 2048, 4096, 8192)) {} }

    @Test fun signIn() = shot("signin") { SignInScreen(SignInUi(), SignInActions()) }

    @Test fun signInFilled() = shot("signin_filled") {
        SignInScreen(SignInUi("https://music.example.com", "alice", passSet = true, error = "Wrong username or password"), SignInActions())
    }

    @Test fun phoneQueue() = shot("phone_queue") {
        PhoneQueueScreen(Load.Ok(songs.mapIndexed { i, s -> QueueItem(i.toLong(), s.title, s.artist) }), current = 2) {}
    }

    @Test fun phoneLibrary() = shot("phone_library") {
        PhoneLibraryScreen("Phone's library", Load.Ok(listOf(
            BrowseItem("home", "Home"), BrowseItem("playlists", "Playlists"), BrowseItem("albums", "Albums"),
            BrowseItem("radio", "Radio"), BrowseItem("mix:liked", "Liked songs", "Shuffle", playable = true),
        ))) {}
    }

    @Test fun queue() = shot("queue") { QueueScreen(songs, 2) {} }
}

@Config(sdk = [35], qualifiers = "w227dp-h227dp-small-notlong-round-watch-xhdpi-keyshidden-nonav")
class RoundScreenshotTest : ScreenshotTest("round")

@Config(sdk = [35], qualifiers = "w180dp-h180dp-small-notlong-notround-watch-xhdpi-keyshidden-nonav")
class SquareScreenshotTest : ScreenshotTest("square")
