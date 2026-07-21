package com.pokernight.player.data.network

import android.util.Log
import com.pokernight.player.data.model.Card
import com.pokernight.player.data.model.FinalRankInfo
import com.pokernight.player.data.model.GameState
import com.pokernight.player.data.model.HandResultInfo
import io.socket.client.Socket
import org.json.JSONArray
import org.json.JSONObject

/** 动作码 → 中文文案（操作流水用） */
private fun actionTextCn(action: String, amount: Int): String {
    return when (action.lowercase()) {
        "fold" -> "弃牌"
        "check" -> "过牌"
        "call" -> if (amount > 0) "跟注 $amount" else "跟注"
        "raise" -> "加注到 $amount"
        "bet" -> "下注 $amount"
        "allin", "all-in", "all_in" -> "全下 $amount"
        "small_blind" -> "小盲 $amount"
        "big_blind" -> "大盲 $amount"
        else -> "$action $amount".trim()
    }
}

class SocketService(
    private val onStateUpdate: (GameState) -> Unit,
    private val onEvent: (String, JSONObject) -> Unit,
) {
    private var socket: Socket? = null
    private var currentState = GameState()

    companion object {
        private const val TAG = "SocketService"

        const val EVT_TOURNAMENT_ACTIVATED = "tournament_activated"
        const val EVT_COUNTDOWN_TICK = "countdown_tick"
        const val EVT_TOURNAMENT_STARTED = "tournament_started"
        const val EVT_NEW_HAND = "new_hand"
        const val EVT_HOLE_CARDS = "hole_cards"
        const val EVT_HAND_STARTED = "hand_started"
        const val EVT_STAGE_CHANGED = "stage_changed"
        const val EVT_TURN_CHANGED = "turn_changed"
        const val EVT_ACTION_RESULT = "action_result"
        const val EVT_SHOWDOWN = "showdown"
        const val EVT_HAND_RESULT = "hand_result"
        const val EVT_PLAYER_ELIMINATED = "player_eliminated"
        const val EVT_TOURNAMENT_FINISHED = "tournament_finished"
        const val EVT_BLIND_LEVEL_UP = "blind_level_up"
        const val EVT_ACTION_TIMER_STARTED = "action_timer_started"
    }

    fun connect(token: String?) {
        Log.i(TAG, "connect() called, token=${if (token != null) token.take(20) + "..." else "null"}")
        if (socket?.connected() == true) {
            Log.i(TAG, "Socket already connected, skipping")
            return
        }
        Log.i(TAG, "Creating socket...")
        socket = NetworkProvider.createSocket(token)
        registerListeners()
        Log.i(TAG, "Connecting socket...")
        socket?.connect()
    }

    fun disconnect() {
        socket?.disconnect()
        socket?.off()
        socket = null
        currentState = GameState()
    }

    fun isConnected(): Boolean = socket?.connected() == true

    fun joinTable(tableCode: String) {
        Log.i(TAG, "joinTable($tableCode) called, socket=${if (socket?.connected() == true) "connected" else "NOT connected"}")
        val data = JSONObject().apply { put("tableCode", tableCode) }
        socket?.emit("join_table", data)
        currentState = currentState.copy(tableCode = tableCode)
    }

    fun playerAction(tournamentId: String, action: String, amount: Int = 0) {
        val data = JSONObject().apply {
            put("tournamentId", tournamentId)
            put("action", action)
            put("amount", amount)
        }
        socket?.emit("player_action", data)
    }

    private fun parseSeats(jsonArr: JSONArray?): List<com.pokernight.player.data.model.SeatInfo> {
        if (jsonArr == null) return emptyList()
        val seats = mutableListOf<com.pokernight.player.data.model.SeatInfo>()
        for (i in 0 until jsonArr.length()) {
            val s = jsonArr.getJSONObject(i)
            seats.add(com.pokernight.player.data.model.SeatInfo(
                seatIndex = s.optInt("seatIndex", i),
                playerId = s.optString("playerId", ""),
                nickname = s.optString("nickname", s.optString("name", "")),
                chipCount = s.optInt("chipCount", s.optInt("chips", 0)),
                currentBet = s.optInt("currentBet", 0),
                status = s.optString("status", "empty"),
                isDealer = s.optBoolean("isDealer", false),
                isActing = s.optBoolean("isActing", false),
                lastAction = s.optString("lastAction", ""),
            ))
        }
        return seats
    }

    private fun findMySeat(seats: List<com.pokernight.player.data.model.SeatInfo>): com.pokernight.player.data.model.SeatInfo? {
        val myId = com.pokernight.player.data.network.AuthManager.getPlayerId()
        return seats.find { it.playerId == myId }
    }

    private fun registerListeners() {
        val s = socket ?: return
        // 防御：同一 Socket 实例被复用时先清掉旧监听器，避免事件重复触发
        s.off()

        s.on(Socket.EVENT_CONNECT) {
            Log.i(TAG, "Socket connected")
            // Re-emit join_table after connection is established
            if (currentState.tableCode.isNotEmpty()) {
                Log.i(TAG, "Re-emitting join_table for ${currentState.tableCode}")
                val data = JSONObject().apply { put("tableCode", currentState.tableCode) }
                s.emit("join_table", data)
            }
        }
        s.on(Socket.EVENT_DISCONNECT) { Log.i(TAG, "Socket disconnected") }
        s.on(Socket.EVENT_CONNECT_ERROR) { args ->
            Log.e(TAG, "Connect error: ${args.joinToString()}")
        }

        s.on(EVT_TOURNAMENT_ACTIVATED) { args ->
            if (args.isNotEmpty() && args[0] is JSONObject) {
                onEvent(EVT_TOURNAMENT_ACTIVATED, args[0] as JSONObject)
            }
        }

        s.on(EVT_COUNTDOWN_TICK) { args ->
            if (args.isNotEmpty() && args[0] is JSONObject) {
                val data = args[0] as JSONObject
                val remaining = data.optInt("remaining", 0)
                currentState = currentState.copy(countdown = remaining)
                onStateUpdate(currentState)
            }
        }

        s.on("table_state") { args ->
            // 赛事已结束的终态不可被覆盖：重连后服务端会重推初始桌态（phase=idle/空座位），
            // 若照收会把结算浮层冲掉，界面停在没有出口的死桌面（就是用户反馈的"卡住"）
            if (currentState.phase != "tournament_finished" && args.isNotEmpty() && args[0] is JSONObject) {
                val data = args[0] as JSONObject
                val phase = data.optString("phase", "")
                val displayCode = data.optString("displayCode", "")
                val sb = data.optInt("sb", 10)
                val bb = data.optInt("bb", 20)
                val seats = parseSeats(data.optJSONArray("seats"))
                val mySeat = findMySeat(seats)
                currentState = currentState.copy(
                    phase = phase,
                    tournamentId = data.optString("tournamentId", ""),
                    // Don't overwrite tableCode with displayCode - they are different!
                    // tableCode is the table password (e.g. SNGT01), displayCode is tournament name (e.g. REAL66)
                    sb = sb,
                    bb = bb,
                    pot = data.optInt("pot", 0),
                    currentBet = data.optInt("currentBet", 0),
                    blindLevel = data.optInt("blindLevel", 1),
                    handNumber = data.optInt("handNumber", 0),
                    actingIndex = data.optInt("actingIndex", -1),
                    dealerIndex = data.optInt("dealerIndex", 0),
                    seats = if (seats.isNotEmpty()) seats else currentState.seats,
                    mySeatIndex = mySeat?.seatIndex ?: currentState.mySeatIndex,
                    myChips = mySeat?.chipCount ?: currentState.myChips,
                    myCurrentBet = mySeat?.currentBet ?: 0,
                )
                onStateUpdate(currentState)
            }
        }

        s.on("seat_joined") { args ->
            if (args.isNotEmpty() && args[0] is JSONObject) {
                val data = args[0] as JSONObject
                val seats = parseSeats(data.optJSONArray("seats"))
                val mySeat = findMySeat(seats)
                if (seats.isNotEmpty()) {
                    currentState = currentState.copy(
                        seats = seats,
                        mySeatIndex = mySeat?.seatIndex ?: currentState.mySeatIndex,
                        myChips = mySeat?.chipCount ?: currentState.myChips,
                    )
                    onStateUpdate(currentState)
                }
            }
        }

        s.on(EVT_TOURNAMENT_STARTED) { args ->
            val data = if (args.isNotEmpty() && args[0] is JSONObject) args[0] as JSONObject else JSONObject()
            val tid = data.optString("tournamentId", "")
            val seats = parseSeats(data.optJSONArray("seats"))
            val mySeat = findMySeat(seats)
            currentState = currentState.copy(
                phase = "tournament_started",
                tournamentId = tid,
                seats = seats,
                mySeatIndex = mySeat?.seatIndex ?: currentState.mySeatIndex,
                myChips = mySeat?.chipCount ?: currentState.myChips,
                // 新赛事开始：清掉上一场的终态残留（旧排名/旧淘汰名次会误弹浮层）
                myEliminatedRank = null,
                finalRankings = emptyList(),
                handResult = null,
                communityCards = emptyList(),
                pot = 0,
                currentBet = 0,
                myCurrentBet = 0,
            )
            onEvent(EVT_TOURNAMENT_STARTED, data)
            onStateUpdate(currentState)
        }

        s.on(EVT_NEW_HAND) { args ->
            val data = if (args.isNotEmpty() && args[0] is JSONObject) args[0] as JSONObject else JSONObject()
            currentState = currentState.copy(
                phase = "new_hand",
                communityCards = emptyList(),
                currentBet = 0,
                stage = "",
                actionLog = emptyList(),
                handResult = null,
            )
            onEvent(EVT_NEW_HAND, data)
            onStateUpdate(currentState)
        }

        s.on(EVT_HOLE_CARDS) { args ->
            if (args.isNotEmpty() && args[0] is JSONObject) {
                val data = args[0] as JSONObject
                // Only update if these are MY cards
                val cardPlayerId = data.optString("playerId", "")
                val myPlayerId = com.pokernight.player.data.network.AuthManager.getPlayerId()
                if (cardPlayerId.isEmpty() || cardPlayerId == myPlayerId) {
                    val cardsArr = data.optJSONArray("cards") ?: JSONArray()
                    val cards = mutableListOf<Card>()
                    for (i in 0 until cardsArr.length()) {
                        val c = cardsArr.getJSONObject(i)
                        cards.add(Card(
                            suit = c.optString("suit", ""),
                            rank = c.optString("rank", ""),
                            value = c.optInt("value", 0),
                        ))
                    }
                    currentState = currentState.copy(holeCards = cards)
                    onStateUpdate(currentState)
                }
            }
        }

        s.on(EVT_HAND_STARTED) { args ->
            if (args.isNotEmpty() && args[0] is JSONObject) {
                val data = args[0] as JSONObject
                val handNumber = data.optInt("handNumber", 0)
                val stage = data.optString("stage", "preflop")
                val pot = data.optInt("pot", 0)
                val currentBet = data.optInt("currentBet", 0)
                val actingIndex = data.optInt("actingIndex", -1)
                // Parse seats from server event
                val seats = parseSeats(data.optJSONArray("seats"))
                val mySeat = findMySeat(seats)
                currentState = currentState.copy(
                    phase = "hand_started",
                    handNumber = handNumber,
                    pot = pot,
                    currentBet = currentBet,
                    actingIndex = actingIndex,
                    stage = stage,
                    actionLog = emptyList(),
                    handResult = null,
                    seats = seats,
                    mySeatIndex = mySeat?.seatIndex ?: currentState.mySeatIndex,
                    myChips = mySeat?.chipCount ?: currentState.myChips,
                    myCurrentBet = mySeat?.currentBet ?: 0,
                    isMyTurn = actingIndex == (mySeat?.seatIndex ?: currentState.mySeatIndex),
                )
                onEvent(EVT_HAND_STARTED, data)
                onStateUpdate(currentState)
            }
        }

        s.on(EVT_STAGE_CHANGED) { args ->
            if (args.isNotEmpty() && args[0] is JSONObject) {
                val data = args[0] as JSONObject
                val stage = data.optString("stage", "")
                val pot = data.optInt("pot", 0)
                val currentBet = data.optInt("currentBet", 0)
                val cardsArr = data.optJSONArray("communityCards") ?: JSONArray()
                val cards = mutableListOf<Card>()
                for (i in 0 until cardsArr.length()) {
                    val c = cardsArr.getJSONObject(i)
                    cards.add(Card(
                        suit = c.optString("suit", ""),
                        rank = c.optString("rank", ""),
                        value = c.optInt("value", 0),
                    ))
                }
                // Update seats from stage_changed event
                val seats = parseSeats(data.optJSONArray("seats"))
                val mySeat = findMySeat(seats)
                currentState = currentState.copy(
                    phase = "stage_changed:$stage",
                    pot = pot,
                    currentBet = currentBet,
                    stage = stage,
                    communityCards = cards,
                    seats = if (seats.isNotEmpty()) seats else currentState.seats,
                    myChips = mySeat?.chipCount ?: currentState.myChips,
                    myCurrentBet = mySeat?.currentBet ?: currentState.myCurrentBet,
                )
                onEvent(EVT_STAGE_CHANGED, data)
                onStateUpdate(currentState)
            }
        }

        s.on(EVT_TURN_CHANGED) { args ->
            Log.i(TAG, "turn_changed event received!")
            if (args.isNotEmpty() && args[0] is JSONObject) {
                val data = args[0] as JSONObject
                val actingIndex = data.optInt("actingIndex", -1)
                val pot = data.optInt("pot", 0)
                val currentBet = data.optInt("currentBet", 0)
                val serverSeats = parseSeats(data.optJSONArray("seats"))
                val mySeat = findMySeat(serverSeats)
                Log.i(TAG, "turn_changed: actingIndex=$actingIndex, mySeatIndex=${mySeat?.seatIndex ?: currentState.mySeatIndex}, isMyTurn=${actingIndex == (mySeat?.seatIndex ?: currentState.mySeatIndex)}")
                val seats = if (serverSeats.isNotEmpty()) serverSeats.toMutableList() else currentState.seats.toMutableList()
                for (i in seats.indices) {
                    seats[i] = seats[i].copy(isActing = i == actingIndex)
                }
                currentState = currentState.copy(
                    actingIndex = actingIndex,
                    pot = pot,
                    currentBet = currentBet,
                    seats = seats,
                    myChips = mySeat?.chipCount ?: currentState.myChips,
                    myCurrentBet = mySeat?.currentBet ?: currentState.myCurrentBet,
                    isMyTurn = actingIndex == (mySeat?.seatIndex ?: currentState.mySeatIndex),
                )
                onStateUpdate(currentState)
            }
        }

        s.on(EVT_ACTION_RESULT) { args ->
            if (args.isNotEmpty() && args[0] is JSONObject) {
                val data = args[0] as JSONObject
                val playerId = data.optString("playerId", "")
                val action = data.optString("action", "")
                val amount = data.optInt("amount", 0)
                val pot = data.optInt("pot", data.optInt("pot", 0))
                val currentBet = data.optInt("currentBet", currentState.currentBet)
                // Update seats from server snapshot
                val serverSeats = parseSeats(data.optJSONArray("seats"))
                val mySeat = findMySeat(serverSeats)
                val seats = if (serverSeats.isNotEmpty()) serverSeats.toMutableList() else currentState.seats.toMutableList()
                for (i in seats.indices) {
                    if (seats[i].playerId == playerId) {
                        seats[i] = seats[i].copy(
                            lastAction = "$action" + if (amount > 0) " $amount" else "",
                            isActing = false,
                        )
                    }
                }
                // 操作流水条：昵称 + 中文动作（保留最近 3 条）
                val actorName = seats.find { it.playerId == playerId }?.nickname ?: ""
                val actionText = actionTextCn(action, amount)
                val newLog = (currentState.actionLog + if (actorName.isNotEmpty()) "$actorName $actionText" else actionText).takeLast(3)
                currentState = currentState.copy(
                    seats = seats,
                    pot = if (pot > 0) pot else currentState.pot,
                    currentBet = currentBet,
                    myChips = mySeat?.chipCount ?: currentState.myChips,
                    myCurrentBet = mySeat?.currentBet ?: currentState.myCurrentBet,
                    actionLog = newLog,
                )
                onEvent(EVT_ACTION_RESULT, data)
                onStateUpdate(currentState)
            }
        }

        s.on(EVT_SHOWDOWN) { args ->
            val data = if (args.isNotEmpty() && args[0] is JSONObject) args[0] as JSONObject else JSONObject()
            currentState = currentState.copy(phase = "showdown", stage = "showdown")
            onEvent(EVT_SHOWDOWN, data)
            onStateUpdate(currentState)
        }

        s.on(EVT_HAND_RESULT) { args ->
            if (args.isNotEmpty() && args[0] is JSONObject) {
                val data = args[0] as JSONObject
                val winnerId = data.optString("winnerId", "")
                val winAmount = data.optInt("winAmount", data.optInt("pot", 0))
                val handName = data.optString("handName", "")
                val seats = parseSeats(data.optJSONArray("seats"))
                val mySeat = findMySeat(seats)
                val winnerName = seats.find { it.playerId == winnerId }?.nickname ?: ""
                currentState = currentState.copy(
                    phase = "hand_result",
                    pot = 0,
                    currentBet = 0,
                    seats = if (seats.isNotEmpty()) seats else currentState.seats,
                    myChips = mySeat?.chipCount ?: currentState.myChips,
                    myCurrentBet = 0,
                    handResult = HandResultInfo(
                        winnerId = winnerId,
                        winnerName = winnerName,
                        winAmount = winAmount,
                        handName = handName,
                    ),
                )
                onEvent(EVT_HAND_RESULT, data)
                onStateUpdate(currentState)
            }
        }

        s.on(EVT_PLAYER_ELIMINATED) { args ->
            if (args.isNotEmpty() && args[0] is JSONObject) {
                val data = args[0] as JSONObject
                val playerId = data.optString("playerId", "")
                val seats = currentState.seats.toMutableList()
                for (i in seats.indices) {
                    if (seats[i].playerId == playerId) {
                        seats[i] = seats[i].copy(status = "eliminated", lastAction = "淘汰")
                    }
                }
                // 我被淘汰：计算最终名次 = 剩余存活人数 + 1
                val myId = com.pokernight.player.data.network.AuthManager.getPlayerId()
                val myRank = if (playerId == myId) {
                    seats.count { it.status != "eliminated" && it.status != "empty" } + 1
                } else {
                    currentState.myEliminatedRank
                }
                currentState = currentState.copy(seats = seats, myEliminatedRank = myRank)
                onEvent(EVT_PLAYER_ELIMINATED, data)
                onStateUpdate(currentState)
            }
        }

        s.on(EVT_ACTION_TIMER_STARTED) { args ->
            if (args.isNotEmpty() && args[0] is JSONObject) {
                val data = args[0] as JSONObject
                val seatIndex = data.optInt("seatIndex", -1)
                val timeoutMs = data.optInt("timeoutMs", 15000)
                // 只关心轮到我时的时限；服务端超时自动弃牌，倒计时条以此为准
                if (seatIndex == currentState.mySeatIndex && timeoutMs > 0) {
                    currentState = currentState.copy(turnTimeoutMs = timeoutMs)
                    onStateUpdate(currentState)
                }
            }
        }

        s.on(EVT_TOURNAMENT_FINISHED) { args ->
            val data = if (args.isNotEmpty() && args[0] is JSONObject) args[0] as JSONObject else JSONObject()
            // 解析最终排名表，结算浮层用
            val rankings = mutableListOf<FinalRankInfo>()
            val arr = data.optJSONArray("rankings")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    rankings.add(
                        FinalRankInfo(
                            playerId = o.optString("playerId", ""),
                            nickname = o.optString("nickname", ""),
                            rank = o.optInt("rank", 0),
                            chips = o.optInt("chips", 0),
                        )
                    )
                }
            }
            currentState = currentState.copy(phase = "tournament_finished", finalRankings = rankings)
            onEvent(EVT_TOURNAMENT_FINISHED, data)
            onStateUpdate(currentState)
        }

        s.on(EVT_BLIND_LEVEL_UP) { args ->
            if (args.isNotEmpty() && args[0] is JSONObject) {
                val data = args[0] as JSONObject
                val level = data.optInt("level", 1)
                val sb = data.optInt("sb", 10)
                val bb = data.optInt("bb", 20)
                currentState = currentState.copy(
                    blindLevel = level,
                    sb = sb,
                    bb = bb,
                )
                onEvent(EVT_BLIND_LEVEL_UP, data)
                onStateUpdate(currentState)
            }
        }
    }
}
