package com.pokernight.tvdisplay.data.network

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pokernight.tvdisplay.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * ViewModel managing table state via Socket.IO events.
 */
class TableViewModel : ViewModel() {

    companion object {
        private const val TAG = "TableViewModel"
    }

    private val _uiState = MutableStateFlow(TableState())
    val uiState: StateFlow<TableState> = _uiState.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

    private var socketService = SocketService()

    fun connect(tableCode: String) {
        if (tableCode.isBlank()) {
            _connectionError.value = "Please enter a table code"
            return
        }

        _isConnecting.value = true
        _connectionError.value = null

        viewModelScope.launch {
            socketService.connect(
                tableCode = tableCode,
                onEvent = { event, data -> handleEvent(event, data) },
                onConnect = {
                    _isConnecting.value = false
                    _uiState.update { it.copy(
                        tableCode = tableCode,
                        connected = true,
                        phase = Phase.IDLE,
                        errorMessage = ""
                    )}
                },
                onDisconnect = {
                    _uiState.update { it.copy(connected = false) }
                },
                onError = { error ->
                    _isConnecting.value = false
                    _connectionError.value = error
                }
            )
        }
    }

    fun disconnect() {
        socketService.disconnect()
        _uiState.value = TableState()
        _isConnecting.value = false
        _connectionError.value = null
    }

    fun clearError() {
        _connectionError.value = null
    }

    /**
     * Reset phase to idle — called by FinishedScreen after auto-display timeout.
     * Server will also push a TABLE_STATE event to sync.
     */
    fun resetToIdle() {
        _uiState.update {
            it.copy(
                phase = Phase.IDLE,
                rankings = emptyList(),
                seats = emptyList(),
                pot = 0,
                communityCards = emptyList(),
                stage = "",
                handNumber = 0,
                countdown = 0,
            )
        }
    }

    private fun handleEvent(event: String, data: JSONObject) {
        Log.d(TAG, "handleEvent: $event")

        when (event) {
            ServerEvent.TOURNAMENT_ACTIVATED -> {
                _uiState.update { it.copy(phase = Phase.REGISTERING) }
            }

            ServerEvent.COUNTDOWN_TICK -> {
                val remaining = data.optInt("remaining", 0)
                _uiState.update { it.copy(countdown = remaining) }
            }

            ServerEvent.TOURNAMENT_STARTED -> {
                _uiState.update { it.copy(phase = Phase.STARTED, countdown = 0) }
            }

            ServerEvent.NEW_HAND -> {
                _uiState.update {
                    it.copy(
                        communityCards = emptyList(),
                        pot = 0,
                        stage = Stage.PREFLOP,
                        actingIndex = -1,
                    )
                }
            }

            ServerEvent.HOLE_CARDS -> {
                // TV display ignores hole cards — do nothing
                Log.d(TAG, "hole_cards received (ignored on TV)")
            }

            ServerEvent.HAND_STARTED -> {
                val handNumber = data.optInt("handNumber", 0)
                val stage = data.optString("stage", Stage.PREFLOP)
                val pot = data.optInt("pot", 0)
                val currentBet = data.optInt("currentBet", 0)
                val actingIndex = data.optInt("actingIndex", -1)
                val seats = parseSeats(data.optJSONArray("seats"))

                _uiState.update {
                    it.copy(
                        handNumber = handNumber,
                        stage = stage,
                        pot = pot,
                        currentBet = currentBet,
                        actingIndex = actingIndex,
                        seats = seats,
                    )
                }
            }

            ServerEvent.STAGE_CHANGED -> {
                val stage = data.optString("stage", "")
                val communityCards = parseCards(data.optJSONArray("communityCards"))
                val pot = data.optInt("pot", 0)
                _uiState.update {
                    it.copy(
                        stage = stage,
                        communityCards = communityCards,
                        pot = pot,
                    )
                }
            }

            ServerEvent.TURN_CHANGED -> {
                val actingIndex = data.optInt("actingIndex", -1)
                _uiState.update {
                    it.copy(actingIndex = actingIndex)
                }
            }

            ServerEvent.ACTION_RESULT -> {
                val playerId = data.optString("playerId", "")
                val action = data.optString("action", "")
                val amount = data.optInt("amount", 0)
                val actionText = buildActionText(action, amount)
                val pot = data.optInt("pot", _uiState.value.pot)

                _uiState.update { state ->
                    val updatedSeats = state.seats.map { seat ->
                        if (seat.playerId == playerId) {
                            seat.copy(
                                lastAction = actionText,
                                status = if (action == "fold") PlayerStatus.FOLDED
                                    else if (action == "allin") PlayerStatus.ALL_IN
                                    else seat.status,
                                isActing = false,
                                currentBet = if (action == "fold") seat.currentBet
                                    else seat.currentBet + amount,
                                chipCount = if (action == "fold") seat.chipCount
                                    else seat.chipCount - amount,
                            )
                        } else {
                            seat
                        }
                    }
                    state.copy(seats = updatedSeats, pot = pot)
                }

                // Add to hand history
                addHistory(actionText, playerId)
            }

            ServerEvent.SHOWDOWN -> {
                val pot = data.optInt("pot", 0)
                val winners = data.optJSONArray("winners")
                _uiState.update { it.copy(stage = Stage.SHOWDOWN, pot = pot) }
                // Winners details can be displayed in hand_result
            }

            ServerEvent.HAND_RESULT -> {
                val handNumber = data.optInt("handNumber", 0)
                val winnerId = data.optString("winnerId", "")
                val pot = data.optInt("pot", 0)

                _uiState.update { state ->
                    val updatedSeats = state.seats.map { seat ->
                        if (seat.playerId == winnerId) {
                            seat.copy(chipCount = seat.chipCount + pot)
                        } else {
                            seat
                        }
                    }
                    state.copy(
                        seats = updatedSeats,
                        pot = 0,
                        actingIndex = -1,
                    )
                }

                val winnerName = _uiState.value.seats.find { it.playerId == winnerId }?.nickname ?: winnerId
                addHistory("Hand #$handNumber won by $winnerName (Pot: $pot)")
            }

            ServerEvent.PLAYER_ELIMINATED -> {
                val playerId = data.optString("playerId", "")
                _uiState.update { state ->
                    val updatedSeats = state.seats.map { seat ->
                        if (seat.playerId == playerId) {
                            seat.copy(status = PlayerStatus.ELIMINATED)
                        } else {
                            seat
                        }
                    }
                    state.copy(seats = updatedSeats)
                }
            }

            ServerEvent.TOURNAMENT_FINISHED -> {
                val rankingsArray = data.optJSONArray("rankings")
                val rankings = mutableListOf<Map<String, Any>>()
                if (rankingsArray != null) {
                    for (i in 0 until rankingsArray.length()) {
                        val item = rankingsArray.optJSONObject(i)
                        if (item != null) {
                            val map = mutableMapOf<String, Any>()
                            val keysIterator = item.keys()
                            while (keysIterator.hasNext()) {
                                val key: String = keysIterator.next() as String
                                val value: Any = item.get(key)
                                map[key] = value
                            }
                            rankings.add(map)
                        }
                    }
                }
                _uiState.update { it.copy(phase = Phase.FINISHED, rankings = rankings) }
            }

            ServerEvent.BLIND_LEVEL_UP -> {
                val level = data.optInt("level", 1)
                val sb = data.optInt("sb", 10)
                val bb = data.optInt("bb", 20)
                _uiState.update { it.copy(blindLevel = level, sb = sb, bb = bb) }
            }

            ServerEvent.TABLE_STATE -> {
                // Full state sync from server
                val phase = data.optString("phase", _uiState.value.phase)
                val blindLevel = data.optInt("blindLevel", _uiState.value.blindLevel)
                val sb = data.optInt("sb", _uiState.value.sb)
                val bb = data.optInt("bb", _uiState.value.bb)
                val pot = data.optInt("pot", _uiState.value.pot)
                val communityCards = parseCards(data.optJSONArray("communityCards"))
                val seats = parseSeats(data.optJSONArray("seats"))
                val actingIndex = data.optInt("actingIndex", -1)
                val dealerIndex = data.optInt("dealerIndex", 0)
                val handNumber = data.optInt("handNumber", _uiState.value.handNumber)
                val stage = data.optString("stage", _uiState.value.stage)
                val displayCode = data.optString("displayCode", _uiState.value.displayCode)

                _uiState.update {
                    it.copy(
                        phase = phase,
                        blindLevel = blindLevel,
                        sb = sb,
                        bb = bb,
                        pot = pot,
                        communityCards = communityCards,
                        seats = seats,
                        actingIndex = actingIndex,
                        dealerIndex = dealerIndex,
                        handNumber = handNumber,
                        stage = stage,
                        displayCode = displayCode,
                    )
                }
            }

            ServerEvent.SEAT_JOINED -> {
                val seatIndex = data.optInt("seatIndex", -1)
                val nickname = data.optString("nickname", "")
                val avatar = data.optString("avatar", "\uD83C\uDCCF")
                val playerId = data.optString("playerId", "")
                if (seatIndex >= 0) {
                    _uiState.update { state ->
                        val updatedSeats = state.seats.toMutableList()
                        while (updatedSeats.size <= seatIndex) {
                            updatedSeats.add(PlayerSeat(seatIndex = updatedSeats.size, status = PlayerStatus.EMPTY))
                        }
                        updatedSeats[seatIndex] = PlayerSeat(
                            seatIndex = seatIndex,
                            playerId = playerId,
                            nickname = nickname,
                            avatar = avatar,
                            status = PlayerStatus.WAITING,
                            chipCount = 1000,
                        )
                        state.copy(seats = updatedSeats)
                    }
                }
            }

            ServerEvent.SEAT_LEFT -> {
                val seatIndex = data.optInt("seatIndex", -1)
                if (seatIndex >= 0) {
                    _uiState.update { state ->
                        val updatedSeats = state.seats.mapIndexed { index, seat ->
                            if (index == seatIndex) PlayerSeat(seatIndex = index, status = PlayerStatus.EMPTY)
                            else seat
                        }
                        state.copy(seats = updatedSeats)
                    }
                }
            }
        }
    }

    private fun parseCards(jsonArray: JSONArray?): List<Card> {
        if (jsonArray == null) return emptyList()
        val cards = mutableListOf<Card>()
        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.optJSONObject(i)
            if (item != null) {
                cards.add(Card.fromMap(item.toMap()))
            }
        }
        return cards
    }

    private fun parseSeats(jsonArray: JSONArray?): List<PlayerSeat> {
        if (jsonArray == null) return emptyList()
        val seats = mutableListOf<PlayerSeat>()
        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.optJSONObject(i)
            if (item != null) {
                seats.add(PlayerSeat.fromMap(item.toMap(), i))
            } else {
                seats.add(PlayerSeat(seatIndex = i, status = PlayerStatus.EMPTY))
            }
        }
        return seats
    }

    private fun buildActionText(action: String, amount: Int): String {
        return when (action.lowercase()) {
            "fold" -> "Fold"
            "check" -> "Check"
            "call" -> if (amount > 0) "Call $amount" else "Call"
            "raise" -> "Raise to $amount"
            "bet" -> "Bet $amount"
            "allin", "all-in", "all_in" -> "All-In $amount"
            "small_blind" -> "SB $amount"
            "big_blind" -> "BB $amount"
            else -> "$action $amount".trim()
        }
    }

    private fun addHistory(text: String, playerId: String = "") {
        _uiState.update { state ->
            val playerName = state.seats.find { it.playerId == playerId }?.nickname ?: ""
            val entry = if (playerName.isNotEmpty()) "$playerName: $text" else text
            state.copy(handHistory = (state.handHistory + entry).takeLast(20))
        }
    }

    override fun onCleared() {
        super.onCleared()
        socketService.disconnect()
    }
}

/**
 * Extension to convert JSONObject to Map.
 */
private fun JSONObject.toMap(): Map<String, Any?> {
    val map = mutableMapOf<String, Any?>()
    val keysIterator = keys()
    while (keysIterator.hasNext()) {
        val key: String = keysIterator.next() as String
        val value: Any = get(key)
        map[key] = value
    }
    return map
}
