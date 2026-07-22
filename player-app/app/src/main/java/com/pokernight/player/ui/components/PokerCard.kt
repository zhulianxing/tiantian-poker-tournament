package com.pokernight.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokernight.player.data.model.Card
import com.pokernight.player.data.model.key
import com.pokernight.player.ui.theme.CardBackA
import com.pokernight.player.ui.theme.CardBackB
import com.pokernight.player.ui.theme.CardBackLine
import com.pokernight.player.ui.theme.CardBorder
import com.pokernight.player.ui.theme.CardWhite
import com.pokernight.player.ui.theme.DisabledGray
import com.pokernight.player.ui.theme.Gold
import com.pokernight.player.ui.theme.SuitBlack
import com.pokernight.player.ui.theme.SuitRed

// 服务端直接下发 Unicode 花色字符（♠♥♦♣），同时兼容英文花色名
private fun suitSymbol(suit: String): String = when (suit.lowercase()) {
    "hearts", "h", "♥" -> "♥"
    "diamonds", "d", "♦" -> "♦"
    "clubs", "c", "♣" -> "♣"
    "spades", "s", "♠" -> "♠"
    else -> "?"
}

private fun suitColor(suit: String) = when (suit.lowercase()) {
    "hearts", "diamonds", "h", "d", "♥", "♦" -> SuitRed
    "clubs", "spades", "c", "s", "♣", "♠" -> SuitBlack
    else -> DisabledGray
}

/**
 * 扑克牌（对齐网页 .pcard）：白底、rank 左上角小号、花色居中大号；
 * 我的底牌 isBig = 64×90（网页 .my-cards .pcard），公共牌 44×62。
 * win = 赢家最佳 5 张之一：金色描边 + 金色辉光 + 上移 4dp（网页 .pcard.win）。
 */
@Composable
fun PokerCard(
    card: Card?,
    modifier: Modifier = Modifier,
    faceDown: Boolean = false,
    isBig: Boolean = false,
    win: Boolean = false,
) {
    val w = if (isBig) 64.dp else 44.dp
    val h = if (isBig) 90.dp else 62.dp
    val corner = if (isBig) 8.dp else 6.dp
    val isBack = faceDown || card == null

    Box(modifier = modifier.offset(y = if (win) (-4).dp else 0.dp)) {
        if (win) {
            // 金色辉光（网页 .pcard.win box-shadow 0 0 14px 金，放射渐变近似）
            Box(
                Modifier
                    .matchParentSize()
                    .scale(1.35f)
                    .background(Brush.radialGradient(listOf(Gold.copy(alpha = 0.5f), Color.Transparent))),
            )
        }
        Box(
            modifier = Modifier
                .size(width = w, height = h)
                .shadow(if (isBig) 6.dp else 3.dp, RoundedCornerShape(corner))
                .clip(RoundedCornerShape(corner))
                .background(
                    if (isBack) {
                        // 牌背：网页 .pcard.back 深蓝渐变
                        Brush.linearGradient(listOf(CardBackA, CardBackB))
                    } else {
                        Brush.verticalGradient(listOf(CardWhite, CardWhite))
                    }
                )
                .border(
                    width = if (win) 2.dp else 1.dp,
                    color = when {
                        win -> Gold
                        isBack -> CardBackLine
                        else -> CardBorder
                    },
                    shape = RoundedCornerShape(corner),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (faceDown || card == null) {
                // 网页牌背为纯色渐变，无花纹
            } else if (card.suit.isEmpty() && card.rank.isEmpty()) {
                Text("?", fontSize = if (isBig) 24.sp else 15.sp, color = DisabledGray)
            } else {
                val color = suitColor(card.suit)
                // rank 左上角（网页 .pcard .r）
                Text(
                    text = card.rank.ifEmpty { "?" },
                    fontSize = if (isBig) 16.sp else 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = color,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = if (isBig) 6.dp else 4.dp, top = if (isBig) 4.dp else 3.dp),
                )
                // 花色居中大号（网页 .pcard .s）
                Text(
                    text = suitSymbol(card.suit),
                    fontSize = if (isBig) 28.sp else 19.sp,
                    color = color,
                )
            }
        }
    }
}

/** 迷你牌（网页 .mini-card）：摊牌时亮在座位卡里的底牌；win = 金描边 + 辉光 */
@Composable
fun MiniCard(
    card: Card,
    modifier: Modifier = Modifier,
    win: Boolean = false,
) {
    Box(modifier = modifier) {
        if (win) {
            Box(
                Modifier
                    .matchParentSize()
                    .scale(1.35f)
                    .background(Brush.radialGradient(listOf(Gold.copy(alpha = 0.6f), Color.Transparent))),
            )
        }
        Box(
            modifier = Modifier
                .size(width = 22.dp, height = 30.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(CardWhite)
                .then(if (win) Modifier.border(2.dp, Gold, RoundedCornerShape(4.dp)) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = card.rank + suitSymbol(card.suit),
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                color = suitColor(card.suit),
            )
        }
    }
}

/** 公共牌行：只渲染已发出的牌（网页 .community 不占位）；winCards 中的牌加 win 样式 */
@Composable
fun CommunityCardRow(
    cards: List<Card>,
    modifier: Modifier = Modifier,
    winCards: Set<String> = emptySet(),
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        cards.forEach { PokerCard(card = it, win = it.key() in winCards) }
    }
}
