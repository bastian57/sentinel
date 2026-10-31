package com.daftech.sentinel.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val SnBg = Color(0xFF0B0F14)
val SnSurface = Color(0xFF141B24)
val SnSurface2 = Color(0xFF1C2634)
val SnGold = Color(0xFFF59E0B)
val SnCyan = Color(0xFF22D3EE)
val SnText = Color(0xFFF2F4F7)
val SnTextDim = Color(0xFF9AA7B8)
val SnRed = Color(0xFFF87171)
val SnGreen = Color(0xFF34D399)

private val DarkScheme = darkColorScheme(
    primary = SnGold,
    onPrimary = Color(0xFF1A1200),
    secondary = SnCyan,
    onSecondary = Color(0xFF062A33),
    background = SnBg,
    onBackground = SnText,
    surface = SnSurface,
    onSurface = SnText,
    surfaceVariant = SnSurface2,
    onSurfaceVariant = SnTextDim,
    error = SnRed
)

@Composable
fun SentinelTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, content = content)
}
