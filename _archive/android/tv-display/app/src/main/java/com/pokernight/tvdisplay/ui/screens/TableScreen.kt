package com.pokernight.tvdisplay.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokernight.tvdisplay.data.model.ConnectionState
import com.pokernight.tvdisplay.data.model.TableState
import com.pokernight.tvdisplay.ui.components.CommunityCardsRow
import com.pokernight.tvdisplay.ui.components.PlayerSeatView
import com.pokernight.tvdisplay.ui.components.PotDisplay
import com.pokernight.tvdisplay.ui.components.TopBar
import com.pokernight.tvdisplay.ui.theme.*

@Composable
fun TableScreen(
    tableState: TableState?,
    connectionState: ConnectionState,
    onDisconnect: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(Background)) {
        when (connectionState) {
            is ConnectionState.Connected -> {
                if (tableState != null) {
                    TableContent(tableState, onDisconnect)
                } else {
                    // State loading
                    LoadingOverlay("等待牌桌数据...")
                }
            }
            is ConnectionState.Connecting -> {
                LoadingOverlay("连接中...")
            }
            is ConnectionState.Error -> {
                ErrorOverlay(
                    message = connectionState.message,
                    onRetry = onDisconnect
                )
            }
            is ConnectionState.Disconnected -> {
                LoadingOverlay("已断线，重连中...")
            }
        }
    }
}

@Composable
private fun TableContent(
    state: TableState,
    onDisconnect: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        TopBar(
            tableCode = state.tableCode,
            level = state.level,
            maxLevel = state.maxLevel,
            smallBlind = state.smallBlind,
            bigBlind = state.bigBlind,
            blindCountdown = state.blindCountdown,
            playerCount = state.playerCount
        )

        // Main table area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TableGreen),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Top row of players (P1-P3)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (i in 0..2) {
                        if (i < state.seats.size) {
                            PlayerSeatView(seat = state.seats[i])
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Middle section: community cards + pot
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Community cards
                    CommunityCardsRow(cards = state.communityCards)

                    Spacer(Modifier.height(12.dp))

                    // Pot display
                    PotDisplay(mainPot = state.mainPot)

                    // Phase label
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = state.phase.label,
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Bottom row of players (P4-P6)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (i in 3..5) {
                        if (i < state.seats.size) {
                            PlayerSeatView(seat = state.seats[i])
                        }
                    }
                }
            }
        }

        // Bottom bar with disconnect
        DisconnectBar(onDisconnect)
    }
}

@Composable
private fun DisconnectBar(onDisconnect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Background.copy(alpha = 0.9f))
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onDisconnect,
            colors = ButtonDefaults.buttonColors(containerColor = RedAccent)
        ) {
            Text("断开连接", color = CardWhite, fontSize = 14.sp)
        }
    }
}

@Composable
private fun LoadingOverlay(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = TextSecondary,
            fontSize = 20.sp
        )
    }
}

@Composable
private fun ErrorOverlay(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "连接失败",
                color = RedAccent,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                color = TextSecondary,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 48.dp)
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = Gold)
            ) {
                Text("返回", color = Background, fontSize = 16.sp)
            }
        }
    }
}
