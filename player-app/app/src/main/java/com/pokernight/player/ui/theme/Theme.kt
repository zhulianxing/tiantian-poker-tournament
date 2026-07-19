package com.pokernight.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val PokerColorScheme = darkColorScheme(
    primary = Gold,
    onPrimary = Black,
    secondary = TableGreen,
    onSecondary = White,
    tertiary = ActionBlue,
    background = BgDark,
    onBackground = White,
    surface = SurfaceCard,
    onSurface = White,
    surfaceVariant = BgElevated,
    onSurfaceVariant = TextSecondary,
    outline = SurfaceBorder,
    error = ActionRed,
)

@Composable
fun PokerNightTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = PokerColorScheme,
        typography = Typography,
        content = content,
    )
}
