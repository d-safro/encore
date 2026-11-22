package dev.safro.encore.wear.subsonic

import dev.safro.encore.wear.data.Account
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

private fun canned(name: String): String =
    SubsonicTest::class.java.getResource("/subsonic/$name.json")!!.readText()

class AuthTest {
    @Test fun tokenIsMd5OfPasswordPlusSalt() {
        // the Subsonic API documentation's own example
        assertEquals("26719a1196d2a940705a59634eb18eab", Auth.token("sesame", "c19b2d"))
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", Auth.md5Hex(""))
    }

    @Test fun saltsAreFreshAndPlain() {
        val a = Auth.newSalt()
        val b = Auth.newSalt()
        assertEquals(12, a.length)
        assertTrue(a.all { it in '0'..'9' || it in 'a'..'z' })
        assertNotEquals(a, b)
    }
}

class UrlTest {
    private val acct = Account("https://music.example.com", "alice", "sesame")

    @Test fun buildsTokenAuthUrlsWithoutThePassword() {
        val u = SubsonicUrls.build("https://music.example.com/", "getAlbum", acct, listOf("id" to "al 1&x"), salt = "c19b2d")
        assertEquals("https", u.scheme)
        assertEquals("music.example.com", u.host)
        assertEquals("/rest/getAlbum", u.encodedPath)
        assertEquals("alice", u.queryParameter("u"))
        assertEquals("26719a1196d2a940705a59634eb18eab", u.queryParameter("t"))
        assertEquals("c19b2d", u.queryParameter("s"))
        assertEquals("1.16.1", u.queryParameter("v"))
        assertEquals("json", u.queryParameter("f"))
        assertEquals("Encore Watch", u.queryParameter("c"))
        assertEquals("al 1&x", u.queryParameter("id"))
        assertNull(u.queryParameter("p"))
        assertFalse(u.toString().contains("sesame"))
    }

    @Test fun keepsAServerUnderAPath() {
        val u = SubsonicUrls.build("https://example.com/navidrome", "ping", acct, salt = "x")
        assertEquals("/navidrome/rest/ping", u.encodedPath)
    }

    @Test fun addressesAreNormalizedLikeThePhone() {
        assertEquals("https://music.example.com", SubsonicUrls.normalize(" music.example.com/ "))
        assertEquals("http://192.0.2.10:4533", SubsonicUrls.normalize("http://192.0.2.10:4533//"))
        assertEquals("", SubsonicUrls.normalize("  "))
    }

    @Test fun badAddressSaysSo() {
        try {
            SubsonicUrls.build("https://", "ping", acct)
            fail()
        } catch (e: SubsonicException) {
            assertTrue(e.message!!.contains("isn't valid"))
        }
    }

    @Test fun streamQuality() {
        assertEquals(listOf("id" to "s", "format" to "raw"), SubsonicClient.streamParams("s", "raw", 320).toList())
        assertEquals(listOf("id" to "s", "format" to "opus", "maxBitRate" to "128"), SubsonicClient.streamParams("s", "opus", 128).toList())
        assertEquals(listOf("id" to "s", "format" to "mp3"), SubsonicClient.streamParams("s", "mp3", 0).toList())
    }
}

class ParseTest {
    private val client = SubsonicClient({ null }, { "" }, OkHttpClient())
    private fun r(name: String) = client.parseResponse(canned(name))

    @Test fun albumList() {
        val list = Parse.albumList(r("albumList2"))
        assertEquals(2, list.size)
        assertEquals(Album("al-1", "Kind of Blue", "Miles Davis", "al-1_0", 5, 1959), list[0])
        assertEquals("2", list[1].id) // numeric ids come out as strings
        assertEquals("A Love Supreme", list[1].name) // "title" when there's no "name"
    }

    @Test fun albumWithSongs() {
        val a = Parse.album(r("album"))
        assertEquals(2, a.songs.size)
        val s = a.songs[0]
        assertEquals("So What", s.title)
        assertEquals(562, s.durationSec)
        assertEquals(22062419L, s.size)
        assertEquals("flac", s.suffix)
        assertTrue(s.starred)
        assertFalse(a.songs[1].starred)
        assertEquals(589, a.songs[1].durationSec) // fractional durations
    }

    @Test fun singlePlaylistComesAsAnObject() {
        val p = Parse.playlists(r("playlists"))
        assertEquals(listOf(Playlist("pl-1", "Running", 42, "pl-1", 9000)), p)
    }

    @Test fun playlistEntriesWithoutIdAreDropped() {
        val p = Parse.playlist(r("playlist"))
        assertEquals(listOf("s-9", "s-10"), p.songs.map { it.id })
    }

    @Test fun starredAndSearch() {
        val st = Parse.starred(r("starred2"))
        assertEquals("s-1", st.songs.single().id)
        assertEquals("al-1", st.albums.single().id)
        val s = Parse.search(r("search3"))
        assertEquals(1, s.songs.size)
        assertEquals("Miles Davis", s.artists.single().name)
        assertTrue(Parse.search(r("empty_search")).isEmpty)
    }

    @Test fun serverErrorsAreReadable() {
        try {
            r("error")
            fail()
        } catch (e: SubsonicException) {
            assertEquals(40, e.code)
            assertEquals("Wrong username or password", e.message)
        }
        try {
            client.parseResponse("<html>Not a music server</html>")
            fail()
        } catch (e: SubsonicException) {
            assertTrue(e.message!!.contains("doesn't answer like"))
        }
    }
}

class SubsonicTest {
    private lateinit var server: MockWebServer
    private val acct = Account("https://unused.example.com", "alice", "sesame")

    @Before fun start() { server = MockWebServer(); server.start() }
    @After fun stop() { server.close() }

    private fun client() = SubsonicClient({ acct }, { server.url("/").toString() }, OkHttpClient())

    @Test fun callsTheChosenAddressWithAFreshSaltEachTime() = runTest {
        server.enqueue(MockResponse.Builder().body(canned("playlists")).build())
        server.enqueue(MockResponse.Builder().body(canned("playlists")).build())
        val c = client()
        assertEquals("Running", c.playlists().single().name)
        c.playlists()
        val a = server.takeRequest().url
        val b = server.takeRequest().url
        assertEquals("/rest/getPlaylists", a.encodedPath)
        assertNotEquals(a.queryParameter("s"), b.queryParameter("s"))
        assertEquals(Auth.token("sesame", a.queryParameter("s")!!), a.queryParameter("t"))
    }

    @Test fun refusedLoginIsWrongPassword() = runTest {
        server.enqueue(MockResponse.Builder().code(401).build())
        try {
            client().ping()
            fail()
        } catch (e: SubsonicException) {
            assertEquals(40, e.code)
        }
    }

    @Test fun starAndScrobble() = runTest {
        repeat(3) { server.enqueue(MockResponse.Builder().body("""{"subsonic-response":{"status":"ok"}}""").build()) }
        val c = client()
        c.star("s-1", true)
        c.star("s-1", false)
        c.scrobble("s-1", submission = true, timeMs = 1234)
        assertEquals("/rest/star", server.takeRequest().url.encodedPath)
        assertEquals("/rest/unstar", server.takeRequest().url.encodedPath)
        val s = server.takeRequest().url
        assertEquals("true", s.queryParameter("submission"))
        assertEquals("1234", s.queryParameter("time"))
    }

    @Test fun downloadsUseTheOriginalFileOrATranscode() = runTest {
        val c = client()
        assertEquals("/rest/download", c.downloadUrl("s-1", "raw", 0).encodedPath)
        val t = c.downloadUrl("s-1", "opus", 160)
        assertEquals("/rest/stream", t.encodedPath)
        assertEquals("160", t.queryParameter("maxBitRate"))
    }
}
