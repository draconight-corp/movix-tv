package com.example.movix.mobile

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val MovixRed = Color(0xFFE50914)
val MovixBackground = Color(0xFF0B0B0F)
val MovixSurface = Color(0xFF17171F)

private val colors = darkColorScheme(
    primary = MovixRed,
    onPrimary = Color.White,
    secondary = Color(0xFFFF5A5F),
    background = MovixBackground,
    onBackground = Color.White,
    surface = MovixBackground,
    onSurface = Color.White,
    surfaceVariant = MovixSurface,
    onSurfaceVariant = Color(0xFFB8B8C4),
    surfaceContainer = MovixSurface,
    surfaceContainerHigh = Color(0xFF20202A),
    surfaceContainerLow = Color(0xFF121218),
    secondaryContainer = Color(0xFF3A1215),
    onSecondaryContainer = Color.White
)

@Composable
fun MovixTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
