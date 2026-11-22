package dev.safro.encore.wearlink

/** The phone's player, as the watch remote needs it. The phone app implements it over its media session. */
interface RemoteTarget {
    suspend fun nowPlaying(): NowPlaying
    suspend fun play()
    suspend fun pause()
    suspend fun next()
    suspend fun previous()
    suspend fun seekTo(positionMs: Long)
    suspend fun changeVolume(steps: Int)
    suspend fun setVolume(level: Int)
    suspend fun toggleHeart()
    suspend fun queue(): Reply.Queue
    suspend fun skipTo(queueId: Long)
    suspend fun browse(parentId: String): List<BrowseItem>
    suspend fun playItem(mediaId: String)
    suspend fun playSearch(query: String)
    /** Keep sending state to [nodeId] for [seconds]; [fresh]: its screen just opened (it needs the cover). */
    fun subscribe(nodeId: String, seconds: Int, fresh: Boolean)
}

/** Turns a request from the watch into calls on the phone's player, and says what to answer. */
object RemoteDispatcher {
    /** Longest a watch can ask for updates in one go (it renews while its screen is on). */
    const val MAX_SUBSCRIBE_SECONDS = 300

    suspend fun handle(nodeId: String, request: ByteArray, target: RemoteTarget): ByteArray =
        Wire.encode(handle(nodeId, Wire.decodeCommand(request), target))

    suspend fun handle(nodeId: String, command: Command?, target: RemoteTarget): Reply {
        if (command == null) return Reply.Error("Update Encore on your phone")
        return try {
            when (command) {
                is Command.Subscribe -> target.subscribe(nodeId, command.seconds.coerceIn(1, MAX_SUBSCRIBE_SECONDS), command.fresh)
                Command.State -> Unit
                Command.Play -> target.play()
                Command.Pause -> target.pause()
                Command.TogglePlay -> if (target.nowPlaying().playing) target.pause() else target.play()
                Command.Next -> target.next()
                Command.Previous -> target.previous()
                is Command.SeekTo -> target.seekTo(command.positionMs.coerceAtLeast(0))
                is Command.ChangeVolume -> target.changeVolume(command.steps)
                is Command.SetVolume -> target.setVolume(command.level.coerceAtLeast(0))
                Command.ToggleHeart -> target.toggleHeart()
                Command.Queue -> return target.queue()
                is Command.SkipTo -> target.skipTo(command.queueId)
                is Command.Browse -> return Reply.Browse(command.parentId, target.browse(command.parentId))
                is Command.PlayItem -> target.playItem(command.mediaId)
                is Command.PlaySearch -> target.playSearch(command.query)
            }
            Reply.State(target.nowPlaying())
        } catch (e: java.util.concurrent.CancellationException) {
            throw e
        } catch (e: Exception) {
            Reply.Error(e.message ?: "Encore on your phone couldn't do that")
        }
    }
}
