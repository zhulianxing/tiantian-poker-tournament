@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.pokernight.tvdisplay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.pokernight.tvdisplay.data.model.Card
import com.pokernight.tvdisplay.ui.theme.CardWhite
import com.pokernight.tvdisplay.ui.theme.RedAction
import com.pokernight.tvdisplay.ui.theme.GoldAccent

/**
 * Displays a single poker card.
 * @param card The card to display
 * @param modifier Modifier
 * @param faceDown If true, shows card back
 */
@Composable
fun PokerCardView(
    card: Card?,
    modifier: Modifier = Modifier,
    faceDown: Boolean = false,
    highlighted: Boolean = false,
) {
    Box(
        modifier = modifier
            .width(56.dp)
            .height(80.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (faceDown || card == null) {
                    Color(0xFF1E3A5F)
                } else {
                    CardWhite
                }
            )
            .border(
                width = if (highlighted) 2.dp else 1.dp,
                color = if (highlighted) GoldAccent else Color(0xFF888888),
                shape = RoundedCornerShape(6.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (faceDown || card == null) {
            // Card back pattern
            Text(
                text = "♠",
                color = Color(0xFF2A5080),
                fontSize = 32.sp,
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = card.displayRank,
                    color = if (card.isRed) RedAction else Color.Black,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = card.suitSymbol,
                    color = if (card.isRed) RedAction else Color.Black,
                    fontSize = 20.sp,
                )
            }
        }
    }
}

/**
 * Displays a row of community cards.
 * @param cards List of cards (up to 5)
 * @param modifier Modifier
 */
@Composable
fun CommunityCardsRow(
    cards: List<Card>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until 5) {
            val card = cards.getOrNull(i)
            if (card != null) {
                PokerCardView(card = card)
            } else {
                // Empty placeholder
                Box(
                    modifier = Modifier
                        .width(56.dp)
                        .height(80.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x22FFFFFF))
                        .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(6.dp)),
                )
            }
        }
    }
}
