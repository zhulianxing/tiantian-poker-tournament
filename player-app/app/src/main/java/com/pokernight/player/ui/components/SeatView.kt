package com.pokernight.player.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.draw.clip
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
import com.pokernight.player.ui.theme.TableGreen
import com.pokernight.player.ui.theme.White

@Composable
fun SeatView(
    seat: SeatInfo,
    modifier: Modifier = Modifier,
    isMe: Boolean = false,
) {
    val bgColor by animateColorAsState(
        targetValue = when {
            seat.status == "empty" -> Color(0x33000000)
            seat.isActing -> Gold.copy(alpha = 0.3f)
            seat.status == "eliminated" -> Color(0x22FF0000)
            isMe -> Color(0x2200FF00)
            else -> Color(0x33005500)
        },
        label = "seatBg",
    )

    val borderColor = when {
        seat.isActing -> Gold
        isMe -> Color(0xFF4CAF50)
        seat.status == "eliminated" -> ActionRed
        seat.status == "empty" -> DisabledGray
        else -> TableGreen
    }

    Column(
        modifier = modifier
            .width(76.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(10.dp))
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (seat.status == "empty") {
            Text(
                text = "空位",
                fontSize = 11.sp,
                color = DisabledGray,
                textAlign = TextAlign.Center,
            )
        } else {
            // Avatar
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(TableGreen),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "🃏",
                    fontSize = 18.sp,
                )
            }

            Spacer(Modifier.height(2.dp))

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

            if (seat.isDealer) {
                Text(
                    text = "D",
                    fontSize = 9.sp,
                    color = Gold,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (seat.lastAction.isNotEmpty()) {
                Text(
                    text = seat.lastAction,
                    fontSize = 9.sp,
                    color = White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
