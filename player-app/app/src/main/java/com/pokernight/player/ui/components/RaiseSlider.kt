package com.pokernight.player.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pokernight.player.ui.theme.Gold
import com.pokernight.player.ui.theme.HairlineWhite

/** 加注滑杆（网页 input[type=range]：accent 金色） */
@Composable
fun RaiseSlider(
    minValue: Int,
    maxValue: Int,
    currentValue: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (maxValue <= minValue) return

    val progress = if (maxValue > minValue) {
        (currentValue - minValue).toFloat() / (maxValue - minValue).toFloat()
    } else 0f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
    ) {
        Slider(
            value = progress,
            onValueChange = { fraction ->
                val newVal = minValue + ((maxValue - minValue) * fraction).toInt()
                onValueChange(newVal)
            },
            colors = SliderDefaults.colors(
                thumbColor = Gold,
                activeTrackColor = Gold,
                inactiveTrackColor = HairlineWhite,
            ),
        )
    }
}
