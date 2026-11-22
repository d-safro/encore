package dev.safro.encore.wear.data

import dev.safro.encore.wear.data.NowPlayingStore.Companion.PHONE_STALE_MS
import dev.safro.encore.wear.data.NowPlayingStore.Companion.accepts
import dev.safro.encore.wear.data.NowPlayingStore.Companion.shown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingStoreTest {
    private val watchPlaying = NowPlayingStore.Info("A", "X", playing = true, onPhone = false)
    private val watchPaused = watchPlaying.copy(playing = false)
    private val phone = NowPlayingStore.Info("B", "Y", playing = true, onPhone = true)

    @Test fun thePhoneNeverClearsOrCoversTheWatchsOwnPlayer() {
        assertFalse(accepts(watchPlaying, phone, fromPhone = true))
        assertFalse(accepts(watchPlaying, null, fromPhone = true))
        assertFalse(accepts(watchPaused, null, fromPhone = true))
        assertTrue(accepts(watchPaused, phone, fromPhone = true)) // the watch is idle: the phone's song shows
        assertTrue(accepts(phone, null, fromPhone = true))
        assertTrue(accepts(null, phone, fromPhone = true))
        assertTrue(accepts(phone, null, fromPhone = false)) // the watch's own player always counts
    }

    @Test fun thePhonesSongGoesStale() {
        assertEquals(phone, shown(phone, phoneAt = 0, now = PHONE_STALE_MS))
        assertNull(shown(phone, phoneAt = 0, now = PHONE_STALE_MS + 1))
        assertEquals(watchPaused, shown(watchPaused, phoneAt = 0, now = 10 * PHONE_STALE_MS))
    }
}
