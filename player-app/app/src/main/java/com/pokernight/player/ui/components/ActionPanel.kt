package com.pokernight.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokernight.player.ui.theme.ActionRed
import com.pokernight.player.ui.theme.AllInTintBg
import com.pokernight.player.ui.theme.CallGreenA
import com.pokernight.player.ui.theme.CallGreenB
import com.pokernight.player.ui.theme.FoldTintBg
import com.pokernight.player.ui.theme.Gold
import com.pokernight.player.ui.theme.GoldLine
import com.pokernight.player.ui.theme.GoldPillEdge
import com.pokernight.player.ui.theme.PanelGlass
import com.pokernight.player.ui.theme.RaiseA
import com.pokernight.player.ui.theme.RaiseB
import com.pokernight.player.ui.theme.TextDim
import com.pokernight.player.ui.theme.White

/**
 * 操作面板（对齐网页 .action-panel）：毛玻璃 + 金线边框圆角面板；
 * 按钮组 = 弃牌红系 / 过牌·跟注绿渐变 / 加注橙金渐变 / 全下金系描边。
 */
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
            .padding(horizontal = 10.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(PanelGlass)
            .border(
                width = 1.dp,
                color = if (isMyTurn) GoldPillEdge else GoldLine,
                shape = RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!isMyTurn) {
            Text(
                text = "等待其他玩家行动…",
                color = TextDim,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        } else {
            ActButton(
                text = "弃牌",
                enabled = true,
                onClick = onFold,
                modifier = Modifier.weight(1f),
                bgColor = FoldTintBg,
                contentColor = ActionRed,
            )
            Spacer(Modifier.width(8.dp))
            ActButton(
                text = if (callAmount == 0) "过牌" else "跟注 $callAmount",
                enabled = true,
                onClick = if (callAmount == 0) onCheck else onCall,
                modifier = Modifier.weight(1f),
                bgBrush = Brush.linearGradient(listOf(CallGreenA, CallGreenB)),
                contentColor = White,
            )
            Spacer(Modifier.width(8.dp))
            ActButton(
                text = if (raiseConfirm) "确认 $raiseAmount" else "加注",
                enabled = raiseAmount in minRaise..myChips,
                onClick = onRaise,
                modifier = Modifier.weight(1f),
                bgBrush = Brush.linearGradient(listOf(RaiseA, RaiseB)),
                contentColor = White,
            )
            Spacer(Modifier.width(8.dp))
            ActButton(
                text = "全下",
                enabled = myChips > 0,
                onClick = onAllIn,
                modifier = Modifier.weight(1f),
                bgColor = AllInTintBg,
                contentColor = Gold,
                borderColor = GoldLine,
            )
        }
    }
}

/** 行动按钮（网页 button.act：禁用 = 30% 透明度） */
@Composable
private fun ActButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bgBrush: Brush? = null,
    bgColor: Color = Color.Transparent,
    contentColor: Color,
    borderColor: Color? = null,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .height(48.dp)
            .alpha(if (enabled) 1f else 0.3f)
            .clip(shape)
            .then(if (bgBrush != null) Modifier.background(bgBrush) else Modifier.background(bgColor))
            .then(if (borderColor != null) Modifier.border(1.dp, borderColor, shape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            maxLines = 1,
        )
    }
}
