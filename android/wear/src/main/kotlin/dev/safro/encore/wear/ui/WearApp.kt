package dev.safro.encore.wear.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import android.app.RemoteInput
import androidx.navigation.NavHostController
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import androidx.wear.input.RemoteInputIntentHelper
import dev.safro.encore.wear.BuildConfig
import dev.safro.encore.wear.Graph
import dev.safro.encore.wear.data.Account
import dev.safro.encore.wear.data.SettingsStore
import dev.safro.encore.wear.downloads.DownloadState
import dev.safro.encore.wear.downloads.DownloadWorker
import dev.safro.encore.wear.playback.AudioOutput
import dev.safro.encore.wear.playback.PlayerConnection
import dev.safro.encore.wear.playback.Volume
import dev.safro.encore.wear.subsonic.Song
import dev.safro.encore.wear.subsonic.SubsonicClient
import dev.safro.encore.wear.subsonic.SubsonicUrls
import dev.safro.encore.wearlink.BrowseItem
import dev.safro.encore.wearlink.Command
import dev.safro.encore.wearlink.QueueItem
import dev.safro.encore.wearlink.Reply
import kotlinx.coroutines.launch

/** The app: every screen, wired to the watch's player, the server and the phone. */
@Composable
fun WearApp(graph: Graph, player: PlayerConnection, volume: Volume, ambient: Boolean, openOnStart: String?) {
    val nav = rememberSwipeDismissableNavController()
    val account by graph.accounts.account.collectAsState()
    EncoreTheme(ambient) {
        CompositionLocalProvider(LocalArt provides remember { loaderArtSource(graph.art) }) {
            EncoreScaffold {
                SwipeDismissableNavHost(navController = nav, startDestination = if (account == null) Routes.SIGN_IN else Routes.HOME) {
                    composable(Routes.SIGN_IN) { SignInRoute(graph) { nav.navigate(Routes.HOME) { popUpTo(Routes.SIGN_IN) { inclusive = true } } } }
                    composable(Routes.HOME) { HomeRoute(graph, player) { nav.navigate(it) } }
                    composable(Routes.PLAYER) { WatchPlayerRoute(graph, player, volume) { nav.navigate(it) } }
                    composable(Routes.QUEUE) {
                        val st by player.state.collectAsState()
                        QueueScreen(st.queue, st.index) { player.skipTo(it); nav.popBackStack() }
                    }
                    composable(Routes.REMOTE) { RemoteRoute(graph) { nav.navigate(it) } }
                    composable(Routes.REMOTE_QUEUE) { RemoteQueueRoute(graph) { nav.popBackStack() } }
                    composable(Routes.REMOTE_LIBRARY) { e ->
                        val parent = e.arguments?.getString("parent") ?: ""
                        val title = e.arguments?.getString("title") ?: "Phone"
                        RemoteLibraryRoute(graph, parent, title, open = { nav.navigate(Routes.remoteLibrary(it.id, it.title)) }) {
                            nav.popBackStack(Routes.REMOTE, inclusive = false)
                        }
                    }
                    composable(Routes.ALBUMS) {
                        val albums by load(account) { graph.client.albums("recent", 30).ifEmpty { graph.client.albums("newest", 30) } }
                        AlbumsScreen("Recent albums", albums) { nav.navigate(Routes.album(it.id)) }
                    }
                    composable(Routes.PLAYLISTS) {
                        val lists by load(account) { graph.client.playlists() }
                        PlaylistsScreen(lists) { nav.navigate(Routes.playlist(it.id)) }
                    }
                    composable(Routes.FAVOURITES) {
                        val songs by load(account) { graph.client.starred().songs }
                        SongsRoute(graph, player, "Favourites", null, null, songs, keepKind = null, keepId = "") { nav.navigate(Routes.PLAYER) }
                    }
                    composable(Routes.ALBUM) { e ->
                        val id = e.arguments?.getString("id") ?: ""
                        val album by load(id) { graph.client.album(id) }
                        val a = (album as? Load.Ok)?.value
                        SongsRoute(graph, player, a?.name ?: "Album", a?.artist, a?.coverArt ?: "", album.map { it.songs }, "album", id) {
                            nav.navigate(Routes.PLAYER)
                        }
                    }
                    composable(Routes.PLAYLIST) { e ->
                        val id = e.arguments?.getString("id") ?: ""
                        val pl by load(id) { graph.client.playlist(id) }
                        val p = (pl as? Load.Ok)?.value
                        SongsRoute(graph, player, p?.name ?: "Playlist", p?.let { "${it.songCount} songs" }, p?.coverArt ?: "", pl.map { it.songs }, "playlist", id) {
                            nav.navigate(Routes.PLAYER)
                        }
                    }
                    composable(Routes.SEARCH) { SearchRoute(graph, player, nav) }
                    composable(Routes.DOWNLOADS) { DownloadsRoute(graph) { r -> nav.navigate(if (r.key.startsWith("album:")) Routes.album(r.key.substringAfter(':')) else Routes.playlist(r.key.substringAfter(':'))) } }
                    composable(Routes.SETTINGS) { SettingsRoute(graph, nav) }
                    composable(Routes.CAP) {
                        val s by graph.settings.settings.collectAsState()
                        CapScreen(s.downloadCapMb, SettingsStore.CAPS_MB) { mb -> graph.settings.update { it.copy(downloadCapMb = mb) }; nav.popBackStack() }
                    }
                    composable(Routes.TILE_PLAYLIST) {
                        val lists by load(account) { graph.client.playlists() }
                        PlaylistsScreen(lists) { p ->
                            graph.settings.update { it.copy(tilePlaylistId = p.id, tilePlaylistName = p.name) }
                            graph.nowPlaying.refreshSurfaces()
                            nav.popBackStack()
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(account) {
        if (account == null && nav.currentDestination?.route != Routes.SIGN_IN) {
            nav.navigate(Routes.SIGN_IN) { popUpTo(0) }
        }
    }
    LaunchedEffect(openOnStart, account) {
        if (account != null && openOnStart != null) nav.navigate(openOnStart)
    }
}

private fun <T, R> Load<T>.map(f: (T) -> R): Load<R> = when (this) {
    Load.Loading -> Load.Loading
    is Load.Failed -> this
    is Load.Ok -> Load.Ok(f(value))
}

/** Loads once per [key], showing the server's error in words. */
@Composable
private fun <T> load(key: Any?, block: suspend () -> T) = produceState<Load<T>>(Load.Loading, key) {
    value = try {
        Load.Ok(block())
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        Load.Failed(friendly(e))
    }
}

private fun friendly(e: Exception): String = when (e) {
    is java.net.UnknownHostException, is java.net.ConnectException, is java.net.SocketTimeoutException -> "Can't reach the server"
    else -> e.message ?: "Something went wrong"
}

/** The watch keyboard (or voice): returns the text typed, for whichever field asked. */
@Composable
private fun rememberKeyboard(onText: (String, String) -> Unit): (String, String) -> Unit {
    var field by rememberSaveable { mutableStateOf("") }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val text = r.data?.let { RemoteInput.getResultsFromIntent(it) }?.getCharSequence(field)?.toString() ?: return@rememberLauncherForActivityResult
        onText(field, text.trim())
    }
    return { key, label ->
        field = key
        val intent: Intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(RemoteInput.Builder(key).setLabel(label).build()))
        runCatching { launcher.launch(intent) }
    }
}

@Composable
private fun SignInRoute(graph: Graph, done: () -> Unit) {
    var server by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val keyboard = rememberKeyboard { k, v -> when (k) { "server" -> server = v; "user" -> user = v; "pass" -> pass = v } }
    SignInScreen(
        SignInUi(server, user, pass.isNotEmpty(), busy, error),
        SignInActions(
            usePhone = {
                scope.launch {
                    val info = graph.phone.phoneAccount()
                    if (info == null) error = "Sign in on your phone first, with the phone nearby"
                    else { graph.accounts.save(Account(info.server, info.user, info.pass, info.localAddress, fromPhone = true)); done() }
                }
            },
            editServer = { keyboard("server", "Server address") },
            editUser = { keyboard("user", "Username") },
            editPass = { keyboard("pass", "Password") },
            signIn = {
                busy = true
                error = null
                scope.launch {
                    val a = Account(SubsonicUrls.normalize(server), user, pass)
                    val check = SubsonicClient({ a }, { a.server }, graph.http)
                    try {
                        check.ping()
                        graph.accounts.save(a)
                        done()
                    } catch (e: Exception) {
                        error = friendly(e)
                    } finally {
                        busy = false
                    }
                }
            },
        ),
    )
}

@Composable
private fun HomeRoute(graph: Graph, player: PlayerConnection, go: (String) -> Unit) {
    val st by player.state.collectAsState()
    val phone by graph.phone.state.collectAsState()
    val downloads by graph.downloads.state.collectAsState()
    LaunchedEffect(Unit) { runCatching { player.connect() }; graph.phone.send(Command.State) }
    HomeScreen(
        HomeUi(
            watchPlaying = st.song?.let { "${it.title} · ${it.artist}" },
            phonePlaying = phone.nowPlaying.takeIf { !it.isEmpty }?.let { "${it.title} · ${it.artist}" },
            phoneReachable = phone.reachable,
            downloads = downloads.collections.size,
        ),
        go,
    )
}

@Composable
private fun WatchPlayerRoute(graph: Graph, player: PlayerConnection, volume: Volume, go: (String) -> Unit) {
    val context = LocalContext.current
    val st by player.state.collectAsState()
    val hearts by graph.hearts.hearts.collectAsState()
    val vol by volume.level.collectAsState()
    DisposableEffect(Unit) {
        player.tick(true)
        volume.refresh()
        onDispose { player.tick(false) }
    }
    val s = st.song
    PlayerScreen(
        NowPlayingUi(
            title = s?.title ?: "",
            artist = s?.artist ?: "",
            coverArt = s?.coverArt ?: "",
            playing = st.playing,
            buffering = st.buffering,
            progress = if (st.durationMs > 0) st.positionMs.toFloat() / st.durationMs else 0f,
            starred = s?.let { hearts[it.id] ?: it.starred } ?: false,
            hasPrevious = st.hasPrevious,
            hasNext = st.hasNext,
            volume = vol.fraction,
            output = AudioOutput.headphones(context),
            needsHeadphones = st.needsHeadphones,
            message = st.error,
        ),
        PlayerActions(
            onToggle = {
                // no headphones: ask for some first (the player won't play out of the watch)
                if (!st.playing && AudioOutput.headphones(context) == null) AudioOutput.openSwitcher(context)
                player.toggle()
            },
            onNext = player::next,
            onPrevious = player::previous,
            onHeart = { s?.let { graph.hearts.toggle(it.id, it.starred) } },
            onVolume = volume::change,
            onOutput = { AudioOutput.openSwitcher(context) },
            onQueue = { go(Routes.QUEUE) },
        ),
    )
}

@Composable
private fun RemoteRoute(graph: Graph, go: (String) -> Unit) {
    val phone by graph.phone.state.collectAsState()
    DisposableEffect(Unit) {
        graph.phone.watch(true)
        onDispose { graph.phone.watch(false) }
    }
    // the position counts on between updates from the phone
    val now by produceState(System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); kotlinx.coroutines.delay(500) }
    }
    val n = phone.nowPlaying
    val link = graph.phone
    PlayerScreen(
        NowPlayingUi(
            title = n.title,
            artist = n.artist,
            art = phone.art.takeIf { phone.artFor == n.mediaId },
            playing = n.playing,
            buffering = n.buffering,
            progress = if (n.durationMs > 0) n.positionAt(now - phone.receivedAt + n.updatedAt).toFloat() / n.durationMs else 0f,
            starred = n.starred,
            volume = if (n.maxVolume > 0) n.volume.toFloat() / n.maxVolume else 0f,
            onPhone = true,
            message = when {
                phone.reachable == false -> "Phone not connected"
                phone.error != null -> phone.error
                phone.reachable == null -> "Looking for your phone…"
                else -> null
            },
        ),
        PlayerActions(
            onToggle = { link.command(Command.TogglePlay) },
            onNext = { link.command(Command.Next) },
            onPrevious = { link.command(Command.Previous) },
            onHeart = { link.command(Command.ToggleHeart) },
            onVolume = { link.command(Command.ChangeVolume(it)) },
            onQueue = { go(Routes.REMOTE_QUEUE) },
            onLibrary = { go(Routes.remoteLibrary("", "Phone's library")) },
        ),
    )
}

@Composable
private fun RemoteQueueRoute(graph: Graph, back: () -> Unit) {
    val q by produceState<Load<Reply.Queue>>(Load.Loading) {
        value = when (val r = graph.phone.send(Command.Queue)) {
            is Reply.Queue -> Load.Ok(r)
            is Reply.Error -> Load.Failed(r.message)
            else -> Load.Failed("Phone not connected")
        }
    }
    PhoneQueueScreen(q.map { it.items }, (q as? Load.Ok)?.value?.currentQueueId ?: -1) { item: QueueItem ->
        graph.phone.command(Command.SkipTo(item.queueId))
        back()
    }
}

@Composable
private fun RemoteLibraryRoute(graph: Graph, parent: String, title: String, open: (BrowseItem) -> Unit, played: () -> Unit) {
    val items by produceState<Load<List<BrowseItem>>>(Load.Loading, parent) {
        value = when (val r = graph.phone.send(Command.Browse(parent))) {
            is Reply.Browse -> Load.Ok(r.items)
            is Reply.Error -> Load.Failed(r.message)
            else -> Load.Failed("Phone not connected")
        }
    }
    PhoneLibraryScreen(title, items) { b ->
        if (b.playable) { graph.phone.command(Command.PlayItem(b.id)); played() } else open(b)
    }
}

@Composable
private fun SongsRoute(
    graph: Graph,
    player: PlayerConnection,
    title: String,
    subtitle: String?,
    coverArt: String?,
    songs: Load<List<Song>>,
    keepKind: String?,
    keepId: String,
    playing: () -> Unit,
) {
    val context = LocalContext.current
    val downloads by graph.downloads.state.collectAsState()
    val st by player.state.collectAsState()
    val key = "$keepKind:$keepId"
    val c = downloads.collections.firstOrNull { it.key == key }
    // offline: an album or playlist on the watch still opens from what was kept
    val shown = if (songs is Load.Failed && c != null) Load.Ok(downloads.songsOf(c).map { it.song }) else songs
    SongsScreen(
        title = c?.name ?: title,
        subtitle = subtitle,
        coverArt = coverArt ?: c?.coverArt,
        songs = shown,
        keep = keepKind?.let {
            KeepUi(
                kept = c != null,
                done = c?.let { downloads.doneCount(it) } ?: 0,
                total = c?.songIds?.size ?: 0,
                note = when (c?.state) {
                    DownloadState.FULL -> "Storage cap reached"
                    DownloadState.FAILED -> c.error.ifEmpty { "Download failed" }
                    DownloadState.QUEUED -> if (c.error.isNotEmpty()) c.error else ""
                    else -> ""
                },
            )
        },
        play = { i, shuffle ->
            val list = (shown as? Load.Ok)?.value ?: return@SongsScreen
            player.play(list, i, shuffle)
            playing()
        },
        onKeep = keepKind?.let {
            {
                val list = (shown as? Load.Ok)?.value.orEmpty()
                if (c != null) {
                    DownloadWorker.cancel(context, key)
                    graph.downloads.remove(key)
                } else if (list.isNotEmpty()) {
                    graph.downloads.add(keepKind, keepId, title, coverArt ?: "", list)
                    DownloadWorker.enqueue(context, key, graph.settings.current.downloadWifiOnly)
                }
            }
        },
        playingId = st.song?.id,
    )
}

@Composable
private fun SearchRoute(graph: Graph, player: PlayerConnection, nav: NavHostController) {
    var query by rememberSaveable { mutableStateOf("") }
    val keyboard = rememberKeyboard { _, v -> query = v }
    val results by produceState<Load<dev.safro.encore.wear.subsonic.SearchResult>?>(null, query) {
        if (query.isEmpty()) return@produceState
        value = Load.Loading
        value = try { Load.Ok(graph.client.search(query)) } catch (e: Exception) { Load.Failed(friendly(e)) }
    }
    LaunchedEffect(Unit) { if (query.isEmpty()) keyboard("q", "Search") }
    SearchScreen(
        query, results,
        onType = { keyboard("q", "Search") },
        playSong = { list, i -> player.play(list, i); nav.navigate(Routes.PLAYER) },
        openAlbum = { nav.navigate(Routes.album(it.id)) },
    )
}

@Composable
private fun DownloadsRoute(graph: Graph, open: (DownloadRow) -> Unit) {
    val d by graph.downloads.state.collectAsState()
    val s by graph.settings.settings.collectAsState()
    val used = remember(d) { graph.downloads.usedBytes() shr 20 }
    DownloadsScreen(
        d.collections.map { c ->
            val done = d.doneCount(c)
            DownloadRow(
                c.key, c.name, c.coverArt,
                when (c.state) {
                    DownloadState.DONE -> "${c.songIds.size} songs"
                    DownloadState.FULL -> "Storage cap reached"
                    DownloadState.FAILED -> c.error.ifEmpty { "Failed" }
                    else -> "$done of ${c.songIds.size}"
                },
            )
        },
        used, s.downloadCapMb, open,
    )
}

@Composable
private fun SettingsRoute(graph: Graph, nav: NavHostController) {
    val account by graph.accounts.account.collectAsState()
    val s by graph.settings.settings.collectAsState()
    val scope = rememberCoroutineScope()
    val keyboard = rememberKeyboard { _, v -> graph.accounts.setLocalAddress(if (v.isEmpty()) "" else SubsonicUrls.normalize(v)) }
    val a = account ?: return
    SettingsScreen(
        SettingsUi(
            server = a.server, user = a.user, fromPhone = a.fromPhone, localAddress = a.localAddress,
            onHomeAddress = graph.router.onHomeAddress, capMb = s.downloadCapMb, wifiOnly = s.downloadWifiOnly,
            scrobble = s.scrobble, tilePlaylist = s.tilePlaylistName, version = BuildConfig.VERSION_NAME,
        ),
        SettingsActions(
            usePhoneSignIn = {
                scope.launch {
                    graph.phone.phoneAccount()?.let { graph.accounts.save(Account(it.server, it.user, it.pass, it.localAddress, fromPhone = true)) }
                }
            },
            editLocalAddress = { keyboard("local", "Home network address") },
            pickCap = { nav.navigate(Routes.CAP) },
            setWifiOnly = { on -> graph.settings.update { it.copy(downloadWifiOnly = on) } },
            setScrobble = { on -> graph.settings.update { it.copy(scrobble = on) } },
            pickTilePlaylist = { nav.navigate(Routes.TILE_PLAYLIST) },
            signOut = { graph.accounts.signOut() },
        ),
    )
}
