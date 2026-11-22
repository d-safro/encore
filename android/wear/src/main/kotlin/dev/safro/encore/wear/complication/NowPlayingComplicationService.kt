package dev.safro.encore.wear.complication

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import dev.safro.encore.wear.Graph
import dev.safro.encore.wear.MainActivity
import dev.safro.encore.wear.R
import dev.safro.encore.wear.data.NowPlayingStore

/** A watch-face complication: the song playing now (short: its title; long: title and artist). */
class NowPlayingComplicationService : SuspendingComplicationDataSourceService() {
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        build(this, request.complicationType, Graph.get(this).nowPlaying.info.value)

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(this, type, NowPlayingStore.Info("Blue in Green", "Miles Davis", playing = true, onPhone = false))

    companion object {
        /** The words shown, apart from Android types so tests can check them. */
        fun words(info: NowPlayingStore.Info?): Pair<String, String> = when {
            info == null -> "Encore" to "Nothing playing"
            else -> info.title.ifEmpty { "Encore" } to info.artist
        }

        fun build(context: Context, type: ComplicationType, info: NowPlayingStore.Info?): ComplicationData? {
            val (title, artist) = words(info)
            val image = MonochromaticImage.Builder(Icon.createWithResource(context, R.drawable.ic_stat_encore)).build()
            val tap = PendingIntent.getActivity(
                context, 2,
                Intent(context, MainActivity::class.java).putExtra(
                    MainActivity.EXTRA_OPEN,
                    if (info?.onPhone == true) MainActivity.OPEN_REMOTE else MainActivity.OPEN_PLAYER,
                ),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val description = PlainComplicationText.Builder(if (info == null) "Encore: nothing playing" else "Playing $title by $artist").build()
            return when (type) {
                ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(PlainComplicationText.Builder(title).build(), description)
                    .setMonochromaticImage(image)
                    .setTapAction(tap)
                    .build()
                ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(PlainComplicationText.Builder(title).build(), description)
                    .setTitle(PlainComplicationText.Builder(artist.ifEmpty { "Encore" }).build())
                    .setMonochromaticImage(image)
                    .setTapAction(tap)
                    .build()
                else -> null
            }
        }
    }
}
