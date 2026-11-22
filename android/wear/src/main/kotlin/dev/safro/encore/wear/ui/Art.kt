package dev.safro.encore.wear.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.safro.encore.wear.subsonic.SubsonicClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/** Album covers for the screens: from memory, else the watch's cache, else the server. */
class ArtLoader(context: Context, private val client: SubsonicClient, private val http: OkHttpClient) {
    private val memory = LruCache<String, ImageBitmap>(40)
    private val dir = File(context.cacheDir, "art")

    fun cached(coverArt: String, size: Int): ImageBitmap? = memory.get("$coverArt@$size")

    suspend fun load(coverArt: String, size: Int): ImageBitmap? {
        val key = "$coverArt@$size"
        memory.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                dir.mkdirs()
                val f = File(dir, key.replace(Regex("[^A-Za-z0-9@._-]"), "_") + ".img")
                if (!f.exists()) {
                    val url = client.coverArtUrl(coverArt, size)
                    http.newCall(Request.Builder().url(url).build()).execute().use { res ->
                        if (!res.isSuccessful) return@runCatching null
                        val tmp = File(dir, f.name + ".tmp")
                        tmp.outputStream().use { res.body.byteStream().copyTo(it) }
                        tmp.renameTo(f)
                    }
                }
                decode(f.readBytes(), size)?.asImageBitmap()?.also { memory.put(key, it) }
            }.getOrNull()
        }
    }

    companion object {
        /** Decodes at no more than about [size] px across (covers can be huge; a watch has little memory). */
        fun decode(bytes: ByteArray, size: Int): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= size && bounds.outHeight / (sample * 2) >= size) sample *= 2
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
}

/** Where screens get covers from (screenshot tests give made-up ones). */
fun interface ArtSource {
    @Composable
    fun art(coverArt: String, size: Int): ImageBitmap?
}

val LocalArt = staticCompositionLocalOf<ArtSource> { ArtSource { _, _ -> null } }

fun loaderArtSource(loader: ArtLoader) = ArtSource { coverArt, size ->
    val bmp by produceState(loader.cached(coverArt, size), coverArt, size) {
        if (coverArt.isNotEmpty() && value == null) value = loader.load(coverArt, size)
    }
    bmp
}
