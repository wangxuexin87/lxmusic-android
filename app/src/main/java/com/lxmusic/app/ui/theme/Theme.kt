package com.lxmusic.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// LX Music 品牌色：青绿渐变风
val BrandGreen = Color(0xFF1DB954)
val BrandTeal = Color(0xFF1ED760)
val BrandDark = Color(0xFF0B0F1A)

/** 主题模式 */
enum class ThemeMode(val label: String) {
    DARK("深色"),
    LIGHT("浅色"),
    AUTO("自动")
}

private val DarkColors = darkColorScheme(
    primary = BrandGreen,
    onPrimary = Color(0xFF06281A),
    primaryContainer = Color(0xFF0E3B28),
    onPrimaryContainer = Color(0xFFB8F5D4),
    secondary = Color(0xFF7DD3A8),
    onSecondary = Color(0xFF06281A),
    background = BrandDark,
    onBackground = Color(0xFFF2F5F9),
    surface = Color(0xFF131A2B),
    onSurface = Color(0xFFF2F5F9),
    surfaceVariant = Color(0xFF1C2538),
    onSurfaceVariant = Color(0xFFA8B3C7),
    surfaceContainerLow = Color(0xFF101624),
    surfaceContainer = Color(0xFF161D30),
    surfaceContainerHigh = Color(0xFF1E2740),
    outline = Color(0xFF2A3550),
    outlineVariant = Color(0xFF1E2740),
    error = Color(0xFFFF8A8A),
    onError = Color(0xFF3A0A0A)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0E9F5B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD2F2E0),
    onPrimaryContainer = Color(0xFF06281A),
    secondary = Color(0xFF0E7A45),
    onSecondary = Color.White,
    background = Color(0xFFF6F8F7),
    onBackground = Color(0xFF101418),
    surface = Color.White,
    onSurface = Color(0xFF101418),
    surfaceVariant = Color(0xFFE8EDEB),
    onSurfaceVariant = Color(0xFF4A5560),
    surfaceContainerLow = Color(0xFFEFF3F1),
    surfaceContainer = Color(0xFFE8EDEB),
    surfaceContainerHigh = Color(0xFFDDE4E1),
    outline = Color(0xFFCBD5D1),
    outlineVariant = Color(0xFFE0E7E4),
    error = Color(0xFFD32F2F),
    onError = Color.White
)

/**
 * 是否使用深色：AUTO 模式下 6:00-18:00 为浅色，其余为深色
 */
fun isDarkTheme(mode: ThemeMode): Boolean {
    return when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.AUTO -> {
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            hour < 6 || hour >= 18
        }
    }
}

@Composable
fun LxTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
