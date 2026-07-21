@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.pokernight.tvdisplay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.pokernight.tvdisplay.ui.theme.*

/**
 * Bottom bar with hand history and disconnect button.
 */
@Composable
fun BottomBar(
    handHistory: List<String>,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SeatBg)
            .border(1.dp, SeatBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left: Hand history (latest 2)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "牌局记录",
                color = GoldAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            if (handHistory.isEmpty()) {
                Text(
                    text = "暂无记录",
                    color = TextTertiary,
                    fontSize = 12.sp,
                )
            } else {
                handHistory.takeLast(2).reversed().forEach { entry ->
                    Text(
                        text = entry,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                }
            }
        }

        // Right: Disconnect button
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(RedAction.copy(alpha = 0.2f))
                .clickable { onDisconnect() }
                .padding(horizontal = 24.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "断开连接",
                color = RedAction,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
