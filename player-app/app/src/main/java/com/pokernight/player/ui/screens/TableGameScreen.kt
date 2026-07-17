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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokernight.player.data.GameViewModel
import com.pokernight.player.data.model.SeatInfo
import com.pokernight.player.ui.components.ActionPanel
import com.pokernight.player.ui.components.CommunityCardRow
import com.pokernight.player.ui.components.PokerCard
import com.pokernight.player.ui.components.RaiseSlider
import com.pokernight.player.ui.components.SeatView
import com.pokernight.player.ui.theme.BgDark
import com.pokernight.player.ui.theme.Gold
import com.pokernight.player.ui.theme.TableGreen
import com.pokernight.player.ui.theme.White

@Composable
fun TableGameScreen(
    viewModel: GameViewModel,
    tableCode: String,
    onBack: () -> Unit,
) {
    val gameState by viewModel.gameState.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val toast by viewModel.toast.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showConfirmAction by remember { mutableStateOf<String?>(null) }
    var raiseAmount by remember { mutableIntStateOf(0) }

    // Connect socket on enter
    LaunchedEffect(tableCode) {
        viewModel.connectSocket(tableCode)
    }

    // Handle toast
    LaunchedEffect(toast) {
        toast?.let { msg ->
            snackbarHostState.showSnackbar(message = msg)
            viewModel.consumeToast()
        }
    }

    // Confirmation snackbar for fold/all-in
    LaunchedEffect(showConfirmAction) {
        showConfirmAction?.let { action ->
            val msg = when (action) {
                "fold" -> "确认弃牌？"
                "all_in" -> "确认全下 ${gameState.myChips} 筹码？"
                else -> null
            }
            if (msg != null) {
                val result = snackbarHostState.showSnackbar(
                    message = msg,
                    actionLabel = "确认",
                    duration = androidx.compose.material3.SnackbarDuration.Short,
                )
                if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                    viewModel.performAction(action)
                }
            }
            showConfirmAction = null
        }
    }

    val callAmount = (gameState.currentBet - gameState.myCurrentBet).coerceAtLeast(0)
    val minRaise = (gameState.currentBet * 2).coerceAtLeast(gameState.bb)
    val maxRaise = gameState.myChips

    // Update raiseAmount when entering turn
    LaunchedEffect(gameState.isMyTurn) {
        if (gameState.isMyTurn) {
            raiseAmount = minRaise
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark),
    ) {
        // Table background (oval green)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 100.dp, bottom = 200.dp, start = 16.dp, end = 16.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(TableGreen, TableGreen.copy(alpha = 0.8f), Color(0xFF063E1F)),
                    ),
                    shape = RoundedCornerShape(120.dp),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("桌号: $tableCode", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("L${gameState.blindLevel} SB:${gameState.sb} BB:${gameState.bb}", color = White, fontSize = 12.sp)
                Text("第${gameState.handNumber}手", color = White, fontSize = 12.sp)
                if (gameState.countdown > 0) {
                    Text("⏱ ${gameState.countdown}s", color = Color.Red, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Opponents: top 3 seats
            val seats = gameState.seats
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                for (i in 0..2) {
                    SeatView(
                        seat = seats.getOrNull(i) ?: SeatInfo(seatIndex = i, status = "empty"),
                        isMe = i == gameState.mySeatIndex,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Community cards
            CommunityCardRow(
                cards = gameState.communityCards,
                modifier = Modifier.padding(vertical = 4.dp),
            )

            // Pot
            Text(
                text = "底池: ${gameState.pot}",
                color = Gold,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(8.dp))

            // Left and right seats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SeatView(
                    seat = seats.getOrNull(3) ?: SeatInfo(seatIndex = 3, status = "empty"),
                    isMe = 3 == gameState.mySeatIndex,
                )
                SeatView(
                    seat = seats.getOrNull(4) ?: SeatInfo(seatIndex = 4, status = "empty"),
                    isMe = 4 == gameState.mySeatIndex,
                )
            }

            Spacer(Modifier.height(12.dp))

            // My hole cards
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PokerCard(
                    card = gameState.holeCards.getOrNull(0),
                    isBig = true,
                    faceDown = gameState.holeCards.isEmpty(),
                )
                PokerCard(
                    card = gameState.holeCards.getOrNull(1),
                    isBig = true,
                    faceDown = gameState.holeCards.isEmpty(),
                )
            }

            Spacer(Modifier.height(4.dp))

            // Info bar: my chips, my bet
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "我的筹码: ${gameState.myChips}",
                    color = Gold,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "已下注: ${gameState.myCurrentBet}",
                    color = White,
                    fontSize = 14.sp,
                )
            }

            Spacer(Modifier.height(4.dp))

            // Raise slider
            if (gameState.isMyTurn && maxRaise > minRaise) {
                RaiseSlider(
                    minValue = minRaise,
                    maxValue = maxRaise,
                    currentValue = raiseAmount,
                    onValueChange = { raiseAmount = it },
                )
            }

            // Action panel
            ActionPanel(
                isMyTurn = gameState.isMyTurn,
                currentBet = gameState.currentBet,
                myCurrentBet = gameState.myCurrentBet,
                myChips = gameState.myChips,
                callAmount = callAmount,
                minRaise = minRaise,
                raiseAmount = raiseAmount,
                onRaiseAmountChange = { raiseAmount = it },
                onFold = {
                    showConfirmAction = "fold"
                },
                onCheck = {
                    viewModel.performAction("check")
                },
                onCall = {
                    viewModel.performAction("call")
                },
                onRaise = {
                    viewModel.performAction("raise", raiseAmount)
                },
                onAllIn = {
                    showConfirmAction = "all_in"
                },
                modifier = Modifier.padding(top = 4.dp),
            )

            // Event info
            if (uiState.lastEvent.isNotEmpty()) {
                Text(
                    text = "事件: ${uiState.lastEvent}",
                    color = White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                )
            }
        }

        // Snackbar host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 180.dp),
        )
    }
}
