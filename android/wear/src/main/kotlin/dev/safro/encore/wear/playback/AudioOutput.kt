package dev.safro.encore.wear.playback

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager

/**
 * Where the watch's sound goes. Music on a watch needs headphones: with none connected, Encore asks for them
 * with Wear OS's output switcher (or, on watches without it, the Bluetooth screen that closes once connected).
 */
object AudioOutput {
    // Wear OS's media output switcher, and the Bluetooth picker as a fallback (as Media3 / Horologist use)
    private const val SWITCHER = "com.android.settings.panel.action.MEDIA_OUTPUT"
    private const val SWITCHER_PACKAGE = "com.android.settings.panel.extra.PACKAGE_NAME"
    private const val BT_CONNECTION_ONLY = "EXTRA_CONNECTION_ONLY"
    private const val BT_CLOSE_ON_CONNECT = "EXTRA_CLOSE_ON_CONNECT"
    private const val BT_FILTER_TYPE = "android.bluetooth.devicepicker.extra.FILTER_TYPE"
    private const val BT_FILTER_AUDIO = 1

    private val headphoneTypes = setOf(
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_BLE_BROADCAST,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_USB_HEADSET,
    )

    /** The headphones (or speaker) music plays on now, or null if none is connected. */
    fun headphones(context: Context): String? {
        val am = context.getSystemService(AudioManager::class.java) ?: return null
        val d = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type in headphoneTypes } ?: return null
        return d.productName?.toString()?.ifBlank { null } ?: "Headphones"
    }

    /** Opens the output switcher. Returns false if the watch has neither it nor a Bluetooth screen. */
    fun openSwitcher(context: Context): Boolean {
        val switcher = Intent(SWITCHER)
            .putExtra(SWITCHER_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (isSystemActivity(context, switcher) && start(context, switcher)) return true
        val bt = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
            .putExtra(BT_CONNECTION_ONLY, true)
            .putExtra(BT_CLOSE_ON_CONNECT, true)
            .putExtra(BT_FILTER_TYPE, BT_FILTER_AUDIO)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        return start(context, bt)
    }

    private fun isSystemActivity(context: Context, intent: Intent): Boolean {
        val info = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_SYSTEM_ONLY)
        return info.any { (it.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 }
    }

    private fun start(context: Context, intent: Intent) = try {
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }
}
