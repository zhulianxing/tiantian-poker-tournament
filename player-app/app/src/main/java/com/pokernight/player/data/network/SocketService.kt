package com.pokernight.player.data.network

import android.util.Log
import com.pokernight.player.data.model.Card
import com.pokernight.player.data.model.GameState
import io.socket.client.Socket
import org.json.JSONArray
import org.json.JSONObject

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
    }

    fun connect(token: String?) {
        if (socket?.connected() == true) return
        socket = NetworkProvider.createSocket(token)
        registerListeners()
        socket?.connect()
    }

    fun disconnect() {
        socket?.disconnect()
        socket?.off()
        socket = null
        currentState = GameState()
    }

    fun joinTable(tableCode: String) {
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

        s.on(Socket.EVENT_CONNECT) { Log.i(TAG, "Socket connected") }
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
            if (args.isNotEmpty() && args[0] is JSONObject) {
                val data = args[0] as JSONObject
                val phase = data.optString("phase", "")
                val displayCode = data.optString("displayCode", "")
                val sb = data.optInt("sb", 10)
                val bb = data.optInt("bb", 20)
                val seats = parseSeats(data.optJSONArray("seats"))
                val mySeat = findMySeat(seats)
                currentState = currentState.copy(
                    phase = phase,
                    sb = sb,
                    bb = bb,
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
            currentState = currentState.copy(phase = "tournament_started")
            onEvent(EVT_TOURNAMENT_STARTED, data)
            onStateUpdate(currentState)
        }

        s.on(EVT_NEW_HAND) { args ->
            val data = if (args.isNotEmpty() && args[0] is JSONObject) args[0] as JSONObject else JSONObject()
            currentState = currentState.copy(
                phase = "new_hand",
                communityCards = emptyList(),
                currentBet = 0,
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
            if (args.isNotEmpty() && args[0] is JSONObject) {
                val data = args[0] as JSONObject
                val actingIndex = data.optInt("actingIndex", -1)
                val pot = data.optInt("pot", 0)
                val currentBet = data.optInt("currentBet", 0)
                val serverSeats = parseSeats(data.optJSONArray("seats"))
                val mySeat = findMySeat(serverSeats)
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
                currentState = currentState.copy(
                    seats = seats,
                    pot = if (pot > 0) pot else currentState.pot,
                    currentBet = currentBet,
                    myChips = mySeat?.chipCount ?: currentState.myChips,
                    myCurrentBet = mySeat?.currentBet ?: currentState.myCurrentBet,
                )
                onEvent(EVT_ACTION_RESULT, data)
                onStateUpdate(currentState)
            }
        }

        s.on(EVT_SHOWDOWN) { args ->
            val data = if (args.isNotEmpty() && args[0] is JSONObject) args[0] as JSONObject else JSONObject()
            currentState = currentState.copy(phase = "showdown")
            onEvent(EVT_SHOWDOWN, data)
            onStateUpdate(currentState)
        }

        s.on(EVT_HAND_RESULT) { args ->
            if (args.isNotEmpty() && args[0] is JSONObject) {
                val data = args[0] as JSONObject
                val pot = data.optInt("pot", 0)
                val seats = parseSeats(data.optJSONArray("seats"))
                val mySeat = findMySeat(seats)
                currentState = currentState.copy(
                    phase = "hand_result",
                    pot = 0,
                    currentBet = 0,
                    seats = if (seats.isNotEmpty()) seats else currentState.seats,
                    myChips = mySeat?.chipCount ?: currentState.myChips,
                    myCurrentBet = 0,
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
                currentState = currentState.copy(seats = seats)
                onEvent(EVT_PLAYER_ELIMINATED, data)
                onStateUpdate(currentState)
            }
        }

        s.on(EVT_TOURNAMENT_FINISHED) { args ->
            val data = if (args.isNotEmpty() && args[0] is JSONObject) args[0] as JSONObject else JSONObject()
            currentState = currentState.copy(phase = "tournament_finished")
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
