package dev.safro.encore.wear.playback

import dev.safro.encore.wear.data.Settings
import dev.safro.encore.wear.net.NetKind
import java.io.File

/**
 * Where a song plays from when it starts: the copy on the watch if it's downloaded (no network needed), else
 * a stream at the quality set for the network the watch is on (Wi-Fi: wifiFormat / wifiBitrate; LTE or the
 * phone's connection: mobileFormat / mobileBitrate, as on the phone).
 */
class SongResolver(
    private val downloaded: (String) -> File?,
    private val streamUrl: suspend (songId: String, format: String, bitrate: Int) -> String,
    private val network: () -> NetKind,
    private val settings: () -> Settings,
) {
    fun quality(net: NetKind, s: Settings = settings()): Pair<String, Int> =
        if (net == NetKind.WIFI) s.wifiFormat to s.wifiBitrate else s.mobileFormat to s.mobileBitrate

    /** A file path ("file://…") or the stream's URL. */
    suspend fun resolve(songId: String): String {
        downloaded(songId)?.let { return "file://" + it.absolutePath }
        val (format, bitrate) = quality(network())
        return streamUrl(songId, format, bitrate)
    }

    fun isDownloaded(songId: String) = downloaded(songId) != null
}
