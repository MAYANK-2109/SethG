package com.sethg.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Kabadiwala-inspired color palette ─────────────────────────────────────────
// Earthy greens + warm amber — evokes recycling, trust, and approachability

val GreenPrimary     = Color(0xFF2E7D32)   // Deep forest green
val GreenOnPrimary   = Color(0xFFFFFFFF)
val GreenContainer   = Color(0xFFA5D6A7)   // Soft mint green
val GreenOnContainer = Color(0xFF1B5E20)

val AmberSecondary   = Color(0xFFFF8F00)   // Warm amber
val AmberContainer   = Color(0xFFFFE082)
val AmberOnContainer = Color(0xFF7F6000)

val BackgroundDark   = Color(0xFF121212)
val SurfaceDark      = Color(0xFF1E1E1E)
val SurfaceVariant   = Color(0xFF2C2C2C)
val OnSurfaceDark    = Color(0xFFE0E0E0)
val SubText          = Color(0xFF9E9E9E)

val ErrorColor       = Color(0xFFCF6679)

private val DarkColorScheme = darkColorScheme(
    primary          = GreenPrimary,
    onPrimary        = GreenOnPrimary,
    primaryContainer = GreenContainer,
    onPrimaryContainer = GreenOnContainer,
    secondary        = AmberSecondary,
    secondaryContainer = AmberContainer,
    onSecondaryContainer = AmberOnContainer,
    background       = BackgroundDark,
    surface          = SurfaceDark,
    surfaceVariant   = SurfaceVariant,
    onSurface        = OnSurfaceDark,
    onSurfaceVariant = SubText,
    error            = ErrorColor
)

@Composable
fun SethGTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = SethGTypography,
        content     = content
    )
}
