package dev.safro.encore.wear.downloads

import dev.safro.encore.wear.subsonic.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
enum class DownloadState { QUEUED, DOWNLOADING, DONE, FULL, FAILED }

/** An album or playlist kept on the watch. */
@Serializable
data class Collection(
    val id: String,
    /** "album" or "playlist" */
    val kind: String,
    val name: String,
    val coverArt: String = "",
    val songIds: List<String> = emptyList(),
    val state: DownloadState = DownloadState.QUEUED,
    val error: String = "",
) {
    val key get() = "$kind:$id"
}

@Serializable
data class KeptSong(val song: Song, val file: String = "", val bytes: Long = 0, val done: Boolean = false)

@Serializable
data class Downloads(val collections: List<Collection> = emptyList(), val songs: Map<String, KeptSong> = emptyMap()) {
    fun songsOf(c: Collection) = c.songIds.mapNotNull { songs[it] }
    fun doneCount(c: Collection) = songsOf(c).count { it.done }
}

/**
 * What's downloaded to the watch: albums and playlists, and the songs in them (a song in two of them is kept
 * once). Kept as JSON next to the music in the app's own storage.
 */
class DownloadStore(private val dir: File) {
    private val json = Json { ignoreUnknownKeys = true }
    private val file = File(dir, "downloads.json")
    val musicDir = File(dir, "music")
    private val _state = MutableStateFlow(load())
    val state: StateFlow<Downloads> = _state.asStateFlow()
    val current get() = _state.value

    private fun load(): Downloads =
        runCatching { json.decodeFromString(Downloads.serializer(), file.readText()) }.getOrDefault(Downloads())

    @Synchronized
    private fun change(f: (Downloads) -> Downloads) {
        val next = f(_state.value)
        dir.mkdirs()
        val tmp = File(dir, "downloads.json.tmp")
        tmp.writeText(json.encodeToString(Downloads.serializer(), next))
        tmp.renameTo(file)
        _state.value = next
    }

    fun collection(key: String) = current.collections.firstOrNull { it.key == key }

    fun add(kind: String, id: String, name: String, coverArt: String, songs: List<Song>) = change { d ->
        val c = Collection(id, kind, name, coverArt, songs.map { it.id }.distinct())
        val kept = d.songs.toMutableMap()
        for (s in songs) kept[s.id] = kept[s.id]?.copy(song = s) ?: KeptSong(s)
        d.copy(collections = d.collections.filter { it.key != c.key } + c, songs = kept)
    }

    /** Removes it, and the files of songs no other download needs. */
    fun remove(key: String) = change { d ->
        val rest = d.collections.filter { it.key != key }
        val needed = rest.flatMap { it.songIds }.toSet()
        val kept = d.songs.filter { (id, k) ->
            (id in needed).also { keep -> if (!keep) deleteFiles(k) }
        }
        d.copy(collections = rest, songs = kept)
    }

    private fun deleteFiles(k: KeptSong) {
        if (k.file.isEmpty()) return
        val f = File(musicDir, k.file)
        f.delete()
        RangeDownloader.partFile(f).delete()
    }

    fun setState(key: String, state: DownloadState, error: String = "") = change { d ->
        d.copy(collections = d.collections.map { if (it.key == key) it.copy(state = state, error = error) else it })
    }

    fun songDone(id: String, fileName: String, bytes: Long) = change { d ->
        val k = d.songs[id] ?: return@change d
        d.copy(songs = d.songs + (id to k.copy(file = fileName, bytes = bytes, done = true)))
    }

    fun songStarted(id: String, fileName: String) = change { d ->
        val k = d.songs[id] ?: return@change d
        d.copy(songs = d.songs + (id to k.copy(file = fileName)))
    }

    /** The downloaded file of a song, if it's complete. */
    fun fileFor(songId: String): File? {
        val k = current.songs[songId] ?: return null
        if (!k.done || k.file.isEmpty()) return null
        return File(musicDir, k.file).takeIf { it.exists() }
    }

    /** What the downloads take on the watch now, including parts still arriving. */
    fun usedBytes(): Long = musicDir.listFiles()?.sumOf { it.length() } ?: 0L

    /** File name for a song: its id made safe, and the format it arrives in. */
    fun fileName(song: Song, format: String): String {
        val ext = if (format == "raw") song.suffix.ifEmpty { "audio" } else format
        return song.id.replace(Regex("[^A-Za-z0-9._-]"), "_") + "." + ext.replace(Regex("[^A-Za-z0-9]"), "")
    }
}
