package com.pokernight.tvdisplay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.pokernight.tvdisplay.data.model.PlayerSeat
import com.pokernight.tvdisplay.ui.theme.GoldAccent
import com.pokernight.tvdisplay.ui.theme.SeatBg
import com.pokernight.tvdisplay.ui.theme.SeatBorder
import com.pokernight.tvdisplay.ui.theme.TextPrimary
import com.pokernight.tvdisplay.ui.theme.TextSecondary
import com.pokernight.tvdisplay.ui.theme.TextTertiary

/**
 * 实时排行：存活者按筹码降序，淘汰者置底变暗。
 * 与 tv.html 的排行榜排序/字段保持一致（改动需两边同步）。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LeaderboardPanel(
    seats: List<PlayerSeat>,
    modifier: Modifier = Modifier,
    width: Dp = 210.dp,
) {
    val ranked = seats
        .filter { it.status != "empty" }
        .sortedWith(compareBy({ it.status == "eliminated" }, { -it.chipCount }, { it.seatIndex }))

    Column(
        modifier = modifier
            .width(width)
            .fillMaxHeight()
            .background(SeatBg.copy(alpha = 0.85f), RoundedCornerShape(14.dp))
            .border(1.dp, SeatBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = "实时排行",
            color = GoldAccent,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(10.dp))
        ranked.forEachIndexed { index, seat ->
            val eliminated = seat.status == "eliminated"
            val isLeader = index == 0 && !eliminated
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${if (isLeader) "🥇 " else ""}#${index + 1} ${seat.nickname}",
                    color = when {
                        eliminated -> TextTertiary
                        isLeader -> GoldAccent
                        else -> TextPrimary
                    },
                    fontSize = 14.sp,
                    fontWeight = if (isLeader) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (eliminated) "淘汰" else "${seat.chipCount}",
                    color = if (eliminated) TextTertiary else TextSecondary,
                    fontSize = 13.sp,
                )
            }
        }
    }
}
