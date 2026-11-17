package dev.safro.encore

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/**
 * Keeps Encore running while songs download with the app in the background, with a quiet "Downloading"
 * notification (like Spotify). The downloading itself happens in the app (DownloadsNotifier); this only stops
 * Android from pausing the app meanwhile. Started and stopped from Dart over the "encore/system" channel.
 */
class DownloadService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val count = intent?.getIntExtra("count", 0) ?: 0
        if (count <= 0) {
            stop()
            return START_NOT_STICKY
        }
        try {
            ServiceCompat.startForeground(
                this, ID, notification(count),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
            )
        } catch (e: Exception) {
            stopSelf() // not allowed right now (e.g. started from the background): downloads go on while the app is open
        }
        return START_NOT_STICKY // after Android stops it, the app carries on from where it stopped when opened
    }

    // Android 15 allows data-sync work 6 hours a day: then the app pauses, and carries on when opened
    override fun onTimeout(startId: Int, fgsType: Int) = stop()

    private fun stop() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notification(count: Int): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shown while songs download"
                    setShowBadge(false)
                })
        }
        val open = PendingIntent.getActivity(
            this, 0, packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_encore)
            .setContentTitle("Downloading")
            .setContentText(if (count == 1) "1 song to go" else "$count songs to go")
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(open)
            .build()
    }

    companion object {
        private const val ID = 4207
        private const val CHANNEL = "dev.safro.encore.downloads"
        private var last = -1

        /** [count] songs still to download; 0 stops the service. */
        fun update(context: Context, count: Int) {
            if (count == last) return
            val wasRunning = last > 0
            last = count
            val i = Intent(context, DownloadService::class.java).putExtra("count", count)
            try {
                if (count > 0 && !wasRunning) {
                    androidx.core.content.ContextCompat.startForegroundService(context, i)
                } else if (count > 0 || wasRunning) {
                    context.startService(i) // update the count, or stop
                }
            } catch (e: Exception) {
                last = -1 // not allowed from the background: try again next time
            }
        }
    }
}
