package com.sethg.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary             = GreenPrimary,
    onPrimary           = GreenOnPrimary,
    primaryContainer    = GreenContainer,
    onPrimaryContainer  = GreenOnContainer,
    secondary           = OchreSecondary,
    secondaryContainer  = OchreContainer,
    onSecondaryContainer= OchreOnContainer,
    background          = LightBackground,
    surface             = LightSurface,
    surfaceVariant      = LightSurfaceVariant,
    onSurface           = TextPrimary,
    onSurfaceVariant    = TextSecondary,
    outline             = LightBorder,
    error               = ErrorColor,
    errorContainer      = ErrorContainer
)

@Composable
fun SethGTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography  = SethGTypography,
        content     = content
    )
}
