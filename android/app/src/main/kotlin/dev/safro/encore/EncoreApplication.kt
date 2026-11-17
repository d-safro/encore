package dev.safro.encore

import android.util.Log
import androidx.work.WorkManager
import io.flutter.app.FlutterApplication

/**
 * Song downloads are Encore's own since 1.7.2 (DownloadsNotifier in Dart), but Android's job scheduler still
 * held downloads queued with the old download system (background_downloader) by 1.7.1 and older, and starts
 * them again whenever the app process starts: hundreds at once, over the network instead of a USB cable, each
 * one a song the server converts. They're called off here, as the process starts and before any can run; the
 * Dart side then moves those songs to Encore's own queue (adoptOldDownloads).
 *
 * Making songs smaller still uses that system while the app runs. Those jobs are called off too when the app
 * starts again: the app offers the rest again (Downloads › Shrink).
 */
class EncoreApplication : FlutterApplication() {
    override fun onCreate() {
        super.onCreate()
        try {
            WorkManager.getInstance(this).cancelAllWorkByTag("BackgroundDownloader")
            Log.i("Encore", "Called off download jobs left from an earlier run")
        } catch (e: Exception) {
            Log.w("Encore", "Could not call off old download jobs: $e")
        }
    }
}
