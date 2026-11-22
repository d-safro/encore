package dev.safro.encore.wear.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import dev.safro.encore.wear.subsonic.Song

/**
 * Songs in the player's queue. Their address is `encore://song/<id>`, worked out only when the song starts
 * (see SongResolver): the downloaded file if there is one, else a stream from the server's address right then.
 */
object SongItems {
    const val SCHEME = "encore"
    private const val COVER = "coverArt"
    private const val STARRED = "starred"
    private const val ALBUM_ID = "albumId"
    private const val SUFFIX = "suffix"

    fun uri(songId: String): Uri = Uri.Builder().scheme(SCHEME).authority("song").appendPath(songId).build()

    fun songId(uri: Uri): String? = if (uri.scheme == SCHEME && uri.authority == "song") uri.lastPathSegment else null

    fun toItem(s: Song, artworkUri: Uri? = null): MediaItem {
        val u = uri(s.id)
        return MediaItem.Builder()
            .setMediaId(s.id)
            .setUri(u)
            // URIs don't cross from controller to session; this does (PlaybackService puts it back)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(u).build())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(s.title)
                    .setArtist(s.artist)
                    .setAlbumTitle(s.album)
                    .setArtworkUri(artworkUri)
                    .setDurationMs(if (s.durationSec > 0) s.durationSec * 1000L else null)
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .setExtras(Bundle().apply {
                        putString(COVER, s.coverArt)
                        putBoolean(STARRED, s.starred)
                        putString(ALBUM_ID, s.albumId)
                        putString(SUFFIX, s.suffix)
                    })
                    .build(),
            )
            .build()
    }

    fun toSong(item: MediaItem): Song {
        val m = item.mediaMetadata
        val x = m.extras
        return Song(
            id = item.mediaId,
            title = m.title?.toString() ?: "",
            artist = m.artist?.toString() ?: "",
            album = m.albumTitle?.toString() ?: "",
            albumId = x?.getString(ALBUM_ID) ?: "",
            coverArt = x?.getString(COVER) ?: "",
            durationSec = ((m.durationMs ?: 0L) / 1000).toInt(),
            suffix = x?.getString(SUFFIX) ?: "",
            starred = x?.getBoolean(STARRED) ?: false,
        )
    }

    /** Puts back what a controller's items lose on the way to the session. */
    fun restore(item: MediaItem): MediaItem =
        if (item.localConfiguration != null) item
        else item.buildUpon().setUri(item.requestMetadata.mediaUri ?: uri(item.mediaId)).build()
}
