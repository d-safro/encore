package dev.safro.encore.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.LinearProgressIndicator
import androidx.wear.compose.material3.ListSubHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import dev.safro.encore.wear.subsonic.Album
import dev.safro.encore.wear.subsonic.Playlist
import dev.safro.encore.wear.subsonic.SearchResult
import dev.safro.encore.wear.subsonic.Song
import dev.safro.encore.wearlink.BrowseItem
import dev.safro.encore.wearlink.QueueItem

// Every screen here is plain: it gets what to show and what to do on a tap. WearApp.kt gives them the real
// data; the screenshot tests give them made-up data.

data class HomeUi(
    val watchPlaying: String? = null,
    val phonePlaying: String? = null,
    val phoneReachable: Boolean? = null,
    val downloads: Int = 0,
)

@Composable
fun HomeScreen(ui: HomeUi, go: (String) -> Unit) {
    ListScreen("Encore") {
        if (ui.watchPlaying != null) {
            item { ItemButton("Now playing", { go(Routes.PLAYER) }, subtitle = ui.watchPlaying, icon = Glyphs.Watch, primary = true) }
        }
        item {
            ItemButton(
                "Phone",
                { go(Routes.REMOTE) },
                subtitle = when {
                    ui.phonePlaying != null -> ui.phonePlaying
                    ui.phoneReachable == false -> "Not connected"
                    else -> "Control Encore on your phone"
                },
                icon = Glyphs.Phone,
                primary = ui.watchPlaying == null && ui.phonePlaying != null,
            )
        }
        item { ListSubHeader { Text("On this watch") } }
        item { ItemButton("Recent albums", { go(Routes.ALBUMS) }, icon = Glyphs.Album) }
        item { ItemButton("Playlists", { go(Routes.PLAYLISTS) }, icon = Glyphs.Queue) }
        item { ItemButton("Favourites", { go(Routes.FAVOURITES) }, icon = Glyphs.Heart) }
        item { ItemButton("Search", { go(Routes.SEARCH) }, icon = Glyphs.Search) }
        item {
            ItemButton("Downloads", { go(Routes.DOWNLOADS) }, icon = Glyphs.Download,
                subtitle = if (ui.downloads > 0) "${ui.downloads} kept for offline" else "None yet")
        }
        item { ItemButton("Settings", { go(Routes.SETTINGS) }, icon = Glyphs.Settings) }
    }
}

@Composable
fun AlbumsScreen(title: String, albums: Load<List<Album>>, open: (Album) -> Unit) {
    ListScreen(title) {
        loaded(albums, "No albums yet") { list ->
            items(list, key = { it.id }) { a -> ItemButton(a.name, { open(a) }, subtitle = a.artist, coverArt = a.coverArt) }
        }
    }
}

@Composable
fun PlaylistsScreen(playlists: Load<List<Playlist>>, open: (Playlist) -> Unit) {
    ListScreen("Playlists") {
        loaded(playlists, "No playlists yet") { list ->
            items(list, key = { it.id }) { p -> ItemButton(p.name, { open(p) }, subtitle = "${p.songCount} songs", coverArt = p.coverArt) }
        }
    }
}

/** Whether an album or playlist is on the watch. */
data class KeepUi(val kept: Boolean = false, val done: Int = 0, val total: Int = 0, val note: String = "")

/** An album, playlist or Favourites: play it, shuffle it, keep it on the watch, or start from a song. */
@Composable
fun SongsScreen(
    title: String,
    subtitle: String?,
    coverArt: String?,
    songs: Load<List<Song>>,
    keep: KeepUi?,
    play: (Int, Boolean) -> Unit,
    onKeep: (() -> Unit)? = null,
    playingId: String? = null,
) {
    ListScreen(
        title = null,
        edgeButton = if (songs is Load.Ok && songs.value.isNotEmpty()) ({
            EdgeButton(onClick = { play(0, false) }, buttonSize = EdgeButtonSize.Medium) {
                Icon(Glyphs.Play, contentDescription = "Play")
            }
        }) else null,
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrEmpty()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
        if (songs is Load.Ok && songs.value.isNotEmpty()) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (coverArt != null) Cover(coverArt, 48.dp)
                    FilledTonalIconButton(onClick = { play(0, true) }) { Icon(Glyphs.Shuffle, contentDescription = "Shuffle") }
                    if (keep != null && onKeep != null) {
                        FilledTonalIconButton(onClick = onKeep) {
                            Icon(
                                if (keep.kept) Glyphs.Delete else Glyphs.Download,
                                contentDescription = if (keep.kept) "Remove from watch" else "Download to watch",
                            )
                        }
                    }
                }
            }
            if (keep != null && keep.kept) {
                item {
                    Note(
                        when {
                            keep.note.isNotEmpty() -> keep.note
                            keep.done >= keep.total -> "On this watch"
                            else -> "Downloading ${keep.done} of ${keep.total}"
                        },
                    )
                }
            }
        }
        loaded(songs, "No songs") { list ->
            items(list.size) { i ->
                val s = list[i]
                ItemButton(
                    s.title, { play(i, false) },
                    subtitle = s.artist,
                    icon = if (s.id == playingId) Glyphs.Play else null,
                    primary = s.id == playingId,
                )
            }
        }
    }
}

@Composable
fun SearchScreen(query: String, results: Load<SearchResult>?, onType: () -> Unit, playSong: (List<Song>, Int) -> Unit, openAlbum: (Album) -> Unit) {
    ListScreen("Search") {
        item { ItemButton(query.ifEmpty { "Search your music" }, onType, icon = Glyphs.Search, primary = query.isEmpty()) }
        when (results) {
            null -> {}
            Load.Loading -> item { Spinner() }
            is Load.Failed -> item { Note(results.message) }
            is Load.Ok -> {
                val r = results.value
                if (r.isEmpty) item { Note("Nothing found") }
                if (r.songs.isNotEmpty()) item { ListSubHeader { Text("Songs") } }
                items(r.songs.size) { i -> ItemButton(r.songs[i].title, { playSong(r.songs, i) }, subtitle = r.songs[i].artist, coverArt = r.songs[i].coverArt) }
                if (r.albums.isNotEmpty()) item { ListSubHeader { Text("Albums") } }
                items(r.albums, key = { "a" + it.id }) { a -> ItemButton(a.name, { openAlbum(a) }, subtitle = a.artist, coverArt = a.coverArt) }
            }
        }
    }
}

data class DownloadRow(val key: String, val name: String, val coverArt: String, val status: String)

@Composable
fun DownloadsScreen(rows: List<DownloadRow>, usedMb: Long, capMb: Int, open: (DownloadRow) -> Unit) {
    ListScreen("Downloads") {
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${formatMb(usedMb)} of ${formatMb(capMb.toLong())}", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(progress = { (usedMb.toFloat() / capMb).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            }
        }
        if (rows.isEmpty()) item { Note("Albums and playlists you download play here without the phone or a network.") }
        items(rows, key = { it.key }) { r -> ItemButton(r.name, { open(r) }, subtitle = r.status, coverArt = r.coverArt) }
    }
}

fun formatMb(mb: Long): String = if (mb >= 1024) "%.1f GB".format(mb / 1024f) else "$mb MB"

data class SettingsUi(
    val server: String = "",
    val user: String = "",
    val fromPhone: Boolean = false,
    val localAddress: String = "",
    val onHomeAddress: Boolean = false,
    val capMb: Int = 2048,
    val wifiOnly: Boolean = true,
    val scrobble: Boolean = true,
    val tilePlaylist: String = "",
    val version: String = "",
)

class SettingsActions(
    val usePhoneSignIn: () -> Unit = {},
    val editLocalAddress: () -> Unit = {},
    val pickCap: () -> Unit = {},
    val setWifiOnly: (Boolean) -> Unit = {},
    val setScrobble: (Boolean) -> Unit = {},
    val pickTilePlaylist: () -> Unit = {},
    val signOut: () -> Unit = {},
)

@Composable
fun SettingsScreen(ui: SettingsUi, a: SettingsActions) {
    ListScreen("Settings") {
        item {
            ItemButton(ui.server.removePrefix("https://").removePrefix("http://"), {}, subtitle = ui.user + if (ui.fromPhone) " · from phone" else "", icon = Glyphs.Storage)
        }
        item { ItemButton("Use phone's sign-in", a.usePhoneSignIn, icon = Glyphs.Phone) }
        item {
            ItemButton(
                "Home network address", a.editLocalAddress,
                subtitle = when {
                    ui.localAddress.isEmpty() -> "Not set"
                    ui.onHomeAddress -> "In use: " + ui.localAddress.substringAfter("://")
                    else -> ui.localAddress.substringAfter("://")
                },
                icon = Glyphs.Home,
            )
        }
        item { ListSubHeader { Text("Downloads") } }
        item { ItemButton("Storage cap", a.pickCap, subtitle = formatMb(ui.capMb.toLong()), icon = Glyphs.Storage) }
        item {
            SwitchButton(
                checked = ui.wifiOnly, onCheckedChange = a.setWifiOnly, modifier = Modifier.fillMaxWidth(),
                label = { Text("Wi-Fi only") },
                icon = { Icon(Glyphs.Wifi, contentDescription = null) },
            )
        }
        item { ListSubHeader { Text("Playing") } }
        item {
            SwitchButton(
                checked = ui.scrobble, onCheckedChange = a.setScrobble, modifier = Modifier.fillMaxWidth(),
                label = { Text("Scrobble plays") },
            )
        }
        item { ItemButton("Tile playlist", a.pickTilePlaylist, subtitle = ui.tilePlaylist.ifEmpty { "Choose one" }, icon = Glyphs.Queue) }
        item {
            Button(
                onClick = a.signOut, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.filledTonalButtonColors(contentColor = MaterialTheme.colorScheme.error),
                label = { Text("Sign out") },
            )
        }
        if (ui.version.isNotEmpty()) item { Note("Encore ${ui.version}") }
    }
}

@Composable
fun CapScreen(current: Int, choices: List<Int>, pick: (Int) -> Unit) {
    ListScreen("Storage cap") {
        items(choices) { mb ->
            RadioButton(
                selected = mb == current, onSelect = { pick(mb) }, modifier = Modifier.fillMaxWidth(),
                label = { Text(formatMb(mb.toLong())) },
            )
        }
    }
}

data class SignInUi(
    val server: String = "",
    val user: String = "",
    val passSet: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
)

class SignInActions(
    val usePhone: () -> Unit = {},
    val editServer: () -> Unit = {},
    val editUser: () -> Unit = {},
    val editPass: () -> Unit = {},
    val signIn: () -> Unit = {},
)

/** No server yet: take the phone's sign-in (it's sent on pairing), or type one with the watch keyboard. */
@Composable
fun SignInScreen(ui: SignInUi, a: SignInActions) {
    ListScreen("Sign in") {
        item { Note("Open Encore on your phone and the watch signs in to the same server.") }
        item { ItemButton("Use phone's sign-in", a.usePhone, icon = Glyphs.Phone, primary = true) }
        item { ListSubHeader { Text("Or sign in here") } }
        item { ItemButton("Server", a.editServer, subtitle = ui.server.ifEmpty { "music.example.com" }) }
        item { ItemButton("Username", a.editUser, subtitle = ui.user.ifEmpty { "Not set" }) }
        item { ItemButton("Password", a.editPass, subtitle = if (ui.passSet) "••••••••" else "Not set") }
        if (ui.error != null) item { Note(ui.error) }
        item {
            Button(
                onClick = a.signIn, enabled = !ui.busy && ui.server.isNotEmpty() && ui.user.isNotEmpty() && ui.passSet,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (ui.busy) "Signing in…" else "Sign in") },
            )
        }
    }
}

/** Encore's queue on the phone: tap a song to jump to it. */
@Composable
fun PhoneQueueScreen(queue: Load<List<QueueItem>>, current: Long, skipTo: (QueueItem) -> Unit) {
    ListScreen("Up next on phone") {
        loaded(queue, "The queue is empty") { list ->
            items(list, key = { it.queueId }) { q ->
                ItemButton(q.title, { skipTo(q) }, subtitle = q.artist.ifEmpty { null },
                    icon = if (q.queueId == current) Glyphs.Play else null, primary = q.queueId == current)
            }
        }
    }
}

/** The phone's library (what Android Auto shows): folders open, everything else plays on the phone. */
@Composable
fun PhoneLibraryScreen(title: String, items: Load<List<BrowseItem>>, open: (BrowseItem) -> Unit) {
    ListScreen(title) {
        loaded(items, "Nothing here") { list ->
            items(list, key = { it.id }) { b ->
                ItemButton(b.title, { open(b) }, subtitle = b.subtitle.ifEmpty { null }, icon = if (b.playable) Glyphs.Play else Glyphs.Library)
            }
        }
    }
}

/** The watch player's own queue. */
@Composable
fun QueueScreen(queue: List<Song>, index: Int, skipTo: (Int) -> Unit) {
    ListScreen("Up next") {
        if (queue.isEmpty()) item { Note("The queue is empty") }
        items(queue.size) { i ->
            ItemButton(queue[i].title, { skipTo(i) }, subtitle = queue[i].artist, icon = if (i == index) Glyphs.Play else null, primary = i == index)
        }
    }
}

object Routes {
    const val HOME = "home"
    const val PLAYER = "player"
    const val QUEUE = "queue"
    const val REMOTE = "remote"
    const val REMOTE_QUEUE = "remote/queue"
    const val REMOTE_LIBRARY = "remote/library?parent={parent}&title={title}"
    const val ALBUMS = "albums"
    const val PLAYLISTS = "playlists"
    const val FAVOURITES = "favourites"
    const val SEARCH = "search"
    const val ALBUM = "album/{id}"
    const val PLAYLIST = "playlist/{id}"
    const val DOWNLOADS = "downloads"
    const val SETTINGS = "settings"
    const val CAP = "settings/cap"
    const val TILE_PLAYLIST = "settings/tile"
    const val SIGN_IN = "signin"

    fun album(id: String) = "album/" + android.net.Uri.encode(id)
    fun playlist(id: String) = "playlist/" + android.net.Uri.encode(id)
    fun remoteLibrary(parent: String, title: String) =
        "remote/library?parent=" + android.net.Uri.encode(parent) + "&title=" + android.net.Uri.encode(title)
}
