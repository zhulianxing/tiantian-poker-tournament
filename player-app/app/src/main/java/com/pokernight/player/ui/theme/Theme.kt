package com.pokernight.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val PokerColorScheme = darkColorScheme(
    primary = Gold,
    secondary = TableGreen,
    tertiary = ActionBlue,
    background = BgDark,
    surface = DarkSurface,
    onPrimary = Black,
    onSecondary = White,
    onBackground = White,
    onSurface = White,
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
