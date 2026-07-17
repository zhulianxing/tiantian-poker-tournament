package com.pokernight.tvdisplay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokernight.tvdisplay.data.model.PlayerSeat
import com.pokernight.tvdisplay.data.model.SeatStatus
import com.pokernight.tvdisplay.ui.theme.*

@Composable
fun PlayerSeatView(
    seat: PlayerSeat,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        seat.isCurrentActor -> RedAccent
        seat.isDealer -> DealerChip
        seat.status == SeatStatus.EMPTY -> SeatBorder
        else -> SeatBorder
    }
    val borderWidth = when {
        seat.isCurrentActor -> 3.dp
        seat.isDealer -> 2.dp
        else -> 1.dp
    }
    val opacity = when (seat.status) {
        SeatStatus.EMPTY, SeatStatus.FOLDED, SeatStatus.BUSTED -> 0.4f
        else -> 1.0f
    }

    Box(
        modifier = modifier
            .width(160.dp)
            .height(90.dp)
            .background(TableGreenDark.copy(alpha = opacity))
            .border(borderWidth, borderColor, RoundedCornerShape(8.dp))
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        if (seat.status == SeatStatus.EMPTY) {
            Text(
                text = "空座",
                color = TextMuted.copy(alpha = opacity),
                fontSize = 14.sp
            )
            return
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            // Avatar circle + dealer chip
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(avatarColor(seat.seatIndex)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = seat.nickname.firstOrNull()?.toString() ?: "?",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (seat.isDealer) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Gold)
                            .align(Alignment.TopEnd)
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))

            // Nickname
            Text(
                text = seat.nickname.take(6),
                color = TextPrimary.copy(alpha = opacity),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (seat.status != SeatStatus.BUSTED) {
                // Chips
                Text(
                    text = formatChips(seat.chipCount),
                    color = ChipGreen.copy(alpha = opacity),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Status badge
            val statusText = when (seat.status) {
                SeatStatus.WAITING -> "坐"
                SeatStatus.ACTING -> "行动"
                SeatStatus.FOLDED -> "弃牌"
                SeatStatus.ALL_IN -> "All-In"
                SeatStatus.DISCONNECTED -> "断线"
                SeatStatus.BUSTED -> "淘汰"
                SeatStatus.SITOUT -> "暂离"
                else -> ""
            }
            if (statusText.isNotEmpty()) {
                Text(
                    text = statusText,
                    color = when (seat.status) {
                        SeatStatus.ALL_IN -> Gold
                        SeatStatus.FOLDED -> TextMuted
                        SeatStatus.DISCONNECTED -> RedAccent
                        SeatStatus.BUSTED -> RedAccent
                        else -> TextSecondary
                    }.copy(alpha = opacity),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Current bet indicator
            if (seat.currentBet > 0) {
                Text(
                    text = "↓${seat.currentBet}",
                    color = Gold.copy(alpha = opacity),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Last action
            if (!seat.lastAction.isNullOrEmpty()) {
                Text(
                    text = seat.lastAction,
                    color = TextSecondary.copy(alpha = opacity * 0.7f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun avatarColor(index: Int): Color = when (index % 6) {
    0 -> Color(0xFFE53935)
    1 -> Color(0xFF1E88E5)
    2 -> Color(0xFF43A047)
    3 -> Color(0xFFFB8C00)
    4 -> Color(0xFF8E24AA)
    5 -> Color(0xFF00ACC1)
    else -> Color(0xFF666666)
}

private fun formatChips(count: Int): String = when {
    count >= 1000 -> "${count / 1000}.${(count % 1000) / 100}k"
    else -> count.toString()
}
