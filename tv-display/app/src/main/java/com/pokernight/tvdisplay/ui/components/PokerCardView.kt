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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.pokernight.tvdisplay.data.model.Card
import com.pokernight.tvdisplay.ui.theme.CardWhite
import com.pokernight.tvdisplay.ui.theme.RedAction
import com.pokernight.tvdisplay.ui.theme.GoldAccent

/**
 * Classic playing card — rank in corners, suit in center.
 * Matches mainstream poker broadcast style (WSOP, EPT, etc.).
 */
@Composable
fun PokerCardView(
    card: Card?,
    modifier: Modifier = Modifier,
    faceDown: Boolean = false,
    highlighted: Boolean = false,
    cardWidth: androidx.compose.ui.unit.Dp = 64.dp,
    cardHeight: androidx.compose.ui.unit.Dp = 90.dp,
) {
    val isCardBack = faceDown || card == null
    val scale = cardHeight.value / 90f

    Box(
        modifier = modifier
            .width(cardWidth)
            .height(cardHeight)
            .shadow(
                elevation = if (highlighted) 6.dp else 3.dp,
                shape = RoundedCornerShape(8.dp),
                ambientColor = Color.Black.copy(alpha = 0.3f),
                spotColor = Color.Black.copy(alpha = 0.4f),
            )
            .clip(RoundedCornerShape(8.dp))
            .background(if (isCardBack) CardBack else CardWhite)
            .border(
                width = if (highlighted) 2.dp else (1.5.dp),
                color = if (highlighted) GoldAccent else CardBorder,
                shape = RoundedCornerShape(8.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (isCardBack) {
            // ── Classic card back with diamond pattern ──
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF152B45)),
                contentAlignment = Alignment.Center,
            ) {
                // Outer diamond
                Box(
                    modifier = Modifier
                        .size((48 * scale).dp)
                        .background(Color(0xFF1E3A5F), RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("♠", color = Color(0xFF2A5080), fontSize = (28 * scale).sp)
                }
                // Center dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(GoldAccent.copy(alpha = 0.5f)),
                )
            }
        } else {
            // ── Classic card face ──
            val c = card!!  // safe: we're in the non-card-back branch
            val cardColor = if (c.isRed) RedAction else CardTextBlack

            // Corner rank (top-left)
            Text(
                text = c.displayRank,
                color = cardColor,
                fontSize = (16 * scale).sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 5.dp, top = 3.dp),
            )

            // Large suit in center
            Text(
                text = c.suitSymbol,
                color = cardColor,
                fontSize = (28 * scale).sp,
                modifier = Modifier.align(Alignment.Center),
            )

            // Corner rank (bottom-right)
            Text(
                text = c.displayRank,
                color = cardColor,
                fontSize = (16 * scale).sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 5.dp, bottom = 3.dp),
            )
        }
    }
}

// ── Card colors ──
private val CardBack = Color(0xFF0F2440)
private val CardBorder = Color(0xFF999999)
private val CardTextBlack = Color(0xFF1A1A1A)
