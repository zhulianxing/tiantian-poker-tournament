package com.pokernight.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokernight.player.data.GameViewModel
import com.pokernight.player.ui.theme.BgDark
import com.pokernight.player.ui.theme.Gold
import com.pokernight.player.ui.theme.White
import kotlinx.coroutines.delay

@Composable
fun TableLobbyScreen(
    viewModel: GameViewModel,
    tableCode: String,
    onTournamentStart: (String) -> Unit,
    onLeave: () -> Unit,
) {
    val tableStatus by viewModel.tableStatus.collectAsState()
    val joinResult by viewModel.joinResult.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val gameState by viewModel.gameState.collectAsState()

    LaunchedEffect(tableCode) {
        if (tableCode.isNotEmpty()) {
            viewModel.fetchTableStatus(tableCode)
        }
        // Periodic refresh
        while (true) {
            if (tableCode.isNotEmpty()) {
                viewModel.fetchTableStatus(tableCode)
            }
            delay(5000)
        }
    }

    // Watch for tournament status change to "active" → navigate to game
    LaunchedEffect(tableStatus?.tournament?.status) {
        val status = tableStatus?.tournament?.status
        if (status == "active" || status == "running" || status == "started") {
            val code = tableStatus?.table?.code ?: ""
            onTournamentStart(code)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = {
                    viewModel.leaveTournament(tableCode, onLeave)
                }) {
                    Text("← 离开座位", color = Color(0xFFE53935))
                }
                Text(
                    text = "等待开赛",
                    color = Gold,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(16.dp))

            // My seat info
            joinResult?.let { result ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF16213E)),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("我的座位", color = White.copy(alpha = 0.7f), fontSize = 14.sp)
                            Text(
                                text = "Seat ${result.seatIndex}",
                                color = Gold,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("初始筹码", color = White.copy(alpha = 0.7f), fontSize = 14.sp)
                            Text(
                                text = "${if (result.chipCount > 0) result.chipCount else result.startChips}",
                                color = Gold,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Tournament info
            tableStatus?.tournament?.let { tournament ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF16213E)),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "赛事: ${tournament.displayCode}",
                            color = Gold,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("盲注", color = White.copy(alpha = 0.7f), fontSize = 13.sp)
                            Text("${tournament.sb}/${tournament.bb}", color = Gold, fontSize = 13.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("入座", color = White.copy(alpha = 0.7f), fontSize = 13.sp)
                            Text("${tournament.playerCount}/${tournament.maxPlayers}", color = Gold, fontSize = 13.sp)
                        }
                        Text("状态: ${tournament.status}", color = White.copy(alpha = 0.7f), fontSize = 13.sp)
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Countdown
                if (tournament.status == "registering") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "⏳",
                                fontSize = 48.sp,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "等待赛事开始...",
                                color = White.copy(alpha = 0.7f),
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Player list
            tableStatus?.let { status ->
                if (status.players.isNotEmpty()) {
                    Text(
                        text = "已入座玩家 (${status.players.size})",
                        color = Gold,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(status.players) { player ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1B2A)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = player.avatar, fontSize = 24.sp)
                                        Spacer(Modifier.width(8.dp))
                                        Text(text = player.nickname, color = White, fontSize = 14.sp)
                                    }
                                    Text(
                                        text = "座${player.seatIndex} · ${player.chipCount}筹码",
                                        color = Gold,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Leave button
            Button(
                onClick = { viewModel.leaveTournament(tableCode, onLeave) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE53935),
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("离开座位", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            uiState.error?.let { err ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = err,
                    color = Color.Red,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
