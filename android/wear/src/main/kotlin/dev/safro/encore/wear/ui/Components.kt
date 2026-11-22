package dev.safro.encore.wear.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeSource
import androidx.wear.compose.material3.TimeText

/** Something loading from the server or the phone. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ok<T>(val value: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

/** The app's frame: the time along the top edge. [timeSource] is fixed in screenshot tests. */
@Composable
fun EncoreScaffold(timeSource: TimeSource? = null, content: @Composable () -> Unit) {
    AppScaffold(
        timeText = { if (timeSource != null) TimeText(timeSource = timeSource) else TimeText() },
    ) { content() }
}

/**
 * A scrolling screen: a list that shrinks and fades at the edges of a round screen, follows the crown, and
 * has its scroll indicator. Used by every list in the app.
 */
@Composable
fun ListScreen(
    title: String?,
    edgeButton: (@Composable androidx.compose.foundation.layout.BoxScope.() -> Unit)? = null,
    content: ScalingLazyListScope.() -> Unit,
) {
    val state = rememberScalingLazyListState()
    val list: @Composable androidx.compose.foundation.layout.BoxScope.(androidx.compose.foundation.layout.PaddingValues) -> Unit = { padding ->
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = state,
            contentPadding = padding,
            // lists start at the top, under the time (centering the second item pushed the first one into
            // the time on small square screens)
            autoCentering = null,
        ) {
            if (title != null) item { ListHeader { Text(title, maxLines = 2, textAlign = TextAlign.Center) } }
            content()
        }
    }
    if (edgeButton != null) ScreenScaffold(scrollState = state, edgeButton = edgeButton, content = list)
    else ScreenScaffold(scrollState = state, content = list)
}

/** A row in a list: an album, playlist, song or menu entry, with its cover or an icon. */
@Composable
fun ItemButton(
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    coverArt: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    primary: Boolean = false,
) {
    val ambient = LocalAmbient.current
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        colors = when {
            ambient -> ButtonDefaults.outlinedButtonColors()
            primary -> ButtonDefaults.buttonColors()
            else -> ButtonDefaults.filledTonalButtonColors()
        },
        border = if (ambient) ButtonDefaults.outlinedButtonBorder(enabled) else null,
        label = { Text(title, maxLines = if (subtitle == null) 2 else 1, overflow = TextOverflow.Ellipsis) },
        secondaryLabel = subtitle?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        icon = when {
            coverArt != null -> ({ Cover(coverArt, 32.dp, round = true) })
            icon != null -> ({ Icon(icon, contentDescription = null) })
            else -> null
        },
    )
}

/** An album cover, or a plain disc while it loads (and always in ambient mode). */
@Composable
fun Cover(coverArt: String, size: Dp, round: Boolean = false, modifier: Modifier = Modifier) {
    val shape = if (round) CircleShape else RoundedCornerShape(size / 8)
    val ambient = LocalAmbient.current
    val bmp = if (ambient || coverArt.isEmpty()) null else LocalArt.current.art(coverArt, if (size > 64.dp) 300 else 96)
    Box(
        modifier.size(size).clip(shape).background(
            if (ambient) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) Image(bmp, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Icon(Glyphs.Note, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(size / 2))
    }
}

/** A short line in place of a list: loading, empty, or what went wrong. */
@Composable
fun Note(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun Spinner() {
    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.size(32.dp))
    }
}

/** The list items for a [Load]: a spinner, the error, "nothing here", or [items]. */
fun <T> ScalingLazyListScope.loaded(load: Load<List<T>>, empty: String, items: ScalingLazyListScope.(List<T>) -> Unit) {
    when (load) {
        Load.Loading -> item { Spinner() }
        is Load.Failed -> item { Note(load.message) }
        is Load.Ok -> if (load.value.isEmpty()) item { Note(empty) } else items(load.value)
    }
}
