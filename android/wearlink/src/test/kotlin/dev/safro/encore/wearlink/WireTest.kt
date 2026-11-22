package dev.safro.encore.wearlink

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WireTest {
    @Test fun commandsRoundTrip() {
        val all = listOf(
            Command.Subscribe(60), Command.State, Command.Play, Command.Pause, Command.TogglePlay, Command.Next,
            Command.Previous, Command.SeekTo(12_345), Command.ChangeVolume(-2), Command.SetVolume(7),
            Command.ToggleHeart, Command.Queue, Command.SkipTo(4), Command.Browse("pl:abc"),
            Command.PlayItem("album:1"), Command.PlaySearch("blue train"),
        )
        for (c in all) assertEquals(c, Wire.decodeCommand(Wire.encode(c)))
    }

    @Test fun commandJsonIsStable() {
        // the phone and watch may be on different versions: these strings are the contract
        assertEquals("""{"type":"seek","positionMs":5000}""", Wire.encode(Command.SeekTo(5000)).decodeToString())
        assertEquals("""{"type":"heart"}""", Wire.encode(Command.ToggleHeart).decodeToString())
        assertEquals(Command.Browse(""), Wire.decodeCommand("""{"type":"browse"}""".encodeToByteArray()))
    }

    @Test fun unknownCommandOrGarbageIsNull() {
        assertNull(Wire.decodeCommand("""{"type":"teleport"}""".encodeToByteArray()))
        assertNull(Wire.decodeCommand("not json".encodeToByteArray()))
        assertNull(Wire.decodeCommand(ByteArray(0)))
    }

    @Test fun newerFieldsAreIgnored() {
        val c = Wire.decodeCommand("""{"type":"volume","steps":1,"fromTheFuture":true}""".encodeToByteArray())
        assertEquals(Command.ChangeVolume(1), c)
        val n = Wire.decodeNowPlaying("""{"title":"So What","playing":true,"lyrics":"..."}""".encodeToByteArray())
        assertEquals("So What", n?.title)
        assertTrue(n!!.playing)
    }

    @Test fun repliesRoundTrip() {
        val replies = listOf(
            Reply.State(NowPlaying(mediaId = "song:1:2", title = "Blue in Green", artist = "Miles Davis", starred = true)),
            Reply.Queue(listOf(QueueItem(0, "One"), QueueItem(1, "Two", "Artist", "id2")), currentQueueId = 1),
            Reply.Browse("", listOf(BrowseItem("home", "Home"), BrowseItem("mix:liked", "Liked songs", "Shuffle", true))),
            Reply.Error("No"),
        )
        for (r in replies) assertEquals(r, Wire.decodeReply(Wire.encode(r)))
        assertNull(Wire.decodeReply(null))
    }

    @Test fun accountNeedsServerAndUser() {
        val a = AccountInfo("https://music.example.com", "alice", "s3cret", "http://music.home.example.com:4533")
        assertEquals(a, Wire.decodeAccount(Wire.encode(a)))
        assertNull(Wire.decodeAccount("""{"server":"","user":"alice","pass":"x"}"""))
        assertNull(Wire.decodeAccount(null))
        assertFalse("password must not appear in logs", a.toString().contains("s3cret"))
    }

    @Test fun artFrameRoundTrip() {
        val jpeg = ByteArray(1000) { it.toByte() }
        val f = ArtFrame.decode(ArtFrame("song:é:1", jpeg).encode())!!
        assertEquals("song:é:1", f.mediaId)
        assertArrayEquals(jpeg, f.image)
        assertNull(ArtFrame.decode(byteArrayOf(0)))
        assertNull(ArtFrame.decode(byteArrayOf(0, 9, 1)))
        assertEquals(0, ArtFrame.decode(ArtFrame("x", ByteArray(0)).encode())!!.image.size)
    }

    @Test fun positionCountsOnWhilePlaying() {
        val n = NowPlaying(durationMs = 10_000, positionMs = 2_000, updatedAt = 1_000, playing = true)
        assertEquals(2_000, n.positionAt(1_000))
        assertEquals(5_000, n.positionAt(4_000))
        assertEquals(10_000, n.positionAt(60_000)) // never past the end
        assertEquals(2_000, n.copy(playing = false).positionAt(4_000))
        assertEquals(2_000, n.copy(buffering = true).positionAt(4_000))
        assertEquals(8_000, n.copy(speed = 2f).positionAt(4_000))
        assertEquals(2_000, n.positionAt(0)) // clocks that disagree don't go backwards
    }
}

class DispatcherTest {
    private class Fake : RemoteTarget {
        val calls = mutableListOf<String>()
        var np = NowPlaying(title = "T", playing = true)
        override suspend fun nowPlaying() = np
        override suspend fun play() { calls += "play"; np = np.copy(playing = true) }
        override suspend fun pause() { calls += "pause"; np = np.copy(playing = false) }
        override suspend fun next() { calls += "next" }
        override suspend fun previous() { calls += "previous" }
        override suspend fun seekTo(positionMs: Long) { calls += "seek $positionMs" }
        override suspend fun changeVolume(steps: Int) { calls += "volume $steps" }
        override suspend fun setVolume(level: Int) { calls += "setVolume $level" }
        override suspend fun toggleHeart() { calls += "heart"; np = np.copy(starred = !np.starred) }
        override suspend fun queue() = Reply.Queue(listOf(QueueItem(3, "Q")), 3)
        override suspend fun skipTo(queueId: Long) { calls += "skip $queueId" }
        override suspend fun browse(parentId: String) =
            if (parentId == "boom") throw IllegalStateException("Phone is offline") else listOf(BrowseItem("a", "A"))
        override suspend fun playItem(mediaId: String) { calls += "playItem $mediaId" }
        override suspend fun playSearch(query: String) { calls += "search $query" }
        override fun subscribe(nodeId: String, seconds: Int) { calls += "subscribe $nodeId $seconds" }
    }

    private suspend fun ask(f: Fake, c: Command?) = RemoteDispatcher.handle("watch-1", c, f)

    @Test fun commandsReachThePlayerAndAnswerWithState() = runTest {
        val f = Fake()
        val r = ask(f, Command.ToggleHeart)
        assertEquals(listOf("heart"), f.calls)
        assertTrue((r as Reply.State).nowPlaying.starred)
        ask(f, Command.TogglePlay)
        ask(f, Command.TogglePlay)
        ask(f, Command.SeekTo(-50))
        ask(f, Command.SetVolume(-3))
        ask(f, Command.Subscribe(100_000))
        assertEquals(listOf("heart", "pause", "play", "seek 0", "setVolume 0", "subscribe watch-1 300"), f.calls)
    }

    @Test fun queueAndBrowseAnswerWithLists() = runTest {
        val f = Fake()
        assertEquals(3L, (ask(f, Command.Queue) as Reply.Queue).currentQueueId)
        assertEquals("A", (ask(f, Command.Browse("x")) as Reply.Browse).items.single().title)
    }

    @Test fun failuresBecomeErrors() = runTest {
        val f = Fake()
        assertEquals(Reply.Error("Phone is offline"), ask(f, Command.Browse("boom")))
        assertTrue(ask(f, null) is Reply.Error)
        val bytes = RemoteDispatcher.handle("w", """{"type":"teleport"}""".encodeToByteArray(), f)
        assertTrue(Wire.decodeReply(bytes) is Reply.Error)
    }
}
