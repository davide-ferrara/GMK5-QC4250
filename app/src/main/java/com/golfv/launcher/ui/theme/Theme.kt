package com.golfv.launcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GolfColorScheme = darkColorScheme(
    primary = Color(0xFF8DBDEB),
    onPrimary = Color(0xFF06131F),
    surface = Color(0xFF111820),
    onSurface = Color(0xFFE8ECF2),
    background = Color(0xFF090C10),
    onBackground = Color(0xFFE8ECF2),
)

@Composable
fun GolfLauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GolfColorScheme,
        content = content,
    )
}
