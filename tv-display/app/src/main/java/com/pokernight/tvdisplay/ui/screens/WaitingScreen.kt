@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.pokernight.tvdisplay.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.pokernight.tvdisplay.data.model.PlayerSeat
import com.pokernight.tvdisplay.data.model.PlayerStatus
import com.pokernight.tvdisplay.data.model.TableState
import com.pokernight.tvdisplay.data.network.TableViewModel
import com.pokernight.tvdisplay.ui.components.QrCodeCard
import com.pokernight.tvdisplay.ui.theme.*

/**
 * Waiting screen — shown when phase == "registering".
 *
 * Displays:
 * - Tournament display code
 * - Full tournament parameters (entry fee, starting chips, blinds, max players)
 * - Real-time seated player list + remaining seats
 * - 300-second countdown progress bar
 * - Status hint
 * - Dual QR codes (app download + pay)
 */
@Composable
fun WaitingScreen(
    state: TableState,
    viewModel: TableViewModel,
    modifier: Modifier = Modifier,
) {
    val tableCode = state.tableCode
    val payUrl = "http://43.164.130.145/pay?table=$tableCode"
    val downloadUrl = "http://43.164.130.145/download"

    val seatedPlayers = state.seats.filter { it.status != PlayerStatus.EMPTY }
    val maxPlayers = 6
    val remainingSeats = maxPlayers - seatedPlayers.size
    val countdownProgress = if (state.countdown > 0) state.countdown / 300f else 0f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(BgDark, TableGreenDark.copy(alpha = 0.3f))
                )
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // === Header ===
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "♠ 赛事准备中",
                        color = GoldAccent,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "赛事编号: ${state.displayCode.ifEmpty { tableCode }}",
                        color = TextSecondary,
                        fontSize = 16.sp,
                    )
                }

                // Countdown timer
                if (state.countdown > 0) {
                    Column(
                        horizontalAlignment = Alignment.End,
                    ) {
                        Text(
                            text = "开赛倒计时",
                            color = TextTertiary,
                            fontSize = 12.sp,
                        )
                        Text(
                            text = "${state.countdown}s",
                            color = RedAction,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // === Tournament params row ===
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardBg)
                    .border(1.dp, SeatBorder, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ParamChip(label = "启动费", value = "¥50")
                ParamChip(label = "初始筹码", value = "1,000")
                ParamChip(label = "盲注", value = "${state.sb}/${state.bb}")
                ParamChip(label = "人数上限", value = "$maxPlayers 人")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // === Main row: player list + QR codes ===
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Left: Seated players list
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardBg)
                        .border(1.dp, SeatBorder, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "已入座玩家",
                            color = GoldAccent,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "${seatedPlayers.size}/$maxPlayers",
                            color = if (remainingSeats > 0) ChipGreen else RedAction,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Player list
                    if (seatedPlayers.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "暂无玩家入座\n等待玩家扫码加入…",
                                color = TextTertiary,
                                fontSize = 16.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(seatedPlayers) { player ->
                                PlayerListRow(player = player)
                            }
                        }
                    }

                    // Remaining seats indicator
                    if (remainingSeats > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = "剩余 $remainingSeats 个空位",
                                color = TextSecondary,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }

                // Right: QR codes
                Column(
                    modifier = Modifier.width(220.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    QrCodeCard(
                        url = downloadUrl,
                        title = "下载选手 APP",
                        description = "扫码下载 · 免费入座",
                        qrSize = 130,
                    )
                    QrCodeCard(
                        url = payUrl,
                        title = "发起赛事付费",
                        description = "扫码付费激活赛事",
                        qrSize = 130,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // === Countdown progress bar ===
            if (state.countdown > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(SeatBorder),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(countdownProgress)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(GoldAccent, RedAction)
                                )
                            ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // === Status hint ===
            Text(
                text = "赛事已激活，玩家扫码免费入座",
                color = GoldAccent,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ParamChip(
    label: String,
    value: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            color = TextTertiary,
            fontSize = 12.sp,
        )
        Text(
            text = value,
            color = GoldAccent,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun PlayerListRow(
    player: PlayerSeat,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SeatBg)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(CardBg)
                .border(1.dp, SeatBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = player.avatar.ifEmpty { "🃏" },
                fontSize = 20.sp,
            )
        }

        // Nickname
        Text(
            text = player.nickname.ifEmpty { "Player ${player.seatIndex + 1}" },
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )

        // Status badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(GoldAccent.copy(alpha = 0.2f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = "已入座",
                color = GoldAccent,
                fontSize = 12.sp,
            )
        }
    }
}
