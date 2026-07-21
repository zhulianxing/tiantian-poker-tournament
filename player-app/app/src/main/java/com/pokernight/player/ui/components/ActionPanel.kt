package com.pokernight.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokernight.player.ui.theme.ActionFoldGray
import com.pokernight.player.ui.theme.ActionAllInRed
import com.pokernight.player.ui.theme.ActionGreen
import com.pokernight.player.ui.theme.ActionOrange
import com.pokernight.player.ui.theme.BgElevated
import com.pokernight.player.ui.theme.DisabledGray
import com.pokernight.player.ui.theme.Gold
import com.pokernight.player.ui.theme.SurfaceBorder
import com.pokernight.player.ui.theme.TextTertiary
import com.pokernight.player.ui.theme.White

@Composable
fun ActionPanel(
    isMyTurn: Boolean,
    currentBet: Int,
    myCurrentBet: Int,
    myChips: Int,
    callAmount: Int,
    minRaise: Int,
    raiseAmount: Int,
    raiseConfirm: Boolean = false,
    onRaiseAmountChange: (Int) -> Unit,
    onFold: () -> Unit,
    onCheck: () -> Unit,
    onCall: () -> Unit,
    onRaise: () -> Unit,
    onAllIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(BgElevated, Color(0xFF0D0D14))
                )
            )
            .border(
                width = 1.dp,
                color = if (isMyTurn) Gold.copy(alpha = 0.35f) else SurfaceBorder,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            )
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!isMyTurn) {
            Text(
                text = "等待其他玩家行动…",
                color = TextTertiary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
        } else {
            ActionButton(
                text = "弃牌",
                color = ActionFoldGray,
                enabled = true,
                onClick = onFold,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            ActionButton(
                text = if (callAmount == 0) "过牌" else "跟注 $callAmount",
                color = ActionGreen,
                enabled = true,
                onClick = if (callAmount == 0) onCheck else onCall,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            ActionButton(
                text = if (raiseConfirm) "确认 $raiseAmount" else "加注",
                color = ActionOrange,
                enabled = raiseAmount in minRaise..myChips,
                onClick = onRaise,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            ActionButton(
                text = "全下",
                color = ActionAllInRed,
                enabled = myChips > 0,
                onClick = onAllIn,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = White,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = contentColor,
            disabledContainerColor = DisabledGray,
            disabledContentColor = Color(0xFF888888),
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 4.dp,
            pressedElevation = 1.dp,
        ),
        modifier = modifier.height(48.dp),
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}
