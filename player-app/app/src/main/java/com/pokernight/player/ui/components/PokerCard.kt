package com.pokernight.player.ui.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokernight.player.data.model.Card
import com.pokernight.player.ui.theme.CardWhite
import com.pokernight.player.ui.theme.DisabledGray
import com.pokernight.player.ui.theme.Gold

private fun suitSymbol(suit: String): String = when (suit.lowercase()) {
    "hearts" -> "♥"
    "diamonds" -> "♦"
    "clubs" -> "♣"
    "spades" -> "♠"
    else -> "?"
}

private fun suitColor(suit: String): Color = when (suit.lowercase()) {
    "hearts", "diamonds" -> Color.Red
    "clubs", "spades" -> Color.Black
    else -> DisabledGray
}

@Composable
fun PokerCard(
    card: Card?,
    modifier: Modifier = Modifier,
    faceDown: Boolean = false,
    isBig: Boolean = false,
) {
    val w = if (isBig) 70.dp else 44.dp
    val h = if (isBig) 100.dp else 62.dp
    val fs = if (isBig) 28.sp else 16.sp
    val ss = if (isBig) 22.sp else 14.sp

    Box(
        modifier = modifier
            .size(width = w, height = h)
            .clip(RoundedCornerShape(if (isBig) 10.dp else 6.dp))
            .background(
                if (faceDown || card == null) {
                    Brush.verticalGradient(
                        listOf(Color(0xFF1a237e), Color(0xFF0d47a1))
                    )
                } else {
                    Brush.verticalGradient(listOf(CardWhite, Color(0xFFE8E8E0)))
                }
            )
            .border(
                width = if (isBig) 2.dp else 1.dp,
                color = if (faceDown || card == null) Gold else Color(0xFFCCCCCC),
                shape = RoundedCornerShape(if (isBig) 10.dp else 6.dp),
            )
            .padding(if (isBig) 6.dp else 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (faceDown || card == null) {
            Text(
                text = "🂠",
                fontSize = if (isBig) 40.sp else 24.sp,
                color = Gold,
            )
        } else {
            if (card.suit.isEmpty() && card.rank.isEmpty()) {
                Text("?", fontSize = fs, color = DisabledGray)
            } else {
                val color = suitColor(card.suit)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = card.rank.ifEmpty { "?" },
                        fontSize = fs,
                        fontWeight = FontWeight.Bold,
                        color = color,
                    )
                    Text(
                        text = suitSymbol(card.suit),
                        fontSize = ss,
                        color = color,
                    )
                }
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
