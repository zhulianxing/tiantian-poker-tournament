@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.pokernight.tvdisplay.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.pokernight.tvdisplay.data.model.PlayerSeat
import com.pokernight.tvdisplay.data.model.PlayerStatus
import com.pokernight.tvdisplay.ui.theme.*

/**
 * Displays a single player seat.
 */
@Composable
fun PlayerSeatView(
    seat: PlayerSeat,
    modifier: Modifier = Modifier,
) {
    val isEmpty = seat.status == PlayerStatus.EMPTY
    val isEliminated = seat.status == PlayerStatus.ELIMINATED
    val isFolded = seat.status == PlayerStatus.FOLDED
    val isAllIn = seat.status == PlayerStatus.ALL_IN
    val isActing = seat.isActing

    // Blinking animation for acting player
    val infiniteTransition = rememberInfiniteTransition(label = "acting_blink")
    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "border_alpha",
    )

    val seatAlpha = when {
        isEliminated -> EliminatedAlpha
        isFolded -> FoldedAlpha
        isEmpty -> 0.15f
        else -> 1.0f
    }

    val borderColor = when {
        isActing -> RedAction.copy(alpha = borderAlpha)
        isAllIn -> GoldAccent
        else -> SeatBorder
    }

    val borderWidth = when {
        isActing || isAllIn -> 3.dp
        isEmpty -> 1.dp
        else -> 2.dp
    }

    val statusColor = when {
        isAllIn -> GoldAccent
        isFolded -> TextTertiary
        isEliminated -> TextTertiary
        seat.status == PlayerStatus.WAITING -> GoldAccent.copy(alpha = 0.6f)
        else -> TextSecondary
    }

    Box(
        modifier = modifier
            .width(200.dp)
            .height(100.dp)
            .alpha(seatAlpha)
            .clip(RoundedCornerShape(12.dp))
            .background(SeatBg)
            .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isEmpty) {
            Text(
                text = "Empty",
                color = TextTertiary,
                fontSize = 14.sp,
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxSize(),
            ) {
                // Top row: nickname + dealer button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = seat.nickname.ifEmpty { "Player ${seat.seatIndex + 1}" },
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    if (seat.isDealer) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(GoldAccent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "D",
                                color = Color.Black,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                // Middle: chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatChipCount(seat.chipCount),
                        color = ChipGreen,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (seat.currentBet > 0) {
                        Text(
                            text = "Bet: ${seat.currentBet}",
                            color = GoldAccent,
                            fontSize = 14.sp,
                        )
                    }
                }

                // Bottom: status / last action
                if (seat.lastAction.isNotEmpty()) {
                    Text(
                        text = seat.lastAction,
                        color = statusColor,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                } else if (seat.status != PlayerStatus.PLAYING) {
                    Text(
                        text = when (seat.status) {
                            PlayerStatus.WAITING -> "Waiting"
                            PlayerStatus.FOLDED -> "Folded"
                            PlayerStatus.ALL_IN -> "All-In"
                            PlayerStatus.ELIMINATED -> "Eliminated"
                            else -> ""
                        },
                        color = statusColor,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private fun formatChipCount(count: Int): String {
    return when {
        count >= 1_000_000 -> "${count / 1_000_000}M"
        count >= 1_000 -> "${count / 1_000}K"
        else -> count.toString()
    }
}
