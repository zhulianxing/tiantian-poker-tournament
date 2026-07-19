package com.pokernight.player.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokernight.player.data.model.SeatInfo
import com.pokernight.player.ui.theme.ActionRed
import com.pokernight.player.ui.theme.DisabledGray
import com.pokernight.player.ui.theme.Gold
import com.pokernight.player.ui.theme.SurfaceCard
import com.pokernight.player.ui.theme.SurfaceBorder
import com.pokernight.player.ui.theme.TableGreen
import com.pokernight.player.ui.theme.TextTertiary
import com.pokernight.player.ui.theme.White

@Composable
fun SeatView(
    seat: SeatInfo,
    modifier: Modifier = Modifier,
    isMe: Boolean = false,
) {
    val bgColor by animateColorAsState(
        targetValue = when {
            seat.status == "empty" -> Color(0x22000000)
            seat.isActing -> Gold.copy(alpha = 0.18f)
            seat.status == "eliminated" -> Color(0x22E5484D)
            else -> SurfaceCard.copy(alpha = 0.85f)
        },
        label = "seatBg",
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            seat.isActing -> Gold
            isMe -> Color(0xFF46A758)
            seat.status == "eliminated" -> ActionRed.copy(alpha = 0.6f)
            seat.status == "empty" -> SurfaceBorder
            else -> TableGreen.copy(alpha = 0.8f)
        },
        label = "seatBorder",
    )

    val contentAlpha by animateFloatAsState(
        targetValue = if (seat.status == "eliminated") 0.4f else 1f,
        label = "seatAlpha",
    )

    Column(
        modifier = modifier
            .width(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(
                width = if (seat.isActing) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp),
            )
            .alpha(contentAlpha)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (seat.status == "empty") {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "空位",
                fontSize = 11.sp,
                color = TextTertiary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
        } else {
            // Avatar
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(TableGreen, Color(0xFF062B17))
                        )
                    )
                    .border(1.5.dp, Gold.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (seat.isDealer) "D" else "♠",
                    fontSize = 15.sp,
                    color = Gold,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(3.dp))

            Text(
                text = seat.nickname.ifEmpty { "玩家" },
                fontSize = 10.sp,
                color = White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
            )

            Text(
                text = if (seat.status == "eliminated") "已淘汰" else "${seat.chipCount}",
                fontSize = 11.sp,
                color = if (seat.status == "eliminated") ActionRed else Gold,
                fontWeight = FontWeight.Bold,
            )

            if (seat.lastAction.isNotEmpty()) {
                Text(
                    text = seat.lastAction,
                    fontSize = 9.sp,
                    color = White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
