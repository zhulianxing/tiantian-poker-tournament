package com.pokernight.player.ui.screens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokernight.player.BuildConfig
import com.pokernight.player.audio.SoundManager
import com.pokernight.player.data.GameViewModel
import com.pokernight.player.data.model.SeatInfo
import com.pokernight.player.data.model.key
import com.pokernight.player.ui.components.ActionPanel
import com.pokernight.player.ui.components.CommunityCardRow
import com.pokernight.player.ui.components.PokerCard
import com.pokernight.player.ui.components.RaiseSlider
import com.pokernight.player.ui.components.SeatView
import com.pokernight.player.ui.theme.ActionAllInRed
import com.pokernight.player.ui.theme.BgDark
import com.pokernight.player.ui.theme.FeltGreen
import com.pokernight.player.ui.theme.FeltSheen
import com.pokernight.player.ui.theme.GhostBg
import com.pokernight.player.ui.theme.Gold
import com.pokernight.player.ui.theme.GoldDark
import com.pokernight.player.ui.theme.GoldLine
import com.pokernight.player.ui.theme.GoldPillEdge
import com.pokernight.player.ui.theme.HairlineWhite
import com.pokernight.player.ui.theme.MaskBg
import com.pokernight.player.ui.theme.PanelDark
import com.pokernight.player.ui.theme.PanelGlass
import com.pokernight.player.ui.theme.TextDim
import com.pokernight.player.ui.theme.TextPrimary
import com.pokernight.player.ui.theme.TextTertiary
import kotlinx.coroutines.delay

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
    var showRaisePanel by remember { mutableStateOf(false) }
    var eliminatedDismissed by remember { mutableStateOf(false) }
    var sndOn by remember { mutableStateOf(SoundManager.isEnabled()) }
    val context = LocalContext.current

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
        } else {
            showRaisePanel = false
        }
    }

    // 行动倒计时：轮到我时按服务端时限倒数（超时服务端自动弃牌）
    // 入场震动一次，最后 3 秒再震一次，杜绝「莫名被弃牌」
    var turnRemainingMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(gameState.isMyTurn, gameState.actingIndex) {
        if (gameState.isMyTurn) {
            val total = gameState.turnTimeoutMs.coerceAtLeast(3000).toLong()
            vibrate(context, 200)
            val start = System.currentTimeMillis()
            var warned = false
            while (true) {
                val remaining = (total - (System.currentTimeMillis() - start)).coerceAtLeast(0L)
                turnRemainingMs = remaining
                if (remaining <= 3000L && !warned) {
                    warned = true
                    vibrate(context, 500)
                }
                if (remaining <= 0L) break
                delay(100)
            }
        } else {
            turnRemainingMs = 0L
        }
    }

    // 存活/总人数（顶栏「剩 X/Y」）
    val activeCount = gameState.seats.count { it.status != "empty" && it.status != "eliminated" }
    val totalCount = gameState.seats.count { it.status != "empty" }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark),
    ) {
        // 氛围光斑（对齐网页 .glow-a 顶部绿光 / .glow-b 右下金光）
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-260).dp)
                .width(560.dp)
                .height(560.dp)
                .background(Brush.radialGradient(listOf(FeltGreen, Color.Transparent))),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 140.dp, y = 140.dp)
                .width(480.dp)
                .height(480.dp)
                .background(Brush.radialGradient(listOf(Gold.copy(alpha = 0.06f), Color.Transparent))),
        )

        // 牌桌毡面（对齐网页 .felt：绿色微光渐变 + 金线圆角面板）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 66.dp, bottom = 208.dp, start = 10.dp, end = 10.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.linearGradient(
                        colorStops = arrayOf(
                            0.0f to FeltGreen,
                            0.55f to FeltSheen,
                            1.0f to FeltSheen,
                        ),
                    ),
                )
                .border(1.dp, GoldLine, RoundedCornerShape(22.dp)),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 顶栏：桌号 · 盲注级别 · 手牌号 · 剩余人数
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .background(
                        color = PanelGlass,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .border(1.dp, HairlineWhite, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("桌号 $tableCode", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("L${gameState.blindLevel} ${gameState.sb}/${gameState.bb}", color = TextDim, fontSize = 12.sp)
                Text("第${gameState.handNumber}手", color = TextDim, fontSize = 12.sp)
                if (totalCount > 0) {
                    Text("剩$activeCount/$totalCount", color = TextDim, fontSize = 12.sp)
                }
                if (gameState.countdown > 0) {
                    Text("⏱ ${gameState.countdown}s", color = Color(0xFFE5484D), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                // 音效开关（对应网页牌桌页 sndBtn，状态持久化在 SoundManager）
                Text(
                    text = if (sndOn) "🔊" else "🔇",
                    fontSize = 15.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            sndOn = SoundManager.toggle()
                            // 与网页一致：打开时试播一声
                            if (sndOn) SoundManager.play("check")
                        }
                        .padding(horizontal = 4.dp),
                )
            }

            // Opponents: top 3 seats
            val seats = gameState.seats
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                for (i in 0..2) {
                    SeatView(
                        seat = seats.getOrNull(i) ?: SeatInfo(seatIndex = i, status = "empty"),
                        isMe = i == gameState.mySeatIndex,
                        isWinner = ((seats.getOrNull(i)?.playerId ?: "") == gameState.handResult?.winnerId && gameState.handResult != null) || gameState.winSeats.contains(i),
                        revealCards = gameState.showdownHands[i] ?: emptyList(),
                        winCards = gameState.winCards,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // 阶段徽章：翻牌前/翻牌圈/转牌圈/河牌圈/摊牌
            if (gameState.stage.isNotEmpty()) {
                Text(
                    text = stageLabelCn(gameState.stage),
                    color = Gold.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp,
                )
                Spacer(Modifier.height(4.dp))
            }

            // Community cards
            CommunityCardRow(
                cards = gameState.communityCards,
                winCards = gameState.winCards,
                modifier = Modifier.padding(vertical = 4.dp),
            )

            Spacer(Modifier.height(6.dp))

            // 底池：金色徽章
            Row(
                modifier = Modifier
                    .background(
                        brush = Brush.horizontalGradient(
                            listOf(Color(0x2EFFD700), Color(0x12FFD700), Color(0x2EFFD700))
                        ),
                        shape = RoundedCornerShape(20.dp),
                    )
                    .border(1.dp, GoldPillEdge, RoundedCornerShape(20.dp))
                    .padding(horizontal = 22.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("底池", color = Gold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${gameState.pot}",
                    color = Gold,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                )
            }

            Spacer(Modifier.height(10.dp))

            // Left and right seats
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SeatView(
                    seat = seats.getOrNull(3) ?: SeatInfo(seatIndex = 3, status = "empty"),
                    isMe = 3 == gameState.mySeatIndex,
                    isWinner = ((seats.getOrNull(3)?.playerId ?: "") == gameState.handResult?.winnerId && gameState.handResult != null) || gameState.winSeats.contains(3),
                    revealCards = gameState.showdownHands[3] ?: emptyList(),
                    winCards = gameState.winCards,
                )
                SeatView(
                    seat = seats.getOrNull(4) ?: SeatInfo(seatIndex = 4, status = "empty"),
                    isMe = 4 == gameState.mySeatIndex,
                    isWinner = ((seats.getOrNull(4)?.playerId ?: "") == gameState.handResult?.winnerId && gameState.handResult != null) || gameState.winSeats.contains(4),
                    revealCards = gameState.showdownHands[4] ?: emptyList(),
                    winCards = gameState.winCards,
                )
            }

            Spacer(Modifier.height(8.dp))

            // 操作流水条：最近 2–3 条动作
            if (gameState.actionLog.isNotEmpty()) {
                Text(
                    text = gameState.actionLog.joinToString(" · "),
                    color = TextDim,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(4.dp))
            }

            // My hole cards
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PokerCard(
                    card = gameState.holeCards.getOrNull(0),
                    isBig = true,
                    faceDown = gameState.holeCards.isEmpty(),
                    win = gameState.holeCards.getOrNull(0)?.let { it.key() in gameState.winCards } == true,
                )
                PokerCard(
                    card = gameState.holeCards.getOrNull(1),
                    isBig = true,
                    faceDown = gameState.holeCards.isEmpty(),
                    win = gameState.holeCards.getOrNull(1)?.let { it.key() in gameState.winCards } == true,
                )
            }

            Spacer(Modifier.height(6.dp))

            // Info bar: my chips, my bet
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(PanelGlass, RoundedCornerShape(10.dp))
                    .border(1.dp, HairlineWhite, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "我的筹码 ${gameState.myChips}",
                    color = Gold,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "已下注 ${gameState.myCurrentBet}",
                    color = TextDim,
                    fontSize = 14.sp,
                )
            }

            Spacer(Modifier.height(4.dp))

            // 行动倒计时条（轮到我时可见，金→红渐变）
            if (gameState.isMyTurn && turnRemainingMs > 0L) {
                val total = gameState.turnTimeoutMs.coerceAtLeast(3000).toLong()
                val frac = (turnRemainingMs.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(GhostBg),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(frac)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Brush.horizontalGradient(listOf(Gold, ActionAllInRed))),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${(turnRemainingMs + 500) / 1000}s",
                        color = if (frac < 0.25f) ActionAllInRed else Gold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // 加注抽屉（对齐网页 .raise-drawer：金色大额 + 金滑杆 + 快捷注额）
            if (gameState.isMyTurn && showRaisePanel) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .background(PanelDark, RoundedCornerShape(14.dp))
                        .border(1.dp, HairlineWhite, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "加注到 $raiseAmount",
                        color = Gold,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    RaiseSlider(
                        minValue = minRaise,
                        maxValue = maxRaise,
                        currentValue = raiseAmount,
                        onValueChange = { raiseAmount = it },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        QuickRaiseButton("½底池", Modifier.weight(1f)) {
                            raiseAmount = (gameState.pot / 2).coerceIn(minRaise, maxRaise)
                        }
                        QuickRaiseButton("1底池", Modifier.weight(1f)) {
                            raiseAmount = gameState.pot.coerceIn(minRaise, maxRaise)
                        }
                        QuickRaiseButton("2底池", Modifier.weight(1f)) {
                            raiseAmount = (gameState.pot * 2).coerceIn(minRaise, maxRaise)
                        }
                        QuickRaiseButton("全下", Modifier.weight(1f)) {
                            raiseAmount = maxRaise
                        }
                        QuickRaiseButton("✕", Modifier.weight(1f)) {
                            showRaisePanel = false
                        }
                    }
                }
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
                raiseConfirm = showRaisePanel,
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
                    if (showRaisePanel) {
                        // 抽屉展开时按钮即「确认加注」
                        viewModel.performAction("raise", raiseAmount)
                        showRaisePanel = false
                    } else {
                        showRaisePanel = true
                    }
                },
                onAllIn = {
                    showConfirmAction = "all_in"
                },
                modifier = Modifier.padding(top = 4.dp),
            )

            // Event info（仅调试包可见）
            if (BuildConfig.DEBUG && uiState.lastEvent.isNotEmpty()) {
                Text(
                    text = uiState.lastEvent,
                    color = TextTertiary,
                    fontSize = 11.sp,
                )
            }
        }

        // 每手结果横幅（对齐网页 .hand-banner：顶部居中浮层，深底金边金字）
        gameState.handResult?.let { hr ->
            val isMe = hr.winnerId == viewModel.getPlayerId()
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp)
                    .background(Color(0xF2101018), RoundedCornerShape(14.dp))
                    .border(1.dp, GoldDark, RoundedCornerShape(14.dp))
                    .padding(horizontal = 26.dp, vertical = 12.dp),
            ) {
                Text(
                    text = buildString {
                        append(if (isMe) "🏆 你赢得 " else "🏆 ${hr.winnerName} 赢得 ")
                        append(hr.winAmount)
                        if (hr.handName.isNotEmpty()) append(" · ${hr.handName}")
                    },
                    color = Gold,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // 淘汰时刻：最终名次 + 继续围观 / 返回大厅
        gameState.myEliminatedRank?.let { rank ->
            if (!eliminatedDismissed) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xCC000000))
                        .clickable { /* 拦截点击，防误触 */ },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .padding(32.dp)
                            .background(MaskBg, RoundedCornerShape(22.dp))
                            .border(1.dp, GoldDark, RoundedCornerShape(22.dp))
                            .padding(horizontal = 38.dp, vertical = 30.dp),
                    ) {
                        Text("淘汰", color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("第 $rank 名", color = Gold, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(20.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { eliminatedDismissed = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = GhostBg,
                                    contentColor = TextPrimary,
                                ),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Text("继续围观")
                            }
                            Button(
                                onClick = onBack,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Gold,
                                    contentColor = Color(0xFF1A1400),
                                ),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Text("返回大厅", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // 赛事结束：最终排名 + 返回大厅
        if (gameState.phase == "tournament_finished") {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC000000))
                    .clickable { /* 拦截点击，防误触 */ },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(32.dp)
                        .background(MaskBg, RoundedCornerShape(22.dp))
                        .border(1.dp, GoldDark, RoundedCornerShape(22.dp))
                        .padding(horizontal = 38.dp, vertical = 30.dp),
                ) {
                    Text("🏆 赛事结束", color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    val myRank = gameState.finalRankings.find { it.playerId == viewModel.getPlayerId() }?.rank
                    if (myRank != null) {
                        Spacer(Modifier.height(6.dp))
                        Text("我的名次：第 $myRank 名", color = Color.White, fontSize = 15.sp)
                    }
                    Spacer(Modifier.height(16.dp))
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        gameState.finalRankings.sortedBy { it.rank }.forEach { r ->
                            Row(
                                modifier = Modifier
                                    .width(240.dp)
                                    .then(
                                        if (r.playerId == viewModel.getPlayerId())
                                            Modifier.background(Color(0x1446A758), RoundedCornerShape(8.dp))
                                        else Modifier,
                                    ),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "${if (r.rank == 1) "🏆 " else ""}#${r.rank} ${r.nickname}",
                                    color = if (r.rank == 1) Gold else Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = if (r.rank == 1) FontWeight.Bold else FontWeight.Normal,
                                )
                                Text(
                                    text = "${r.chips}",
                                    color = TextDim,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = onBack,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Gold,
                            contentColor = Color(0xFF1A1400),
                        ),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("返回大厅", fontWeight = FontWeight.Bold)
                    }
                }
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

/** 快捷加注档位按钮 */
@Composable
private fun QuickRaiseButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = GhostBg,
            contentColor = TextPrimary,
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.height(34.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** 牌局阶段英文码 → 中文标签 */
private fun stageLabelCn(stage: String): String = when (stage.lowercase()) {
    "preflop", "pre-flop", "pre_flop" -> "翻牌前"
    "flop" -> "翻牌圈"
    "turn" -> "转牌圈"
    "river" -> "河牌圈"
    "showdown" -> "摊牌"
    else -> stage
}

/** 震动（容错：无权限/无硬件时静默） */
private fun vibrate(context: Context, ms: Long) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(ms)
        }
    } catch (_: Exception) {
        // 无震动硬件或权限异常时忽略
    }
}
