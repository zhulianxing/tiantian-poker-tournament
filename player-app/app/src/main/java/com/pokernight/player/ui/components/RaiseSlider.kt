package com.pokernight.player.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokernight.player.ui.theme.ActionBlue
import com.pokernight.player.ui.theme.Gold
import com.pokernight.player.ui.theme.White

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
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "最小: $minValue",
                color = White.copy(alpha = 0.7f),
                fontSize = 11.sp,
            )
            Text(
                text = "$currentValue",
                color = Gold,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "最大: $maxValue",
                color = White.copy(alpha = 0.7f),
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.height(2.dp))
        Slider(
            value = progress,
            onValueChange = { fraction ->
                val newVal = minValue + ((maxValue - minValue) * fraction).toInt()
                onValueChange(newVal)
            },
            colors = SliderDefaults.colors(
                thumbColor = Gold,
                activeTrackColor = ActionBlue,
                inactiveTrackColor = Color(0xFF333355),
            ),
        )
    }
}
