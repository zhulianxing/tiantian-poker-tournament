@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.pokernight.tvdisplay.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.pokernight.tvdisplay.data.model.WinnerBanner
import com.pokernight.tvdisplay.ui.theme.GoldAccent

/**
 * 赢家横幅：每手结束后从顶部落下，下一手开始自动消失（由 ViewModel 清除）。
 * TV 盒子性能有限，只用一次滑入+淡入，不用循环动画。
 */
@Composable
fun WinnerBannerView(
    banner: WinnerBanner?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = banner != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        val text = buildString {
            append("🏆 ")
            append(banner?.winnerName ?: "")
            append(" 赢得 ")
            append(formatAmount(banner?.winAmount ?: 0))
            if (!banner?.handName.isNullOrEmpty()) {
                append(" · ")
                append(banner?.handName)
            }
        }
        Row(
            modifier = Modifier
                .background(
                    brush = Brush.horizontalGradient(
                        listOf(Color(0x66FFD700), Color(0x33FFD700), Color(0x66FFD700))
                    ),
                    shape = RoundedCornerShape(28.dp),
                )
                .border(2.dp, GoldAccent, RoundedCornerShape(28.dp))
                .padding(horizontal = 32.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                color = GoldAccent,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
            )
        }
    }
}

private fun formatAmount(amount: Int): String {
    return when {
        amount >= 1_000_000 -> "${amount / 1_000_000}M"
        amount >= 10_000 -> "${amount / 1_000}K"
        else -> amount.toString()
    }
}
