package com.pokernight.player.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokernight.player.data.model.Card
import com.pokernight.player.data.model.SeatInfo
import com.pokernight.player.data.model.key
import com.pokernight.player.ui.theme.ActionBlue
import com.pokernight.player.ui.theme.ActionGreen
import com.pokernight.player.ui.theme.ActionRed
import com.pokernight.player.ui.theme.Gold
import com.pokernight.player.ui.theme.HairlineWhite
import com.pokernight.player.ui.theme.SeatBg
import com.pokernight.player.ui.theme.TextDim
import com.pokernight.player.ui.theme.TextPrimary

/** 座位卡上的最近动作 → 中文（与网页 actionLabel 一致；原始串形如 "raise 200"） */
private fun lastActionCn(raw: String): String {
    val parts = raw.split(" ")
    val amt = parts.getOrNull(1) ?: ""
    return when (parts.getOrNull(0)?.lowercase()) {
        "fold" -> "弃牌"
        "check" -> "过牌"
        "call" -> if (amt.isNotEmpty()) "跟注 $amt" else "跟注"
        "raise" -> "加注到 $amt"
        "bet" -> "下注 $amt"
        "allin", "all-in", "all_in" -> "全下 $amt"
        "small_blind" -> "小盲 $amt"
        "big_blind" -> "大盲 $amt"
        else -> raw
    }
}

/**
 * 座位卡（对齐网页 .seat）：毛玻璃圆角面板；
 * 行动中 = 金框 + 金色辉光；全下 = 金框微光；赢家 = 金框 + 微金底 + 🏆；
 * 我 = 绿框；弃牌/淘汰/空位降透明度。
 */
@Composable
fun SeatView(
    seat: SeatInfo,
    modifier: Modifier = Modifier,
    isMe: Boolean = false,
    isWinner: Boolean = false,
    revealCards: List<Card> = emptyList(),
    winCards: Set<String> = emptySet(),
) {
    val isAllIn = seat.lastAction.lowercase().let {
        it.startsWith("allin") || it.startsWith("all-in") || it.startsWith("all_in")
    }
    val isFolded = seat.status == "folded" || seat.lastAction.equals("fold", ignoreCase = true)

    val bgColor by animateColorAsState(
        targetValue = when {
            isWinner -> Color(0x14FFD700)  // rgba(255,215,0,.08) 微金底
            else -> SeatBg
        },
        label = "seatBg",
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            seat.isActing -> Gold
            isWinner -> Gold
            isAllIn -> Gold
            isMe -> ActionGreen
            else -> HairlineWhite
        },
        label = "seatBorder",
    )
    val borderWidth = when {
        seat.isActing || isWinner || isMe -> 2.dp
        else -> 1.dp
    }

    // 金色辉光（网页 box-shadow 0 0 22px 金，用放射渐变近似）
    val glowAlpha by animateFloatAsState(
        targetValue = when {
            seat.isActing -> 0.30f
            isWinner -> 0.40f
            isAllIn -> 0.18f
            else -> 0f
        },
        label = "seatGlow",
    )

    val contentAlpha by animateFloatAsState(
        targetValue = when {
            seat.status == "empty" -> 0.25f
            seat.status == "eliminated" -> 0.3f
            revealCards.isNotEmpty() && !isWinner -> 0.35f
            isFolded -> 0.45f
            else -> 1f
        },
        label = "seatAlpha",
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (glowAlpha > 0f) {
            Box(
                Modifier
                    .matchParentSize()
                    .scale(1.22f)
                    .background(
                        Brush.radialGradient(listOf(Gold.copy(alpha = glowAlpha), Color.Transparent)),
                        RoundedCornerShape(16.dp),
                    ),
            )
        }

        Column(
            modifier = Modifier
                .width(88.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(bgColor)
                .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
                .alpha(contentAlpha)
                .padding(vertical = 6.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (seat.status == "empty") {
                Spacer(Modifier.height(4.dp))
                Text("空位", fontSize = 10.sp, color = TextDim, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
            } else {
                // 昵称行：昵称 + （我）+ 庄家 D 徽标（网页 .s-nick / .me-tag / .d-badge）
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = seat.nickname.ifEmpty { "玩家" },
                        fontSize = 11.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isMe) {
                        Text(" 我", fontSize = 9.sp, color = ActionGreen, fontWeight = FontWeight.Bold)
                    }
                    if (seat.isDealer) {
                        Spacer(Modifier.width(3.dp))
                        Box(
                            modifier = Modifier
                                .size(13.dp)
                                .clip(CircleShape)
                                .background(Gold),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("D", fontSize = 8.sp, color = Color(0xFF1A1400), fontWeight = FontWeight.Black)
                        }
                    }
                }

                // 筹码（网页 .s-chips 绿色粗体）
                Text(
                    text = if (seat.status == "eliminated") "已淘汰" else "${seat.chipCount}",
                    fontSize = 13.sp,
                    color = if (seat.status == "eliminated") ActionRed else ActionGreen,
                    fontWeight = FontWeight.ExtraBold,
                )

                // 本轮下注（网页 .s-bet 金色）
                if (seat.currentBet > 0) {
                    Text(
                        text = "下注 ${seat.currentBet}",
                        fontSize = 10.sp,
                        color = Gold,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }

                // 最近动作（网页 .s-last 蓝色中文）
                if (seat.lastAction.isNotEmpty()) {
                    Text(
                        text = lastActionCn(seat.lastAction),
                        fontSize = 10.sp,
                        color = ActionBlue,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // 摊牌亮牌（网页 .reveal：mini-card ×2，属赢家最佳 5 张的金框）
                if (revealCards.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        revealCards.take(2).forEach { c ->
                            MiniCard(card = c, win = c.key() in winCards)
                        }
                    }
                }
            }
        }

        // 赢家徽标（网页 .win-badge：座位右上角 🏆）
        if (isWinner) {
            Text(
                text = "🏆",
                fontSize = 15.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 0.dp, end = 2.dp),
            )
        }
    }
}
