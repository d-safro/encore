package dev.safro.encore.wear

import android.content.Context
import android.os.SystemClock
import dev.safro.encore.wear.data.AccountStore
import dev.safro.encore.wear.data.Hearts
import dev.safro.encore.wear.data.NowPlayingStore
import dev.safro.encore.wear.data.SettingsStore
import dev.safro.encore.wear.downloads.DownloadStore
import dev.safro.encore.wear.net.Networks
import dev.safro.encore.wear.net.ServerRouter
import dev.safro.encore.wear.phone.PhoneLink
import dev.safro.encore.wear.subsonic.SubsonicClient
import dev.safro.encore.wear.subsonic.SubsonicUrls
import dev.safro.encore.wear.ui.ArtLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** Everything the watch app shares between its screens, player, tile, complication and workers. */
class Graph private constructor(val app: Context) {
    val prefs = app.getSharedPreferences("encore", Context.MODE_PRIVATE)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val accounts = AccountStore(prefs)
    val settings = SettingsStore(prefs)
    val networks = Networks(app)

    val http: OkHttpClient = OkHttpClient.Builder()
        .socketFactory(networks.socketFactory)
        .dns(networks.dns)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val probeHttp = http.newBuilder().callTimeout(3, TimeUnit.SECONDS).build()

    val router = ServerRouter(
        account = { accounts.current },
        network = networks::kind,
        probe = { address, a ->
            withContext(Dispatchers.IO) {
                runCatching {
                    probeHttp.newCall(Request.Builder().url(SubsonicUrls.build(address, "ping", a)).build()).execute().use {
                        it.code == 200 && it.body.string().contains("\"ok\"")
                    }
                }.getOrDefault(false)
            }
        },
        clock = SystemClock::elapsedRealtime,
    )

    init {
        networks.onChange = { router.networkChanged() }
    }

    val client = SubsonicClient({ accounts.current }, router::base, http)
    val downloads = DownloadStore(File(app.filesDir, "downloads"))
    val hearts = Hearts(client, scope)
    val nowPlaying = NowPlayingStore(app, prefs)
    val phone = PhoneLink(app, scope)
    val art = ArtLoader(app, client, http)

    companion object {
        @Volatile private var instance: Graph? = null

        fun get(context: Context): Graph =
            instance ?: synchronized(this) { instance ?: Graph(context.applicationContext).also { instance = it } }
    }
}
