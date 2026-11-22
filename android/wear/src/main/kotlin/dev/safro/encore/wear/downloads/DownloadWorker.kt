package dev.safro.encore.wear.downloads

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.safro.encore.wear.Graph
import dev.safro.encore.wear.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Downloads one album or playlist to the watch, song by song. A song cut off halfway resumes where it
 * stopped on the next try (WorkManager retries with back-off, on Wi-Fi only unless you allowed mobile data).
 */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val g = Graph.get(applicationContext)
        val key = inputData.getString(KEY) ?: return Result.failure()
        val store = g.downloads
        val c = store.collection(key) ?: return Result.success() // removed meanwhile
        val s = g.settings.current
        val capBytes = s.downloadCapMb.toLong() shl 20
        store.setState(key, DownloadState.DOWNLOADING)
        runCatching { setForeground(foregroundInfo(c.name)) }
        g.networks.acquire()
        try {
            val downloader = RangeDownloader(g.http)
            val pending = store.current.songsOf(c).filter { !it.done }
            var failed = 0
            for ((i, kept) in pending.withIndex()) {
                if (isStopped) return Result.retry()
                if (store.collection(key) == null) return Result.success()
                val name = kept.file.ifEmpty { store.fileName(kept.song, s.downloadFormat) }
                store.songStarted(kept.song.id, name)
                val dest = File(store.musicDir, name)
                try {
                    val url = g.client.downloadUrl(kept.song.id, s.downloadFormat, s.downloadBitrate)
                    val bytes = withContext(Dispatchers.IO) {
                        downloader.download(
                            url, dest,
                            room = { more -> store.usedBytes() + more <= capBytes },
                            stopped = { isStopped },
                        )
                    }
                    store.songDone(kept.song.id, name, bytes)
                    setProgress(workDataOf(DONE to i + 1, TOTAL to pending.size))
                } catch (e: OverCapException) {
                    store.setState(key, DownloadState.FULL, "Storage cap reached")
                    return Result.failure()
                } catch (e: PermanentDownloadException) {
                    failed++ // this one song: skip it, keep the rest
                }
            }
            val ok = store.collection(key)?.let { store.current.doneCount(it) == it.songIds.size } ?: true
            store.setState(key, if (ok) DownloadState.DONE else DownloadState.FAILED, if (failed > 0) "$failed songs couldn't be downloaded" else "")
            return Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            store.setState(key, DownloadState.QUEUED, "Waiting for the network")
            return if (runAttemptCount < 20) Result.retry() else Result.failure()
        } catch (e: Exception) {
            store.setState(key, DownloadState.FAILED, e.message ?: "Download failed")
            return Result.failure()
        } finally {
            g.networks.release()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo("Encore")

    private fun foregroundInfo(name: String): ForegroundInfo {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW))
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_encore)
            .setContentTitle("Downloading")
            .setContentText(name)
            .setOngoing(true)
            .setSilent(true)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    companion object {
        const val KEY = "collection"
        const val DONE = "done"
        const val TOTAL = "total"
        private const val CHANNEL = "downloads"
        private const val NOTIFICATION_ID = 2001

        fun workName(key: String) = "download:$key"

        fun enqueue(context: Context, key: String, wifiOnly: Boolean) {
            val req = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(workDataOf(KEY to key))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                        .setRequiresStorageNotLow(true)
                        .build(),
                )
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag("download")
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(workName(key), ExistingWorkPolicy.KEEP, req)
        }

        fun cancel(context: Context, key: String) = WorkManager.getInstance(context).cancelUniqueWork(workName(key))
    }
}
