package com.pokernight.tvdisplay.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

@OptIn(ExperimentalTvMaterial3Api::class)
private val TvColorScheme = darkColorScheme(
    primary = GoldAccent,
    onPrimary = Color.Black,
    secondary = ChipGreen,
    onSecondary = Color.Black,
    tertiary = RedAction,
    onTertiary = Color.White,
    background = BgDark,
    onBackground = TextPrimary,
    surface = SeatBg,
    onSurface = TextPrimary,
    error = RedAction,
    onError = Color.White,
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TVDisplayTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = TvColorScheme,
        content = content,
    )
}
