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

        s.on(EVT_HAND_STARTED) { args ->
            if (args.isNotEmpty() && args[0] is JSONObject) {
                val data = args[0] as JSONObject
                val handNumber = data.optInt("handNumber", 0)
                val stage = data.optString("stage", "preflop")
                val pot = data.optInt("pot", 0)
                val currentBet = data.optInt("currentBet", 0)
                val actingIndex = data.optInt("actingIndex", -1)
                currentState = currentState.copy(
                    phase = "hand_started",
                    handNumber = handNumber,
                    pot = pot,
                    currentBet = currentBet,
                    actingIndex = actingIndex,
                    isMyTurn = actingIndex == currentState.mySeatIndex,
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
                currentState = currentState.copy(
                    phase = "stage_changed:$stage",
                    pot = pot,
                    communityCards = cards,
                )
                onEvent(EVT_STAGE_CHANGED, data)
                onStateUpdate(currentState)
            }
        }

        s.on(EVT_TURN_CHANGED) { args ->
            if (args.isNotEmpty() && args[0] is JSONObject) {
                val data = args[0] as JSONObject
                val actingIndex = data.optInt("actingIndex", -1)
                val seats = currentState.seats.toMutableList()
                if (actingIndex in seats.indices) {
                    for (i in seats.indices) {
                        seats[i] = seats[i].copy(isActing = i == actingIndex)
                    }
                }
                currentState = currentState.copy(
                    actingIndex = actingIndex,
                    seats = seats,
                    isMyTurn = actingIndex == currentState.mySeatIndex,
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
                val seats = currentState.seats.toMutableList()
                for (i in seats.indices) {
                    if (seats[i].playerId == playerId) {
                        seats[i] = seats[i].copy(
                            lastAction = "$action" + if (amount > 0) " $amount" else "",
                            isActing = false,
                        )
                    }
                }
                currentState = currentState.copy(seats = seats)
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
                currentState = currentState.copy(
                    phase = "hand_result",
                    pot = 0,
                    currentBet = 0,
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
