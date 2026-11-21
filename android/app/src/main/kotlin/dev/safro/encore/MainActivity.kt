package dev.safro.encore

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRouter
import android.media.MediaRouter2
import android.os.Build
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.ryanheise.audioservice.AudioServiceActivity
import java.io.File
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

/** The player screen's sound panel: system media volume, where the sound plays, Android's output switcher. */
class MainActivity : AudioServiceActivity() {
    // "What's playing?": a short recording for song identification (your server, your library)
    private var recorder: android.media.MediaRecorder? = null
    private var micAnswer: MethodChannel.Result? = null

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 4711) {
            micAnswer?.success(grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED)
            micAnswer = null
        }
    }
    private var multicast: android.net.wifi.WifiManager.MulticastLock? = null
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        // App updates and links: which APK fits this phone, permission to install, the installer, a browser.
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "encore/system").setMethodCallHandler { call, result ->
            try {
                when (call.method) {
                    "abis" -> result.success(Build.SUPPORTED_ABIS.toList())
                    "model" -> result.success(Build.MODEL)   // this phone's name on your other devices
                    "icon" -> result.success(pendingIcon ?: icons.firstOrNull { iconOn(it) } ?: icons.first())
                    "setIcon" -> {
                        // applied when you leave the app: switching off the icon Encore was opened from ends its window
                        pendingIcon = call.argument<String>("name") ?: icons.first()
                        result.success(true)
                    }
                    "multicastLock" -> {   // finding Cast speakers (mDNS) needs multicast packets
                        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
                        if (call.argument<Boolean>("on") == true) {
                            if (multicast == null) multicast = wifi.createMulticastLock("encore-cast").apply { setReferenceCounted(false) }
                            multicast?.acquire()
                        } else multicast?.release()
                        result.success(true)
                    }
                    "downloading" -> {   // songs still to download (0: done): keeps the app running meanwhile
                        DownloadService.update(applicationContext, call.argument<Int>("count") ?: 0)
                        result.success(true)
                    }
                    "canInstall" -> result.success(Build.VERSION.SDK_INT < 26 || packageManager.canRequestPackageInstalls())
                    "allowInstalls" -> {
                        startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        result.success(true)
                    }
                    "install" -> {
                        val file = File(call.argument<String>("path")!!)
                        val uri = FileProvider.getUriForFile(this, "$packageName.updates", file)
                        startActivity(Intent(Intent.ACTION_VIEW)
                            .setDataAndType(uri, "application/vnd.android.package-archive")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
                        result.success(true)
                    }
                    "micPermission" -> {
                        val p = android.Manifest.permission.RECORD_AUDIO
                        if (checkSelfPermission(p) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            result.success(true)
                        } else {
                            micAnswer?.success(false)
                            micAnswer = result
                            requestPermissions(arrayOf(p), 4711)
                        }
                    }
                    "recordStart" -> {
                        recorder?.release()
                        val r = if (Build.VERSION.SDK_INT >= 31) android.media.MediaRecorder(this) else @Suppress("DEPRECATION") android.media.MediaRecorder()
                        r.setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                        r.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
                        r.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
                        r.setAudioChannels(1)
                        r.setAudioSamplingRate(22050)
                        r.setAudioEncodingBitRate(64000)
                        r.setOutputFile(call.argument<String>("path")!!)
                        r.prepare()
                        r.start()
                        recorder = r
                        result.success(true)
                    }
                    "recordStop" -> {
                        try { recorder?.stop() } catch (_: Exception) {}
                        recorder?.release()
                        recorder = null
                        result.success(true)
                    }
                    "openUrl" -> {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(call.argument<String>("url")!!))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        result.success(true)
                    }
                    else -> result.notImplemented()
                }
            } catch (e: Exception) {
                result.error("system", e.message, null)
            }
        }
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "encore/audio").setMethodCallHandler { call, result ->
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            try {
                when (call.method) {
                    "volume" -> result.success(mapOf(
                        "current" to am.getStreamVolume(AudioManager.STREAM_MUSIC),
                        "max" to am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)))
                    "setVolume" -> {
                        am.setStreamVolume(AudioManager.STREAM_MUSIC, (call.argument<Int>("value") ?: 0), 0)
                        result.success(true)
                    }
                    "output" -> result.success(currentOutput(am))
                    "devices" -> result.success(am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                        .filter { it.type != AudioDeviceInfo.TYPE_TELEPHONY && it.type != AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                        .map { mapOf("name" to label(it), "type" to kind(it.type)) }
                        .distinctBy { it["name"] })
                    "showOutputSwitcher" -> result.success(showOutputSwitcher())
                    "openSettings" -> {
                        val action = when (call.argument<String>("which")) {
                            "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS   // mono audio, left/right balance
                            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
                            else -> Settings.ACTION_SOUND_SETTINGS
                        }
                        startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        result.success(true)
                    }
                    else -> result.notImplemented()
                }
            } catch (e: Exception) {
                result.error("audio", e.message, null)
            }
        }
    }

    private val icons = listOf("IconScript", "IconLine", "IconOriginal")
    private var pendingIcon: String? = null

    override fun onStop() {
        super.onStop()
        val want = pendingIcon ?: return
        pendingIcon = null
        if (iconOn(want)) return
        setIcon(want, true)   // the chosen one on first, so there's always an icon
        for (n in icons) if (n != want) setIcon(n, false)
    }

    private fun iconOn(name: String): Boolean {
        val st = packageManager.getComponentEnabledSetting(android.content.ComponentName(packageName, "$packageName.$name"))
        return st == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
            (st == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && name == icons.first())
    }

    private fun setIcon(name: String, on: Boolean) = packageManager.setComponentEnabledSetting(
        android.content.ComponentName(packageName, "$packageName.$name"),
        if (on) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        android.content.pm.PackageManager.DONT_KILL_APP,
    )

    private fun currentOutput(am: AudioManager): Map<String, String> {
        @Suppress("DEPRECATION")
        val route = (getSystemService(Context.MEDIA_ROUTER_SERVICE) as MediaRouter)
            .getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO)
        val outs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val bt = outs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET }
        val wired = outs.firstOrNull { it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_HEADSET }
        return when {
            bt != null && route.deviceType == MediaRouter.RouteInfo.DEVICE_TYPE_BLUETOOTH -> mapOf("name" to label(bt), "type" to "bluetooth")
            wired != null -> mapOf("name" to label(wired), "type" to "headphones")
            else -> mapOf("name" to "This phone", "type" to "speaker")
        }
    }

    private fun showOutputSwitcher(): Boolean {
        if (Build.VERSION.SDK_INT >= 34 && MediaRouter2.getInstance(this).showSystemOutputSwitcher()) return true
        return try {    // older Android: System UI's media output dialog
            sendBroadcast(Intent("com.android.systemui.action.LAUNCH_MEDIA_OUTPUT_DIALOG")
                .setPackage("com.android.systemui").putExtra("package_name", packageName))
            true
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            false
        }
    }

    private fun label(d: AudioDeviceInfo): String {
        val n = d.productName?.toString() ?: ""
        return if (d.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER || n.isBlank() || n == Build.MODEL) "This phone" else n
    }

    private fun kind(t: Int): String = when (t) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER -> "bluetooth"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_USB_HEADSET -> "headphones"
        AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC -> "tv"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "speaker"
        else -> "other"
    }
}
