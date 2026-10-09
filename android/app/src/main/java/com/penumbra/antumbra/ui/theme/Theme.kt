package com.penumbra.antumbra.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Deep void + single accent (lunar amber)
private val Void = Color(0xFF0A0A0C)
private val Surface = Color(0xFF121216)
private val SurfaceHigh = Color(0xFF1A1A20)
private val Outline = Color(0xFF2A2A32)
private val Amber = Color(0xFFE8A838)
private val AmberDim = Color(0xFFB87A20)
private val TextPrimary = Color(0xFFE8E6E3)
private val TextSecondary = Color(0xFF9A9890)
private val Danger = Color(0xFFCF4A4A)
private val Success = Color(0xFF4ACF8A)

private val DarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = Void,
    primaryContainer = AmberDim,
    onPrimaryContainer = TextPrimary,
    secondary = TextSecondary,
    onSecondary = Void,
    background = Void,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = TextSecondary,
    outline = Outline,
    error = Danger,
    onError = TextPrimary,
)

@Composable
fun AntumbraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = Typography(),
        content = content
    )
}

object AntumbraColors {
    val void = Void
    val surface = Surface
    val surfaceHigh = SurfaceHigh
    val outline = Outline
    val amber = Amber
    val amberDim = AmberDim
    val textPrimary = TextPrimary
    val textSecondary = TextSecondary
    val danger = Danger
    val success = Success
}
