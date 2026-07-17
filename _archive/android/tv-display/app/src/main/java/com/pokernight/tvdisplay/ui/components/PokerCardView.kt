package com.pokernight.tvdisplay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.pokernight.tvdisplay.data.model.Card
import com.pokernight.tvdisplay.ui.theme.*

@Composable
fun PokerCardView(
    card: Card,
    modifier: Modifier = Modifier,
    cardWidth: Int = 72,
    cardHeight: Int = 100
) {
    val backgroundColor = if (card.faceUp) CardWhite else Color(0xFF1E3A5F)
    val textColor = if (card.faceUp) {
        if (card.color == "#E53935") Color(0xFFE53935) else Color(0xFF1A1A1A)
    } else CardWhite

    Box(
        modifier = modifier
            .width(cardWidth.dp)
            .height(cardHeight.dp)
            .background(backgroundColor, RoundedCornerShape(8.dp))
            .border(
                width = if (card.faceUp) 1.dp else 2.dp,
                color = if (card.faceUp) Color(0xFFCCCCCC) else Color(0xFF2A4A7F),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (card.faceUp) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = card.rank,
                    color = textColor,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = card.symbol,
                    color = textColor,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            // Card back
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(Color(0xFF1E3A5F), Color(0xFF2A4A7F))
                        ),
                        shape = RoundedCornerShape(4.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🃏",
                    fontSize = 28.sp
                )
            }
        }
    }
}

@Composable
fun CommunityCardsRow(
    cards: List<Card>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Always show 5 slots
        for (i in 0 until 5) {
            if (i < cards.size) {
                PokerCardView(
                    card = cards[i],
                    cardWidth = 72,
                    cardHeight = 100
                )
            } else {
                // Empty card back
                Box(
                    modifier = Modifier
                        .width(72.dp)
                        .height(100.dp)
                        .background(
                            TableGreenDark.copy(alpha = 0.3f),
                            RoundedCornerShape(8.dp)
                        )
                        .border(
                            1.dp,
                            SeatBorder.copy(alpha = 0.3f),
                            RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "?",
                        color = TextMuted.copy(alpha = 0.3f),
                        fontSize = 20.sp
                    )
                }
            }
        }
    }
}
