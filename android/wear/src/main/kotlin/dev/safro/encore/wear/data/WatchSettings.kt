package dev.safro.encore.wear.data

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Watch settings. Where the phone has the same setting, the name and meaning are the phone's. */
data class Settings(
    val wifiFormat: String = "raw",
    val wifiBitrate: Int = 0,
    val mobileFormat: String = "opus",
    val mobileBitrate: Int = 128,
    val downloadFormat: String = "opus",
    val downloadBitrate: Int = 160,
    val downloadWifiOnly: Boolean = true,
    val scrobble: Boolean = true,
    /** Most the downloads may take on the watch, in MB. */
    val downloadCapMb: Int = 2048,
    /** The playlist the tile plays with one tap ("" none yet). */
    val tilePlaylistId: String = "",
    val tilePlaylistName: String = "",
)

class SettingsStore(private val prefs: SharedPreferences) {
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings.asStateFlow()
    val current get() = _settings.value

    private fun read(): Settings {
        val d = Settings()
        return Settings(
            wifiFormat = prefs.getString("wifiFormat", d.wifiFormat) ?: d.wifiFormat,
            wifiBitrate = prefs.getInt("wifiBitrate", d.wifiBitrate),
            mobileFormat = prefs.getString("mobileFormat", d.mobileFormat) ?: d.mobileFormat,
            mobileBitrate = prefs.getInt("mobileBitrate", d.mobileBitrate),
            downloadFormat = prefs.getString("downloadFormat", d.downloadFormat) ?: d.downloadFormat,
            downloadBitrate = prefs.getInt("downloadBitrate", d.downloadBitrate),
            downloadWifiOnly = prefs.getBoolean("downloadWifiOnly", d.downloadWifiOnly),
            scrobble = prefs.getBoolean("scrobble", d.scrobble),
            downloadCapMb = prefs.getInt("downloadCapMb", d.downloadCapMb),
            tilePlaylistId = prefs.getString("tilePlaylistId", "") ?: "",
            tilePlaylistName = prefs.getString("tilePlaylistName", "") ?: "",
        )
    }

    fun update(change: (Settings) -> Settings) {
        val s = change(current)
        prefs.edit {
            putString("wifiFormat", s.wifiFormat)
            putInt("wifiBitrate", s.wifiBitrate)
            putString("mobileFormat", s.mobileFormat)
            putInt("mobileBitrate", s.mobileBitrate)
            putString("downloadFormat", s.downloadFormat)
            putInt("downloadBitrate", s.downloadBitrate)
            putBoolean("downloadWifiOnly", s.downloadWifiOnly)
            putBoolean("scrobble", s.scrobble)
            putInt("downloadCapMb", s.downloadCapMb)
            putString("tilePlaylistId", s.tilePlaylistId)
            putString("tilePlaylistName", s.tilePlaylistName)
        }
        _settings.value = s
    }

    companion object {
        /** The storage caps offered in Settings, in MB. */
        val CAPS_MB = listOf(512, 1024, 2048, 4096, 8192)
    }
}
