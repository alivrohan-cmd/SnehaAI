package com.sneha.ai.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF8B7CFF), onPrimary = Color(0xFF14102E),
    secondary = Color(0xFF5AA9FF),
    background = Color(0xFF0B0D17), onBackground = Color(0xFFE8E9F5),
    surface = Color(0xFF141726), onSurface = Color(0xFFE8E9F5),
    surfaceVariant = Color(0xFF1E2236), onSurfaceVariant = Color(0xFFC9CBE0),
    error = Color(0xFFFF6B6B),
)
private val LightScheme = lightColorScheme(
    primary = Color(0xFF5B4BDB), onPrimary = Color.White,
    secondary = Color(0xFF2F7DE1),
    background = Color(0xFFF6F6FB), onBackground = Color(0xFF14152A),
    surface = Color.White, onSurface = Color(0xFF14152A),
    surfaceVariant = Color(0xFFE6E7F5), onSurfaceVariant = Color(0xFF3A3C55),
)

@Composable
fun SnehaTheme(mode: String, fontScale: Float, content: @Composable () -> Unit) {
    val dark = when (mode) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }
    val d = LocalDensity.current
    val base = MaterialTheme.typography
    val typo = base.copy(
        bodyLarge = base.bodyLarge.copy(lineHeight = 26.sp),
        bodyMedium = base.bodyMedium.copy(lineHeight = 22.sp),
    )
    CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale * fontScale)) {
        MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, typography = typo, content = content)
    }
}
