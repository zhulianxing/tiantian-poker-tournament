@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.pokernight.tvdisplay.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.Text
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.pokernight.tvdisplay.data.model.PlayerSeat
import com.pokernight.tvdisplay.data.model.TableState
import com.pokernight.tvdisplay.ui.components.BottomBar
import com.pokernight.tvdisplay.ui.components.PlayerSeatView
import com.pokernight.tvdisplay.ui.components.PokerCardView
import com.pokernight.tvdisplay.ui.components.TopBar
import com.pokernight.tvdisplay.ui.theme.*

/**
 * Main table screen — shows the full poker table with seats, community cards, and pot.
 * Only used when phase == "started".
 */
@Composable
fun TableScreen(
    state: TableState,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // Top bar
        TopBar(state = state)

        Spacer(modifier = Modifier.height(8.dp))

        // Center: Poker table
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            PokerTableContent(state = state)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Bottom bar
        BottomBar(
            handHistory = state.handHistory,
            onDisconnect = onDisconnect,
        )
    }
}

/**
 * The poker table with 6 seats arranged (3 top, 3 bottom) and community cards in center.
 */
@Composable
private fun PokerTableContent(state: TableState) {
    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        // 牌桌：木质桌沿 + 绿色毡面（双层椭圆）
        Box(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(130.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(TableRimLight, TableRim, Color(0xFF241A0C)),
                    )
                )
                .border(2.dp, GoldDark.copy(alpha = 0.5f), RoundedCornerShape(130.dp))
                .padding(14.dp)
                .clip(RoundedCornerShape(116.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(TableGreen, TableGreenDark),
                    )
                )
                .border(1.dp, GoldAccent.copy(alpha = 0.25f), RoundedCornerShape(116.dp)),
        ) {
            // Table content
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Top 3 seats
                SeatRow(
                    seats = state.seats,
                    indices = listOf(3, 4, 5),
                    modifier = Modifier.padding(top = 16.dp, start = 24.dp, end = 24.dp),
                )

                // Center: community cards + pot
                CenterArea(state = state)

                // Bottom 3 seats
                SeatRow(
                    seats = state.seats,
                    indices = listOf(0, 1, 2),
                    modifier = Modifier.padding(bottom = 16.dp, start = 24.dp, end = 24.dp),
                )
            }
        }
    }
}

/**
 * Row of 3 seats.
 */
@Composable
private fun SeatRow(
    seats: List<PlayerSeat>,
    indices: List<Int>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        indices.forEach { index ->
            val seat = seats.getOrNull(index) ?: PlayerSeat(seatIndex = index)
            PlayerSeatView(seat = seat)
        }
    }
}

/**
 * Center area with community cards and pot.
 * Mainstream poker broadcast layout: cards centered, stage badge, pot info below.
 * Uses Box with absolute positioning (works around Android TV multi-child bug).
 */
@Composable
private fun CenterArea(state: TableState) {
    val potText = formatPot(state.pot)
    val cards = state.communityCards
    val cardSpacing = 86.dp  // 64w + 22dp gap

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        // ── Stage badge (top of center area) ──
        if (state.stage.isNotEmpty()) {
            Text(
                text = state.stage.uppercase(),
                color = GoldAccent.copy(alpha = 0.6f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = (-8).dp),
            )
        }

        // ── Community cards row (5 slots) ──
        for (i in 0 until 5) {
            val card = cards.getOrNull(i)
            val xOffset = cardSpacing * (i - 2)
            PokerCardView(
                card = card,
                modifier = Modifier.align(Alignment.Center).offset(x = xOffset),
            )
        }

        // ── Pot (below cards)：金色徽章 ──
        if (state.pot > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = (-12).dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            listOf(Color(0x33FFD700), Color(0x1AFFD700), Color(0x33FFD700))
                        ),
                        shape = RoundedCornerShape(20.dp),
                    )
                    .border(1.dp, GoldAccent.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 24.dp, vertical = 7.dp),
            ) {
                Text(
                    text = "POT  $potText",
                    color = GoldAccent,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                )
            }
        }
    }
}


private fun formatPot(pot: Int): String {
    return when {
        pot >= 1_000_000 -> "${pot / 1_000_000}M"
        pot >= 1_000 -> "${pot / 1_000}K"
        else -> pot.toString()
    }
}