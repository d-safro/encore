package dev.safro.encore.wear.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.LevelIndicator
import androidx.wear.compose.material3.LinearProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.delay

/** What a player screen shows: the watch's own player, or Encore on the phone. */
data class NowPlayingUi(
    val title: String = "",
    val artist: String = "",
    val coverArt: String = "",
    /** A cover that came as a picture (the phone sends its own), used over [coverArt]. */
    val art: ImageBitmap? = null,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val progress: Float = 0f,
    val starred: Boolean = false,
    val hasPrevious: Boolean = true,
    val hasNext: Boolean = true,
    val volume: Float = 0f,
    /** The headphones it plays on (watch player); null if none. */
    val output: String? = null,
    val needsHeadphones: Boolean = false,
    val onPhone: Boolean = false,
    val message: String? = null,
) {
    val empty get() = title.isEmpty()
}

class PlayerActions(
    val onToggle: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onPrevious: () -> Unit = {},
    val onHeart: () -> Unit = {},
    /** Crown turned: volume up (+) or down (-) by this many steps. */
    val onVolume: (Int) -> Unit = {},
    val onOutput: (() -> Unit)? = null,
    val onQueue: (() -> Unit)? = null,
    val onLibrary: (() -> Unit)? = null,
)

@Composable
fun PlayerScreen(ui: NowPlayingUi, actions: PlayerActions, showVolume: Boolean = false) {
    val ambient = LocalAmbient.current
    val round = LocalConfiguration.current.isScreenRound
    val focus = remember { FocusRequester() }
    val crown = remember { dev.safro.encore.wear.playback.CrownSteps() }
    // the volume shows at the edge while the crown turns, and for 2 s after
    var turnedAt by remember { mutableLongStateOf(0L) }
    var volumeVisible by remember { mutableStateOf(showVolume) }
    LaunchedEffect(turnedAt) {
        if (turnedAt > 0) { volumeVisible = true; delay(2000); volumeVisible = showVolume }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    ScreenScaffold(timeText = {}) { _ ->
        Box(
            Modifier
                .fillMaxSize()
                .onRotaryScrollEvent {
                    val steps = crown.add(it.verticalScrollPixels)
                    if (steps != 0) actions.onVolume(steps)
                    turnedAt = System.nanoTime()
                    true
                }
                .focusRequester(focus)
                .focusable(),
            contentAlignment = Alignment.Center,
        ) {
            Backdrop(ui, ambient)
            if (round) {
                CircularProgressIndicator(
                    progress = { ui.progress },
                    modifier = Modifier.fillMaxSize().padding(3.dp),
                    strokeWidth = 4.dp,
                    gapSize = 0.dp,
                )
            }
            Column(
                Modifier.fillMaxSize().padding(horizontal = if (round) 26.dp else 10.dp, vertical = if (round) 22.dp else 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Source(ui)
                Spacer(Modifier.height(2.dp))
                if (ui.empty) {
                    Text(
                        ui.message ?: "Nothing playing",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                } else {
                    Text(
                        ui.title,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth().basicMarquee(iterations = if (ambient) 0 else Int.MAX_VALUE),
                    )
                    Text(
                        ui.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Controls(ui, actions, ambient)
                Spacer(Modifier.height(4.dp))
                Extras(ui, actions)
                if (!round) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(progress = { ui.progress }, modifier = Modifier.fillMaxWidth(0.8f))
                }
            }
            if (volumeVisible) {
                LevelIndicator(
                    value = { ui.volume },
                    modifier = Modifier.align(Alignment.CenterEnd).semantics { contentDescription = "Volume" },
                )
            }
            if (ui.needsHeadphones && !ui.onPhone) HeadphonesPrompt(actions.onOutput ?: {})
        }
    }
}

@Composable
private fun Backdrop(ui: NowPlayingUi, ambient: Boolean) {
    if (ambient) return
    val art = ui.art ?: ui.coverArt.takeIf { it.isNotEmpty() }?.let { LocalArt.current.art(it, 300) }
    if (art != null) {
        Image(art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().alpha(0.42f))
    }
    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.85f))),
        ),
    )
}

@Composable
private fun Source(ui: NowPlayingUi) {
    val (icon, label) = when {
        ui.onPhone -> Glyphs.Phone to "Phone"
        ui.output != null -> Glyphs.Headphones to ui.output
        else -> Glyphs.Watch to "Watch"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primaryDim)
        Spacer(Modifier.size(3.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primaryDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Controls(ui: NowPlayingUi, actions: PlayerActions, ambient: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        IconButton(onClick = actions.onPrevious, enabled = ui.hasPrevious && !ui.empty) {
            Icon(Glyphs.Previous, contentDescription = "Previous", modifier = Modifier.size(28.dp))
        }
        val colors = if (ambient) IconButtonDefaults.outlinedIconButtonColors() else IconButtonDefaults.filledIconButtonColors()
        FilledIconButton(
            onClick = actions.onToggle,
            enabled = !ui.empty,
            colors = colors,
            border = if (ambient) ButtonDefaults.outlinedButtonBorder(true) else null,
            modifier = Modifier.size(IconButtonDefaults.LargeButtonSize),
        ) {
            if (ui.buffering && ui.playing) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
            } else {
                Icon(
                    if (ui.playing) Glyphs.Pause else Glyphs.Play,
                    contentDescription = if (ui.playing) "Pause" else "Play",
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        IconButton(onClick = actions.onNext, enabled = ui.hasNext && !ui.empty) {
            Icon(Glyphs.Next, contentDescription = "Next", modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun Extras(ui: NowPlayingUi, actions: PlayerActions) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        val small = Modifier.size(IconButtonDefaults.SmallButtonSize)
        IconButton(onClick = actions.onHeart, enabled = !ui.empty, modifier = small) {
            Icon(
                if (ui.starred) Glyphs.Heart else Glyphs.HeartOutline,
                contentDescription = if (ui.starred) "Unfavourite" else "Favourite",
                tint = if (ui.starred) MaterialTheme.colorScheme.primaryDim else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }
        actions.onQueue?.let {
            IconButton(onClick = it, modifier = small) { Icon(Glyphs.Queue, contentDescription = "Queue", modifier = Modifier.size(20.dp)) }
        }
        actions.onLibrary?.let {
            IconButton(onClick = it, modifier = small) { Icon(Glyphs.Library, contentDescription = "Phone's library", modifier = Modifier.size(20.dp)) }
        }
        actions.onOutput?.let {
            IconButton(onClick = it, modifier = small) { Icon(Glyphs.Headphones, contentDescription = "Headphones", modifier = Modifier.size(20.dp)) }
        }
    }
}

/** No headphones: music doesn't play out of the watch. One tap opens Wear OS's output switcher. */
@Composable
private fun HeadphonesPrompt(onPick: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.88f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(28.dp)) {
            Icon(Glyphs.Headphones, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text("Connect headphones to listen", textAlign = TextAlign.Center, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            CompactButton(onClick = onPick, colors = ButtonDefaults.buttonColors(), label = { Text("Pick headphones") })
        }
    }
}
