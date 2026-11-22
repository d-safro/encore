package dev.safro.encore.wear.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.safro.encore.wear.complication.NowPlayingComplicationService
import dev.safro.encore.wearlink.AccountInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AccountStoreTest {
    private val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("t", Context.MODE_PRIVATE)
    private val phone = AccountInfo("https://music.example.com", "alice", "pw", "http://music.home.example.com")

    @Test fun takesThePhonesSignInOnFirstPairing() {
        val s = AccountStore(prefs)
        assertNull(s.current)
        assertTrue(s.offerFromPhone(phone))
        assertEquals(Account(phone.server, "alice", "pw", phone.localAddress, fromPhone = true), s.current)
        // kept under the phone's setting names
        assertEquals("https://music.example.com", prefs.getString("server", null))
        assertEquals("http://music.home.example.com", prefs.getString("localAddress", null))
        assertEquals(s.current, AccountStore(prefs).current)
    }

    @Test fun followsThePhoneButKeepsOneTypedOnTheWatch() {
        val s = AccountStore(prefs)
        s.offerFromPhone(phone)
        assertTrue(s.offerFromPhone(phone.copy(server = "https://other.example.com")))
        assertEquals("https://other.example.com", s.current!!.server)
        assertFalse(s.offerFromPhone(phone.copy(server = "https://other.example.com"))) // nothing new
        s.save(Account("https://watch.example.com", "bob", "pw2"))
        assertFalse(s.offerFromPhone(phone))
        assertEquals("bob", s.current!!.user)
    }

    @Test fun signOutForgetsEverything() {
        val s = AccountStore(prefs)
        s.offerFromPhone(phone)
        s.signOut()
        assertNull(s.current)
        assertNull(prefs.getString("pass", null))
    }

    @Test fun passwordNeverInToString() {
        assertFalse(Account("https://x.example.com", "u", "secret!").toString().contains("secret!"))
    }

    @Test fun settingsKeepThePhonesNames() {
        val s = SettingsStore(prefs)
        assertEquals(2048, s.current.downloadCapMb)
        s.update { it.copy(downloadCapMb = 512, downloadWifiOnly = false, mobileFormat = "mp3") }
        assertEquals(512, prefs.getInt("downloadCapMb", 0))
        assertFalse(prefs.getBoolean("downloadWifiOnly", true))
        assertEquals("mp3", SettingsStore(prefs).current.mobileFormat)
    }

    @Test fun complicationWords() {
        assertEquals("Encore" to "Nothing playing", NowPlayingComplicationService.words(null))
        assertEquals("So What" to "Miles Davis", NowPlayingComplicationService.words(NowPlayingStore.Info("So What", "Miles Davis", true, false)))
    }
}
