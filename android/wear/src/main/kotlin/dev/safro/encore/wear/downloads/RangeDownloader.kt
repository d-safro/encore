package dev.safro.encore.wear.downloads

import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Not enough room under the storage cap for this song. */
class OverCapException(val needBytes: Long) : IOException("Not enough room under the storage cap")

/** The server won't give this file (gone, or not allowed): trying again won't help. */
class PermanentDownloadException(message: String) : IOException(message)

/**
 * One file, resumable: what has arrived so far waits in `<file>.part`, and the next try asks for the rest
 * with an HTTP Range request. A server that ignores the range (200) starts the file over; one whose range
 * doesn't start where the part ends (a changed file) starts over too. Only a complete file is renamed into
 * place, so a file that exists is whole.
 */
class RangeDownloader(private val http: OkHttpClient) {
    /**
     * [room]: may this many more bytes be written? (the storage cap). [progress]: bytes so far, and the total
     * (-1 if the server doesn't say; transcodes often don't). [stopped]: give up now, keeping the part.
     * Returns the file's size.
     */
    fun download(
        url: HttpUrl,
        dest: File,
        room: (Long) -> Boolean = { true },
        progress: (Long, Long) -> Unit = { _, _ -> },
        stopped: () -> Boolean = { false },
    ): Long {
        if (dest.exists()) return dest.length()
        dest.parentFile?.mkdirs()
        val part = partFile(dest)
        repeat(2) { attempt ->
            val have = if (part.exists()) part.length() else 0L
            val req = Request.Builder().url(url).apply { if (have > 0) header("Range", "bytes=$have-") }.build()
            http.newCall(req).execute().use { res ->
                when (res.code) {
                    206 -> {
                        val range = ContentRange.parse(res.header("Content-Range"))
                        if (range == null || range.start != have) { // not the rest of what we have: start over
                            part.delete()
                            if (attempt == 0) return@repeat else throw IOException("Server sent the wrong part")
                        }
                        val total = range.total.takeIf { it > 0 } ?: (res.body.contentLength().takeIf { it >= 0 }?.plus(have) ?: -1)
                        write(res.body.byteStream(), part, append = true, have, total, room, progress, stopped)
                    }
                    200 -> {
                        val total = res.body.contentLength()
                        write(res.body.byteStream(), part, append = false, 0, total, room, progress, stopped)
                    }
                    416 -> { // nothing after what we have: complete if the server's size says so
                        val total = ContentRange.parse(res.header("Content-Range"))?.total ?: -1
                        if (have > 0 && total == have) Unit else {
                            part.delete()
                            if (attempt == 0) return@repeat else throw IOException("Server refused the range")
                        }
                    }
                    401, 403, 404, 410 -> throw PermanentDownloadException("Server answered ${res.code}")
                    else -> throw IOException("Server answered ${res.code}")
                }
            }
            if (!part.renameTo(dest)) throw IOException("Couldn't save the file")
            return dest.length()
        }
        throw IOException("Download didn't finish")
    }

    private fun write(
        input: java.io.InputStream,
        part: File,
        append: Boolean,
        startAt: Long,
        total: Long,
        room: (Long) -> Boolean,
        progress: (Long, Long) -> Unit,
        stopped: () -> Boolean,
    ) {
        if (total > 0 && !room(total - startAt)) throw OverCapException(total - startAt)
        var done = startAt
        var unchecked = 0L
        FileOutputStream(part, append).use { out ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                if (stopped()) throw IOException("Stopped")
                val n = input.read(buf)
                if (n < 0) break
                // size not known up front: check the cap as it grows
                if (total <= 0) {
                    unchecked += n
                    if (unchecked >= CHECK_EVERY) {
                        if (!room(unchecked)) throw OverCapException(unchecked)
                        unchecked = 0
                    }
                }
                out.write(buf, 0, n)
                done += n
                progress(done, total)
            }
            out.fd.sync()
        }
        if (total > 0 && done < total) throw IOException("Connection closed early") // the part stays: resume next time
    }

    companion object {
        private const val CHECK_EVERY = 1L shl 20

        fun partFile(dest: File) = File(dest.path + ".part")
    }
}

/** `Content-Range: bytes 100-999/1000` (total -1 when `*`). */
data class ContentRange(val start: Long, val end: Long, val total: Long) {
    companion object {
        private val re = Regex("""bytes\s+(?:(\d+)-(\d+)|\*)/(\d+|\*)""")

        fun parse(h: String?): ContentRange? {
            val m = re.find(h ?: return null) ?: return null
            val total = m.groupValues[3].toLongOrNull() ?: -1
            val start = m.groupValues[1].toLongOrNull() ?: -1
            val end = m.groupValues[2].toLongOrNull() ?: -1
            return ContentRange(start, end, total)
        }
    }
}
