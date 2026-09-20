package com.golfv.launcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.golfv.launcher.R

enum class AccentTheme(
    val preferenceValue: String,
    val labelRes: Int,
    val primary: Color,
    val highlight: Color,
    val onHighlight: Color,
    val gradientStart: Color,
    val gradientEnd: Color,
) {
    VolkswagenBlue(
        "volkswagen-blue",
        R.string.accent_theme_volkswagen,
        Color(0xFF236DA8),
        Color(0xFF8FD3FF),
        Color(0xFF06131F),
        Color(0xFF17212D),
        Color(0xFF090D13),
    ),
    InstrumentRed(
        "instrument-red",
        R.string.accent_theme_red,
        Color(0xFFB32632),
        Color(0xFFFF858C),
        Color(0xFF27070A),
        Color(0xFF291419),
        Color(0xFF10090C),
    );

    companion object {
        fun fromPreference(value: String?): AccentTheme =
            entries.firstOrNull { it.preferenceValue == value } ?: VolkswagenBlue
    }
}

@Composable
fun GolfLauncherTheme(
    accentTheme: AccentTheme,
    content: @Composable () -> Unit,
) {
    val colorScheme = darkColorScheme(
        primary = accentTheme.primary,
        onPrimary = Color.White,
        secondary = accentTheme.highlight,
        onSecondary = accentTheme.onHighlight,
        surface = Color(0xFF111820),
        onSurface = Color(0xFFE8ECF2),
        background = Color(0xFF090C10),
        onBackground = Color(0xFFE8ECF2),
    )
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
