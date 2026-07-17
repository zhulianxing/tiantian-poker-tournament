package com.pokernight.tvdisplay.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Gold,
    secondary = ChipGreen,
    tertiary = RaiseBlue,
    background = Background,
    surface = Surface,
    onPrimary = Background,
    onSecondary = Background,
    onTertiary = CardWhite,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = RedAccent,
    onError = CardWhite
)

@Composable
fun PokerNightTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
