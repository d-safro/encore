package dev.safro.encore

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.view.KeyEvent
import android.widget.RemoteViews
import es.antonborri.home_widget.HomeWidgetProvider
import java.io.File

/** Home-screen widget: cover, song, artist, previous / play-pause / next. Data comes from the app (home_widget). */
class EncoreWidget : HomeWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray, widgetData: SharedPreferences) {
        for (id in appWidgetIds) {
            val v = RemoteViews(context.packageName, R.layout.encore_widget)
            v.setTextViewText(R.id.widget_title, widgetData.getString("title", null) ?: "Encore")
            v.setTextViewText(R.id.widget_artist, widgetData.getString("artist", null) ?: "Tap to open")
            val cover = widgetData.getString("cover", null)
            val bmp = if (cover != null && File(cover).exists()) coverBitmap(cover) else null
            if (bmp != null) v.setImageViewBitmap(R.id.widget_cover, bmp) else v.setImageViewResource(R.id.widget_cover, R.drawable.ic_widget_note)
            val playing = widgetData.getBoolean("playing", false)
            v.setImageViewResource(R.id.widget_play, if (playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
            v.setOnClickPendingIntent(R.id.widget_prev, mediaButton(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
            v.setOnClickPendingIntent(R.id.widget_play, mediaButton(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
            v.setOnClickPendingIntent(R.id.widget_next, mediaButton(context, KeyEvent.KEYCODE_MEDIA_NEXT))
            val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
            v.setOnClickPendingIntent(R.id.widget_root,
                PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            appWidgetManager.updateAppWidget(id, v)
        }
    }

    /**
     * The cover, scaled down to at most ~512 px. A widget's pictures must fit in a few screens' worth of memory,
     * and an audiobook's cover straight from Audiobookshelf can be thousands of pixels across: at full size the
     * update throws (and takes the app down with it).
     */
    private fun coverBitmap(path: String): android.graphics.Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 512) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** A media-button press delivered to the player's receiver (works even when the app isn't open). */
    private fun mediaButton(context: Context, key: Int): PendingIntent {
        val intent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            component = ComponentName(context, "com.ryanheise.audioservice.MediaButtonReceiver")
            putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, key))
        }
        return PendingIntent.getBroadcast(context, key, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
