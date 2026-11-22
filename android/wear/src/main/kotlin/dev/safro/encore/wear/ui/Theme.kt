package dev.safro.encore.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

/** True while the watch is in ambient (always-on) mode: dim, mostly black, no pictures. */
val LocalAmbient = staticCompositionLocalOf { false }

// Encore's colours (docs/logo.svg): periwinkle on near-black
private val Periwinkle = Color(0xFF7C90FF)
private val PeriwinkleLight = Color(0xFFDCE3FF)
private val Night = Color(0xFF0D0E15)
private val NightSurface = Color(0xFF1B1E2E)

val EncoreColors = ColorScheme(
    primary = PeriwinkleLight,
    primaryDim = Periwinkle,
    primaryContainer = Color(0xFF3A4796),
    onPrimary = Color(0xFF14204F),
    onPrimaryContainer = PeriwinkleLight,
    secondary = Color(0xFFC3C7E5),
    secondaryContainer = Color(0xFF2E3250),
    onSecondaryContainer = Color(0xFFDFE1F9),
    surfaceContainerLow = Night,
    surfaceContainer = NightSurface,
    surfaceContainerHigh = Color(0xFF262A3E),
    onSurface = Color(0xFFE4E5F2),
    onSurfaceVariant = Color(0xFFB4B7CC),
    background = Color.Black,
)

/** Ambient: grey outlines and text on black, so almost no pixels are lit. */
val AmbientColors = ColorScheme(
    primary = Color(0xFFBDBDBD),
    primaryDim = Color(0xFF9E9E9E),
    primaryContainer = Color.Black,
    onPrimary = Color.Black,
    onPrimaryContainer = Color(0xFFBDBDBD),
    secondary = Color(0xFF9E9E9E),
    secondaryContainer = Color.Black,
    onSecondaryContainer = Color(0xFFBDBDBD),
    surfaceContainerLow = Color.Black,
    surfaceContainer = Color.Black,
    surfaceContainerHigh = Color.Black,
    onSurface = Color(0xFFBDBDBD),
    onSurfaceVariant = Color(0xFF8A8A8A),
    outline = Color(0xFF5A5A5A),
    outlineVariant = Color(0xFF3A3A3A),
    background = Color.Black,
)

@Composable
fun EncoreTheme(ambient: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (ambient) AmbientColors else EncoreColors) {
        CompositionLocalProvider(LocalAmbient provides ambient, content = content)
    }
}
