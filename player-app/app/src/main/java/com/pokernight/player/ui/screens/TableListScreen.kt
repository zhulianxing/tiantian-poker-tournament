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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokernight.player.data.GameViewModel
import com.pokernight.player.ui.theme.BgDark
import com.pokernight.player.ui.theme.Gold
import com.pokernight.player.ui.theme.White

@Composable
fun TableListScreen(
    viewModel: GameViewModel,
    onTableClick: (String) -> Unit,
    onLogout: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val tableStatus by viewModel.tableStatus.collectAsState()
    var tableCode by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "♠ Poker Night",
                    fontSize = 24.sp,
                    color = Gold,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = onLogout) {
                    Text("退出", color = Gold.copy(alpha = 0.8f))
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "欢迎, ${uiState.nickname ?: "玩家"}",
                fontSize = 14.sp,
                color = White.copy(alpha = 0.7f),
            )

            Spacer(Modifier.height(24.dp))

            // Enter table code
            Text(
                text = "输入桌号入座",
                fontSize = 16.sp,
                color = Gold,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = tableCode,
                    onValueChange = { tableCode = it.uppercase().take(6) },
                    label = { Text("桌号", color = White.copy(alpha = 0.6f)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = White,
                        unfocusedTextColor = White,
                        focusedBorderColor = Gold,
                        unfocusedBorderColor = White.copy(alpha = 0.3f),
                        cursorColor = Gold,
                    ),
                    textStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (tableCode.isNotBlank()) {
                            viewModel.fetchTableStatus(tableCode)
                            onTableClick(tableCode)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Gold,
                        contentColor = BgDark,
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(56.dp),
                ) {
                    Text("入座", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(24.dp))

            // Table status info
            tableStatus?.let { status ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = androidx.compose.ui.graphics.Color(0xFF16213E),
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "牌桌: ${status.table.label} (${status.table.code})",
                            color = Gold,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("场地: ${status.table.venueName}", color = White, fontSize = 14.sp)
                        Text("报名费: ${status.table.launchFee}", color = White, fontSize = 14.sp)
                        Text("最大人数: ${status.table.maxPlayers}", color = White, fontSize = 14.sp)
                        Text("状态: ${status.table.status}", color = White, fontSize = 14.sp)

                        status.tournament?.let { t ->
                            Spacer(Modifier.height(8.dp))
                            Text("赛事: ${t.displayCode}", color = Gold, fontSize = 14.sp)
                            Text("人数: ${t.playerCount}/${t.maxPlayers}", color = White, fontSize = 14.sp)
                            Text("赛事状态: ${t.status}", color = White, fontSize = 14.sp)

                            Spacer(Modifier.height(8.dp))
                            Text("已入座玩家:", color = Gold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            status.players.forEach { p ->
                                Text(
                                    "  ${p.avatar} ${p.nickname} — 座位${p.seatIndex} — ${p.chipCount}筹码 — ${p.status}",
                                    color = White,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            uiState.error?.let { err ->
                Text(err, color = androidx.compose.ui.graphics.Color.Red, fontSize = 14.sp)
            }
        }
    }
}
