package dev.safro.encore.wear.downloads

import dev.safro.encore.wear.subsonic.Song
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class RangeDownloaderTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var server: MockWebServer
    private val body = ByteArray(10_000) { (it % 251).toByte() }
    private val dl = RangeDownloader(OkHttpClient())

    @Before fun start() { server = MockWebServer(); server.start() }
    @After fun stop() { server.close() }

    private fun dest() = File(tmp.root, "music/s-1.flac")
    private fun url() = server.url("/rest/download?id=s-1")
    private fun bytes(from: Int, to: Int = body.size) = Buffer().write(body, from, to - from)

    @Test fun freshDownload() {
        server.enqueue(MockResponse.Builder().body(bytes(0)).build())
        assertEquals(10_000L, dl.download(url(), dest()))
        assertArrayEquals(body, dest().readBytes())
        assertNull(server.takeRequest().headers["Range"])
        assertFalse(RangeDownloader.partFile(dest()).exists())
    }

    @Test fun resumesWhereThePartEnds() {
        dest().parentFile!!.mkdirs()
        RangeDownloader.partFile(dest()).writeBytes(body.copyOfRange(0, 4_000)) // cut off last time
        server.enqueue(
            MockResponse.Builder().code(206)
                .addHeader("Content-Range", "bytes 4000-9999/10000")
                .body(bytes(4_000)).build(),
        )
        var lastProgress = 0L to 0L
        dl.download(url(), dest(), progress = { d, t -> lastProgress = d to t })
        assertEquals("bytes=4000-", server.takeRequest().headers["Range"])
        assertArrayEquals(body, dest().readBytes())
        assertEquals(10_000L to 10_000L, lastProgress)
    }

    @Test fun serverIgnoringTheRangeStartsOver() {
        dest().parentFile!!.mkdirs()
        RangeDownloader.partFile(dest()).writeBytes(ByteArray(3_000) { 7 })
        server.enqueue(MockResponse.Builder().code(200).body(bytes(0)).build())
        dl.download(url(), dest())
        assertArrayEquals(body, dest().readBytes()) // not 3000 junk bytes + the file
    }

    @Test fun aRangeThatDoesNotFitStartsOver() {
        dest().parentFile!!.mkdirs()
        RangeDownloader.partFile(dest()).writeBytes(body.copyOfRange(0, 4_000))
        server.enqueue(MockResponse.Builder().code(206).addHeader("Content-Range", "bytes 0-9999/10000").body(bytes(0)).build())
        server.enqueue(MockResponse.Builder().code(200).body(bytes(0)).build())
        dl.download(url(), dest())
        assertEquals("bytes=4000-", server.takeRequest().headers["Range"])
        assertNull(server.takeRequest().headers["Range"])
        assertArrayEquals(body, dest().readBytes())
    }

    @Test fun nothingLeftToGetMeansTheFileIsComplete() {
        dest().parentFile!!.mkdirs()
        RangeDownloader.partFile(dest()).writeBytes(body)
        server.enqueue(MockResponse.Builder().code(416).addHeader("Content-Range", "bytes */10000").build())
        assertEquals(10_000L, dl.download(url(), dest()))
        assertArrayEquals(body, dest().readBytes())
    }

    @Test fun cutOffMidwayKeepsThePartForNextTime() {
        // a server that promises 10000 bytes and drops the connection after 6000
        java.net.ServerSocket(0).use { ss ->
            val t = Thread {
                ss.accept().use { sock ->
                    sock.getInputStream().read(ByteArray(4096))
                    val out = sock.getOutputStream()
                    out.write("HTTP/1.1 200 OK\r\nContent-Length: 10000\r\nConnection: close\r\n\r\n".toByteArray())
                    out.write(body, 0, 6_000)
                    out.flush()
                }
            }
            t.start()
            try {
                dl.download(okhttp3.HttpUrl.Builder().scheme("http").host("127.0.0.1").port(ss.localPort).addPathSegment("x").build(), dest())
                fail()
            } catch (_: IOException) {}
            t.join()
        }
        assertFalse(dest().exists())
        assertEquals(6_000L, RangeDownloader.partFile(dest()).length())
        // next try: only the rest is asked for
        server.enqueue(MockResponse.Builder().code(206).addHeader("Content-Range", "bytes 6000-9999/10000").body(bytes(6_000)).build())
        dl.download(url(), dest())
        assertEquals("bytes=6000-", server.takeRequest().headers["Range"])
        assertArrayEquals(body, dest().readBytes())
    }

    @Test fun storageCapStopsBeforeWriting() {
        server.enqueue(MockResponse.Builder().body(bytes(0)).build())
        try {
            dl.download(url(), dest(), room = { it <= 5_000 })
            fail()
        } catch (e: OverCapException) {
            assertEquals(10_000L, e.needBytes)
        }
        assertFalse(dest().exists())
    }

    @Test fun goneFilesArePermanentFailures() {
        server.enqueue(MockResponse.Builder().code(404).build())
        try {
            dl.download(url(), dest())
            fail()
        } catch (_: PermanentDownloadException) {}
    }

    @Test fun anExistingFileIsNotFetchedAgain() {
        dest().parentFile!!.mkdirs()
        dest().writeBytes(body)
        assertEquals(10_000L, dl.download(url(), dest()))
        assertEquals(0, server.requestCount)
    }

    @Test fun contentRangeParsing() {
        assertEquals(ContentRange(100, 999, 1000), ContentRange.parse("bytes 100-999/1000"))
        assertEquals(ContentRange(-1, -1, 1000), ContentRange.parse("bytes */1000"))
        assertEquals(ContentRange(0, 9, -1), ContentRange.parse("bytes 0-9/*"))
        assertNull(ContentRange.parse(null))
        assertNull(ContentRange.parse("items 1-2/3"))
    }
}

class DownloadStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun song(id: String) = Song(id, "Song $id", "Artist", suffix = "flac")

    @Test fun songsSharedByTwoDownloadsAreKeptOnceAndUntilBothGo() {
        val store = DownloadStore(tmp.root)
        store.add("album", "a1", "Album", "", listOf(song("1"), song("2")))
        store.add("playlist", "p1", "List", "", listOf(song("2"), song("3")))
        assertEquals(3, store.current.songs.size)
        store.musicDir.mkdirs()
        for (id in listOf("1", "2", "3")) {
            val name = store.fileName(song(id), "raw")
            File(store.musicDir, name).writeBytes(ByteArray(100))
            store.songDone(id, name, 100)
        }
        assertEquals(300L, store.usedBytes())
        store.remove("album:a1")
        assertEquals(setOf("2", "3"), store.current.songs.keys)
        assertNull(store.fileFor("1"))
        assertTrue(store.fileFor("2")!!.exists())
        assertEquals(200L, store.usedBytes())
    }

    @Test fun survivesARestart() {
        val a = DownloadStore(tmp.root)
        a.add("album", "a1", "Album", "c1", listOf(song("1")))
        a.setState("album:a1", DownloadState.FULL, "Storage cap reached")
        val b = DownloadStore(tmp.root)
        assertEquals(DownloadState.FULL, b.collection("album:a1")!!.state)
        assertEquals("Song 1", b.current.songs["1"]!!.song.title)
    }

    @Test fun fileNamesAreSafe() {
        val s = DownloadStore(tmp.root)
        assertEquals("a_b_c.flac", s.fileName(Song("a/b:c", "t", suffix = "flac"), "raw"))
        assertEquals("x.opus", s.fileName(Song("x", "t", suffix = "flac"), "opus"))
        assertEquals("x.audio", s.fileName(Song("x", "t"), "raw"))
    }

    @Test fun onlyCompleteFilesPlay() {
        val s = DownloadStore(tmp.root)
        s.add("album", "a", "A", "", listOf(song("1")))
        s.songStarted("1", "1.flac")
        assertNull(s.fileFor("1"))
    }
}
