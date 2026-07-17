package com.pokernight.player.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokernight.player.ui.theme.ActionBlue
import com.pokernight.player.ui.theme.ActionGreen
import com.pokernight.player.ui.theme.ActionRed
import com.pokernight.player.ui.theme.DisabledGray
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
    onRaiseAmountChange: (Int) -> Unit,
    onFold: () -> Unit,
    onCheck: () -> Unit,
    onCall: () -> Unit,
    onRaise: () -> Unit,
    onAllIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor = if (isMyTurn) Color(0xCC1A1A2E) else Color(0x66000000)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!isMyTurn) {
            Text(
                text = "等待中…",
                color = DisabledGray,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
        } else {
            ActionButton(
                text = "弃牌",
                color = ActionRed,
                enabled = true,
                onClick = onFold,
            )
            Spacer(Modifier.width(4.dp))
            ActionButton(
                text = if (callAmount == 0) "过牌" else "跟注 $callAmount",
                color = ActionGreen,
                enabled = true,
                onClick = if (callAmount == 0) onCheck else onCall,
            )
            Spacer(Modifier.width(4.dp))
            ActionButton(
                text = "加注 $raiseAmount",
                color = ActionBlue,
                enabled = raiseAmount in minRaise..myChips,
                onClick = onRaise,
            )
            Spacer(Modifier.width(4.dp))
            ActionButton(
                text = "全下",
                color = ActionRed,
                enabled = myChips > 0,
                onClick = onAllIn,
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
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (enabled) color else DisabledGray,
            contentColor = White,
            disabledContainerColor = DisabledGray,
            disabledContentColor = Color.Gray,
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.height(44.dp),
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}
