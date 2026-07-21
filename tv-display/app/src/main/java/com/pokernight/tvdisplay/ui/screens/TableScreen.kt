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
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Text
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.pokernight.tvdisplay.data.model.Card
import com.pokernight.tvdisplay.data.model.PlayerSeat
import com.pokernight.tvdisplay.data.model.TableState
import com.pokernight.tvdisplay.ui.components.BottomBar
import com.pokernight.tvdisplay.ui.components.LeaderboardPanel
import com.pokernight.tvdisplay.ui.components.PlayerSeatView
import com.pokernight.tvdisplay.ui.components.PokerCardView
import com.pokernight.tvdisplay.ui.components.TopBar
import com.pokernight.tvdisplay.ui.components.WinnerBannerView
import com.pokernight.tvdisplay.ui.components.stageLabel
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
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Top bar
            TopBar(state = state)

            Spacer(modifier = Modifier.height(8.dp))

            // Center: Poker table + 实时排行
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    PokerTableContent(state = state)
                }
                Spacer(modifier = Modifier.width(10.dp))
                LeaderboardPanel(seats = state.seats)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Bottom bar
            BottomBar(
                handHistory = state.handHistory,
                onDisconnect = onDisconnect,
            )
        }

        // 赢家横幅：顶栏下方落下，下一手自动消失
        WinnerBannerView(
            banner = state.winnerBanner,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 68.dp),
        )
    }
}

/**
 * 决赛桌布局：绝对定位——上下两排座位跨桌沿，中央 HERO 公共牌区。
 * 不用 Column/weight 嵌套（TV 上测量异常会把下排座位挤出可视区）。
 * 所有尺寸按可用区域比例计算，适配手机预览与 1080p 电视。
 */
@Composable
private fun PokerTableContent(state: TableState) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val areaW = maxWidth
        val areaH = maxHeight
        val seatW = areaW * 0.24f
        val seatH = maxOf(64.dp, areaH * 0.24f)
        // 中央列高 = 可用高 - 上下座位 - 徽章/底池固定开销(60dp)，手机与电视自适应
        val cardH = (areaH - seatH * 2 - 60.dp).coerceAtLeast(48.dp)
        val cardW = cardH * 0.72f
        val rimPadV = seatH * 0.45f

        // 牌桌：木质桌沿 + 绿色毡面（双层椭圆），上下留出让座位跨沿的空间
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = areaW * 0.02f, vertical = rimPadV)
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
        )

        // 上排座位（3/4/5），跨桌沿；摊牌时明牌翻向桌心（下方）
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(0.92f),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            listOf(3, 4, 5).forEach { index ->
                val seat = state.seats.getOrNull(index) ?: PlayerSeat(seatIndex = index)
                SeatWithReveal(
                    seat = seat,
                    revealed = state.showdownHands[index].orEmpty(),
                    revealBelow = true,
                    seatWidth = seatW,
                    seatHeight = seatH,
                )
            }
        }

        // 下排座位（0/1/2），跨桌沿；摊牌时明牌翻向桌心（上方）
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.92f),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            listOf(0, 1, 2).forEach { index ->
                val seat = state.seats.getOrNull(index) ?: PlayerSeat(seatIndex = index)
                SeatWithReveal(
                    seat = seat,
                    revealed = state.showdownHands[index].orEmpty(),
                    revealBelow = false,
                    seatWidth = seatW,
                    seatHeight = seatH,
                )
            }
        }

        // 中央：阶段徽章 + HERO 公共牌 + 底池（独立成列，互不重叠）
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.stage.isNotEmpty()) {
                Text(
                    text = stageLabel(state.stage),
                    color = GoldAccent.copy(alpha = 0.75f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (i in 0 until 5) {
                    PokerCardView(
                        card = state.communityCards.getOrNull(i),
                        cardWidth = cardW,
                        cardHeight = cardH,
                    )
                }
            }

            if (state.pot > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
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
                        text = "底池  ${formatPot(state.pot)}",
                        color = GoldAccent,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                    )
                }
            }
        }
    }
}

/**
 * 座位 + 摊牌明牌：showdown 时在座位朝桌心一侧翻出两张底牌。
 * 明牌用 offset 绘制在座位边界外，不影响座位测量。
 */
@Composable
private fun SeatWithReveal(
    seat: PlayerSeat,
    revealed: List<Card>,
    revealBelow: Boolean,
    seatWidth: Dp,
    seatHeight: Dp,
) {
    Box(contentAlignment = Alignment.Center) {
        PlayerSeatView(seat = seat, seatWidth = seatWidth, seatHeight = seatHeight)
        if (revealed.isNotEmpty()) {
            val cardH = seatHeight * 0.5f
            val cardW = cardH * 0.72f
            Row(
                modifier = Modifier
                    .align(if (revealBelow) Alignment.BottomCenter else Alignment.TopCenter)
                    .offset(y = if (revealBelow) cardH * 0.6f else -(cardH * 0.6f)),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                revealed.take(2).forEach { card ->
                    PokerCardView(card = card, cardWidth = cardW, cardHeight = cardH)
                }
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