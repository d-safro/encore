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

    // Library > On this device: the "Music and audio" permission, and files opened with Encore
    private var audioAnswer: MethodChannel.Result? = null
    private var filesChannel: MethodChannel? = null
    private var pendingOpen: String? = null

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val ok = grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (requestCode == 4711) {
            micAnswer?.success(ok)
            micAnswer = null
        }
        if (requestCode == 4712) {
            audioAnswer?.success(ok)
            audioAnswer = null
        }
    }

    /** The music file this activity was opened with ("Open with Encore"), if any. */
    private fun openedUri(i: Intent?): String? =
        if (i?.action == Intent.ACTION_VIEW && i.type?.startsWith("audio/") != false) i?.data?.toString() else null

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openedUri(intent)?.let { filesChannel?.invokeMethod("open", listOf(it)) }
    }

    private fun audioPermission(): String =
        if (Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_AUDIO
        else android.Manifest.permission.READ_EXTERNAL_STORAGE

    /** Every music file MediaStore knows, with its tags; one cover per album saved into [covers]. */
    private fun mediaStoreAudio(covers: File): List<Map<String, Any?>> {
        val out = mutableListOf<Map<String, Any?>>()
        val base = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val cols = arrayOf(
            android.provider.MediaStore.Audio.Media._ID, android.provider.MediaStore.Audio.Media.TITLE,
            android.provider.MediaStore.Audio.Media.ARTIST, android.provider.MediaStore.Audio.Media.ALBUM,
            android.provider.MediaStore.Audio.Media.ALBUM_ID, android.provider.MediaStore.Audio.Media.TRACK,
            android.provider.MediaStore.Audio.Media.YEAR, android.provider.MediaStore.Audio.Media.DURATION,
            android.provider.MediaStore.Audio.Media.DATE_MODIFIED, android.provider.MediaStore.Audio.Media.DISPLAY_NAME,
        )
        val tried = HashMap<Long, String?>()
        contentResolver.query(base, cols, "${android.provider.MediaStore.Audio.Media.IS_MUSIC} != 0", null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val uri = android.content.ContentUris.withAppendedId(base, id)
                val albumId = c.getLong(4)
                val cover = tried.getOrPut(albumId) { albumCover(uri, albumId, covers) }
                out.add(mapOf(
                    "uri" to uri.toString(), "title" to c.getString(1), "artist" to c.getString(2),
                    "album" to c.getString(3), "track" to c.getInt(5), "year" to c.getInt(6),
                    "duration" to c.getLong(7), "mtime" to c.getLong(8) * 1000, "name" to c.getString(9),
                    "cover" to cover,
                ))
            }
        }
        return out
    }

    /** An album's picture (from its first song), kept as a file; a "none" marker when it has none. */
    private fun albumCover(uri: Uri, albumId: Long, covers: File): String? {
        val f = File(covers, "ma-$albumId.jpg")
        val none = File(covers, "ma-$albumId.none")
        if (f.exists()) return f.path
        if (none.exists() || Build.VERSION.SDK_INT < 29) return null
        return try {
            val bmp = contentResolver.loadThumbnail(uri, android.util.Size(512, 512), null)
            covers.mkdirs()
            f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }
            f.path
        } catch (_: Exception) {
            try { none.createNewFile() } catch (_: Exception) {}
            null
        }
    }

    /** Tags of one file opened with Encore (it may not be in MediaStore). */
    private fun probeAudio(uri: Uri, covers: File): Map<String, Any?> {
        val r = android.media.MediaMetadataRetriever()
        try {
            r.setDataSource(this, uri)
            fun tag(k: Int) = r.extractMetadata(k)
            var cover: String? = null
            r.embeddedPicture?.let { bytes ->
                val f = File(covers, "open-${java.security.MessageDigest.getInstance("SHA-1").digest(bytes).take(10).joinToString("") { "%02x".format(it) }}.jpg")
                covers.mkdirs()
                if (!f.exists()) f.writeBytes(bytes)
                cover = f.path
            }
            var name: String? = null
            try {
                contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) name = c.getString(0)
                }
            } catch (_: Exception) {}
            return mapOf(
                "title" to tag(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE),
                "artist" to tag(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST),
                "album" to tag(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM),
                "track" to tag(android.media.MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.substringBefore('/')?.trim()?.toIntOrNull(),
                "year" to tag(android.media.MediaMetadataRetriever.METADATA_KEY_YEAR)?.take(4)?.toIntOrNull(),
                "duration" to tag(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
                "name" to (name ?: uri.lastPathSegment),
                "cover" to cover,
            )
        } finally {
            r.release()
        }
    }
    private var multicast: android.net.wifi.WifiManager.MulticastLock? = null
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        // App updates and links: which APK fits this phone, permission to install, the installer, a browser.
        // files opened with Encore: the one it was started with, and later ones (onNewIntent)
        pendingOpen = openedUri(intent)
        filesChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "encore/files").apply {
            setMethodCallHandler { call, result ->
                if (call.method == "initial") {
                    result.success(pendingOpen?.let { listOf(it) } ?: emptyList<String>())
                    pendingOpen = null
                } else result.notImplemented()
            }
        }
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "encore/system").setMethodCallHandler { call, result ->
            try {
                when (call.method) {
                    "audioPermission" -> {
                        val p = audioPermission()
                        if (checkSelfPermission(p) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            result.success(true)
                        } else if (call.argument<Boolean>("ask") != true) {
                            result.success(false)
                        } else {
                            audioAnswer?.success(false)
                            audioAnswer = result
                            requestPermissions(arrayOf(p), 4712)
                        }
                    }
                    "mediaStoreAudio" -> {   // can take a while on a big phone: off the main thread
                        val covers = File(call.argument<String>("covers")!!)
                        Thread {
                            try {
                                val list = mediaStoreAudio(covers)
                                runOnUiThread { result.success(list) }
                            } catch (e: Exception) {
                                runOnUiThread { result.error("mediastore", e.message, null) }
                            }
                        }.start()
                    }
                    "probeAudio" -> {
                        val uri = Uri.parse(call.argument<String>("uri")!!)
                        val covers = File(call.argument<String>("covers")!!)
                        Thread {
                            try {
                                val tags = probeAudio(uri, covers)
                                runOnUiThread { result.success(tags) }
                            } catch (e: Exception) {
                                runOnUiThread { result.error("probe", e.message, null) }
                            }
                        }.start()
                    }
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
                    "addEvent" -> {   // a concert into your calendar: its "new event" screen, filled in (no permission)
                        startActivity(Intent(Intent.ACTION_INSERT)
                            .setData(android.provider.CalendarContract.Events.CONTENT_URI)
                            .putExtra(android.provider.CalendarContract.Events.TITLE, call.argument<String>("title"))
                            .putExtra(android.provider.CalendarContract.Events.EVENT_LOCATION, call.argument<String>("location"))
                            .putExtra(android.provider.CalendarContract.Events.DESCRIPTION, call.argument<String>("description"))
                            .putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, call.argument<Number>("begin")?.toLong())
                            .putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, call.argument<Number>("end")?.toLong())
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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
        // A paired Wear OS watch signs in to the same server (data/wear_link.dart)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "encore/wear").setMethodCallHandler { call, result ->
            try {
                when (call.method) {
                    "account" -> WearAccount.share(
                        applicationContext,
                        call.argument<String>("server") ?: "",
                        call.argument<String>("user") ?: "",
                        call.argument<String>("pass") ?: "",
                        call.argument<String>("localAddress") ?: "",
                    )
                    "signedOut" -> WearAccount.signedOut(applicationContext)
                    else -> return@setMethodCallHandler result.notImplemented()
                }
                result.success(true)
            } catch (e: Exception) {
                result.error("wear", e.message, null)   // no Google Play services: no watch to tell
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
