package dev.safro.encore.wear.playback

import dev.safro.encore.wear.data.Settings
import dev.safro.encore.wear.net.NetKind
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlayTallyTest {
    private var now = 0L
    private val t = PlayTally { now }

    @Test fun halfTheSongCounts() {
        t.start("a", 200_000, playing = true)
        now = 99_000
        assertFalse(t.counts())
        now = 100_000
        assertTrue(t.counts())
        assertEquals("a", t.start("b", 200_000, playing = true))
    }

    @Test fun fourMinutesCountForLongSongs() {
        t.start("a", 3_600_000, playing = true)
        now = 240_000
        assertTrue(t.counts())
    }

    @Test fun pausesAndSkipsDoNotCount() {
        t.start("a", 200_000, playing = true)
        now = 50_000
        t.playing(false)
        now = 500_000 // paused for a long time
        t.playing(true)
        now = 540_000
        assertEquals(90_000, t.heardMs())
        assertNull(t.start("b", 200_000, playing = true)) // skipped before half
    }

    @Test fun shortSongsNeverCountAndEachPlayCountsOnce() {
        t.start("jingle", 20_000, playing = true)
        now = 20_000
        assertNull(t.finish())
        t.start("a", 100_000, playing = true)
        now += 100_000
        assertEquals("a", t.finish())
        assertNull(t.finish())
    }

    @Test fun durationLearnedLater() {
        t.start("a", 0, playing = true)
        t.durationKnown(60_000)
        now = 30_000
        assertTrue(t.counts())
    }
}

class CrownStepsTest {
    @Test fun slowTurnsAddUp() {
        val c = CrownSteps(pxPerStep = 48f)
        assertEquals(0, c.add(20f))
        assertEquals(0, c.add(20f))
        assertEquals(1, c.add(20f)) // 60 px: one step, 12 left over
        assertEquals(2, c.add(90f))
        assertEquals(-1, c.add(-60f))
    }
}

class SongResolverTest {
    private var net = NetKind.WIFI
    private val kept = mutableMapOf<String, File>()
    private val asked = mutableListOf<Triple<String, String, Int>>()
    private val settings = Settings(wifiFormat = "raw", wifiBitrate = 0, mobileFormat = "opus", mobileBitrate = 96)
    private val r = SongResolver(
        downloaded = { kept[it] },
        streamUrl = { id, f, b -> asked += Triple(id, f, b); "https://music.example.com/rest/stream?id=$id" },
        network = { net },
        settings = { settings },
    )

    @Test fun downloadedSongsPlayFromTheWatch() = runTest {
        kept["s-1"] = File("/data/music/s-1.flac")
        assertEquals("file:///data/music/s-1.flac", r.resolve("s-1"))
        assertTrue(asked.isEmpty())
        assertTrue(r.isDownloaded("s-1"))
    }

    @Test fun qualityFollowsTheNetwork() = runTest {
        r.resolve("s-2")
        net = NetKind.CELLULAR
        r.resolve("s-2")
        net = NetKind.PHONE
        r.resolve("s-2")
        assertEquals(listOf(Triple("s-2", "raw", 0), Triple("s-2", "opus", 96), Triple("s-2", "opus", 96)), asked)
    }
}
