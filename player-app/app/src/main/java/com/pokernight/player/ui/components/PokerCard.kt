package com.pokernight.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokernight.player.data.model.Card
import com.pokernight.player.ui.theme.CardWhite
import com.pokernight.player.ui.theme.DisabledGray
import com.pokernight.player.ui.theme.Gold
import com.pokernight.player.ui.theme.GoldDark
import com.pokernight.player.ui.theme.SuitBlack
import com.pokernight.player.ui.theme.SuitRed

private fun suitSymbol(suit: String): String = when (suit.lowercase()) {
    "hearts" -> "♥"
    "diamonds" -> "♦"
    "clubs" -> "♣"
    "spades" -> "♠"
    else -> "?"
}

private fun suitColor(suit: String): Color = when (suit.lowercase()) {
    "hearts", "diamonds" -> SuitRed
    "clubs", "spades" -> SuitBlack
    else -> DisabledGray
}

@Composable
fun PokerCard(
    card: Card?,
    modifier: Modifier = Modifier,
    faceDown: Boolean = false,
    isBig: Boolean = false,
) {
    val w = if (isBig) 74.dp else 46.dp
    val h = if (isBig) 104.dp else 64.dp
    val corner = if (isBig) 10.dp else 7.dp

    Box(
        modifier = modifier
            .size(width = w, height = h)
            .shadow(if (isBig) 8.dp else 4.dp, RoundedCornerShape(corner))
            .clip(RoundedCornerShape(corner))
            .background(
                if (faceDown || card == null) {
                    // 牌背：深蓝金纹
                    Brush.linearGradient(
                        listOf(Color(0xFF16213E), Color(0xFF0F3460), Color(0xFF16213E))
                    )
                } else {
                    Brush.verticalGradient(listOf(CardWhite, Color(0xFFEDEDE4)))
                }
            )
            .border(
                width = if (isBig) 1.5.dp else 1.dp,
                color = if (faceDown || card == null) GoldDark else Color(0xFFD8D8CE),
                shape = RoundedCornerShape(corner),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (faceDown || card == null) {
            // 牌背花纹：内框 + 黑桃标
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isBig) 6.dp else 4.dp)
                    .border(1.dp, Gold.copy(alpha = 0.5f), RoundedCornerShape(if (isBig) 6.dp else 4.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "♠",
                    fontSize = if (isBig) 34.sp else 20.sp,
                    color = Gold,
                )
            }
        } else if (card.suit.isEmpty() && card.rank.isEmpty()) {
            Text("?", fontSize = if (isBig) 28.sp else 16.sp, color = DisabledGray)
        } else {
            val color = suitColor(card.suit)
            val rankFs = if (isBig) 30.sp else 17.sp
            val suitFs = if (isBig) 24.sp else 14.sp
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = card.rank.ifEmpty { "?" },
                    fontSize = rankFs,
                    fontWeight = FontWeight.ExtraBold,
                    color = color,
                )
                Text(
                    text = suitSymbol(card.suit),
                    fontSize = suitFs,
                    color = color,
                )
            }
        }
    }
}

@Composable
fun CommunityCardRow(
    cards: List<Card>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until 5) {
            PokerCard(
                card = cards.getOrNull(i),
                faceDown = cards.getOrNull(i) == null,
            )
        }
    }
}
