package com.pokernight.tvdisplay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokernight.tvdisplay.ui.theme.*

@Composable
fun TopBar(
    tableCode: String,
    level: Int,
    maxLevel: Int,
    smallBlind: Int,
    bigBlind: Int,
    blindCountdown: Int,
    playerCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Background.copy(alpha = 0.9f))
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: table code
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "🃏",
                fontSize = 18.sp
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = tableCode,
                color = Gold,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Center: blind info
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InfoChip("盲注", "$smallBlind/$bigBlind")
            InfoChip("级别", "$level/$maxLevel")
            InfoChip("玩家", "$playerCount/6")
        }

        // Right: countdown
        Row(verticalAlignment = Alignment.CenterVertically) {
            val minutes = blindCountdown / 60
            val seconds = blindCountdown % 60
            Text(
                text = "⏱ ${minutes}:${seconds.toString().padStart(2, '0')}",
                color = if (blindCountdown < 60) RedAccent else TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun InfoChip(label: String, value: String) {
    Row(
        modifier = Modifier
            .background(Surface, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label: ",
            color = TextSecondary,
            fontSize = 14.sp
        )
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun PotDisplay(
    mainPot: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(PotBackground, RoundedCornerShape(12.dp))
            .border(1.dp, Gold.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(horizontal = 24.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "底池: ",
                color = TextSecondary,
                fontSize = 16.sp
            )
            Text(
                text = mainPot.toString(),
                color = Gold,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
