package com.lxmusic.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF10B981),
    onPrimary = Color.White,
    background = Color(0xFF0F1420),
    surface = Color(0xFF161C2C),
    onBackground = Color(0xFFE8ECF4),
    onSurface = Color(0xFFE8ECF4)
)

@Composable
fun LxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}
