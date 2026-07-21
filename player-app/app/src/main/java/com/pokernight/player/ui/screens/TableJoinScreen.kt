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
import androidx.compose.material3.CircularProgressIndicator
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

@Composable
fun TableJoinScreen(
    viewModel: GameViewModel,
    tableCode: String,
    onJoinSuccess: (String) -> Unit,
    onBack: () -> Unit,
) {
    val tableStatus by viewModel.tableStatus.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val joinResult by viewModel.joinResult.collectAsState()

    LaunchedEffect(tableCode) {
        viewModel.fetchTableStatus(tableCode)
    }

    LaunchedEffect(joinResult) {
        android.util.Log.d("TableJoin", "joinResult changed: $joinResult")
        joinResult?.let { result ->
            android.util.Log.d("TableJoin", "Navigating to lobby with tableCode=$tableCode")
            onJoinSuccess(tableCode)
        }
    }

    // Auto-navigate if player is already seated or tournament already started
    LaunchedEffect(tableStatus?.tournament?.status, tableStatus?.players) {
        val status = tableStatus ?: return@LaunchedEffect
        val tournament = status.tournament ?: return@LaunchedEffect
        val myPlayerId = viewModel.getPlayerId()
        val alreadySeated = status.players.any { it.playerId == myPlayerId }
        if (alreadySeated && (tournament.status == "started" || tournament.status == "active" || tournament.status == "running")) {
            // Skip lobby, go straight to game
            android.util.Log.d("TableJoin", "Player already seated and tournament started, navigating to game")
            onJoinSuccess(tableCode)
        } else if (alreadySeated && tournament.status == "registering") {
            // Go to lobby to wait
            android.util.Log.d("TableJoin", "Player already seated, navigating to lobby")
            onJoinSuccess(tableCode)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) {
                    Text("← 返回", color = Gold.copy(alpha = 0.8f))
                }
                Text(
                    text = "桌号: $tableCode",
                    color = Gold,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(16.dp))

            val status = tableStatus
            if (status == null) {
                // Loading or error state
                if (uiState.error != null) {
                    Text(
                        text = uiState.error!!,
                        color = Color.Red,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                    )
                } else {
                    CircularProgressIndicator(
                        color = Gold,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 60.dp),
                    )
                }
            } else {
                // Table info card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF16213E)),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = status.table.label,
                            color = Gold,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("场馆: ${status.table.venueName}", color = White, fontSize = 14.sp)
                        Text("桌号: ${status.table.code}", color = White.copy(alpha = 0.7f), fontSize = 13.sp)
                        Text("状态: ${status.table.status}", color = White.copy(alpha = 0.7f), fontSize = 13.sp)
                    }
                }

                Spacer(Modifier.height(16.dp))

                val tournament = status.tournament
                if (tournament == null) {
                    // No active tournament
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "⏳",
                                fontSize = 48.sp,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "等待赛事开始",
                                color = White.copy(alpha = 0.7f),
                                fontSize = 16.sp,
                            )
                        }
                    }
                } else {
                    // Tournament info
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF16213E)),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "赛事: ${tournament.displayCode}",
                                color = Gold,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("报名费", color = White.copy(alpha = 0.7f), fontSize = 13.sp)
                                Text("${tournament.launchFee}", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("初始筹码", color = White.copy(alpha = 0.7f), fontSize = 13.sp)
                                Text("${tournament.startChips}", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("盲注", color = White.copy(alpha = 0.7f), fontSize = 13.sp)
                                Text("${tournament.sb}/${tournament.bb}", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("已入座/上限", color = White.copy(alpha = 0.7f), fontSize = 13.sp)
                                Text("${tournament.playerCount}/${tournament.maxPlayers}", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Text("状态: ${tournament.status}", color = White.copy(alpha = 0.7f), fontSize = 13.sp)
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Player list
                    if (status.players.isNotEmpty()) {
                        Text(
                            text = "已入座玩家",
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

                    Spacer(Modifier.height(16.dp))

                    // Join button
                    val canJoin = tournament.status == "registering" &&
                            tournament.playerCount < tournament.maxPlayers

                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            color = Gold,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    } else {
                        Button(
                            onClick = { viewModel.joinTournament(tournament.id) },
                            enabled = canJoin,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Gold,
                                contentColor = BgDark,
                                disabledContainerColor = Gold.copy(alpha = 0.3f),
                                disabledContentColor = BgDark.copy(alpha = 0.5f),
                            ),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                text = when {
                                    tournament.status != "registering" -> "赛事不可报名"
                                    tournament.playerCount >= tournament.maxPlayers -> "已满座"
                                    else -> "一键入座"
                                },
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
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

                    // 旁观入口：赛事进行中或已满座时，只进牌局页观看，不占座不操作
                    val canSpectate = tournament.status in listOf("started", "active", "running") ||
                            tournament.playerCount >= tournament.maxPlayers
                    if (canSpectate && !uiState.isLoading) {
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { onJoinSuccess(tableCode) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2A2A38),
                                contentColor = Gold,
                            ),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                text = "👁 旁观",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}
