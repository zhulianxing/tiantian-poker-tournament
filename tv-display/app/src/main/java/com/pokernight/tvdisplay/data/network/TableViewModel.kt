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

        // Disconnect existing connection first to prevent memory leaks
        if (_uiState.value.connected) {
            socketService.disconnect()
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
                // Parse seats from tournament_started event
                val seats = parseSeats(data.optJSONArray("seats"))
                _uiState.update { it.copy(phase = Phase.STARTED, countdown = 0, seats = seats) }
            }

            ServerEvent.NEW_HAND -> {
                _uiState.update {
                    it.copy(
                        communityCards = emptyList(),
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
                        communityCards = emptyList(), // Clear community cards on new hand
                    )
                }
            }

            ServerEvent.STAGE_CHANGED -> {
                val stage = data.optString("stage", "")
                val communityCards = parseCards(data.optJSONArray("communityCards"))
                val pot = data.optInt("pot", _uiState.value.pot)
                val currentBet = data.optInt("currentBet", 0)
                val actingIndex = data.optInt("actingIndex", -1)
                val seats = parseSeats(data.optJSONArray("seats"))
                _uiState.update {
                    it.copy(
                        stage = stage,
                        communityCards = communityCards,
                        pot = pot,
                        currentBet = currentBet,
                        actingIndex = actingIndex,
                        seats = seats.ifEmpty { it.seats },
                    )
                }
            }

            ServerEvent.TURN_CHANGED -> {
                val actingIndex = data.optInt("actingIndex", -1)
                val pot = data.optInt("pot", _uiState.value.pot)
                val currentBet = data.optInt("currentBet", _uiState.value.currentBet)
                val seats = parseSeats(data.optJSONArray("seats"))
                _uiState.update {
                    it.copy(
                        actingIndex = actingIndex,
                        pot = pot,
                        currentBet = currentBet,
                        seats = seats.ifEmpty { it.seats },
                    )
                }
            }

            ServerEvent.ACTION_RESULT -> {
                val playerId = data.optString("playerId", "")
                val action = data.optString("action", "")
                val amount = data.optInt("amount", 0)
                val pot = data.optInt("pot", _uiState.value.pot)
                val currentBet = data.optInt("currentBet", _uiState.value.currentBet)
                val actingIndex = data.optInt("actingIndex", -1)
                val seatsFromServer = parseSeats(data.optJSONArray("seats"))
                val actionText = buildActionText(action, amount)

                _uiState.update { state ->
                    // Use server seats if provided, otherwise update locally
                    val updatedSeats = if (seatsFromServer.isNotEmpty()) {
                        seatsFromServer.map { seat ->
                            // Mark acting player
                            val isActing = seat.seatIndex == actingIndex
                            // Add last action text for the acting player
                            val lastAction = if (seat.playerId == playerId) actionText else seat.lastAction
                            seat.copy(isActing = isActing, lastAction = lastAction)
                        }
                    } else {
                        // Fallback: local update — sync all seat fields
                        state.seats.map { seat ->
                            if (seat.playerId == playerId) {
                                val newChipCount = when (action.lowercase()) {
                                    "fold" -> seat.chipCount
                                    "check" -> seat.chipCount
                                    "call" -> seat.chipCount - amount
                                    "raise", "bet" -> seat.chipCount - amount
                                    "allin", "all-in", "all_in" -> 0
                                    else -> seat.chipCount
                                }
                                seat.copy(
                                    lastAction = actionText,
                                    status = if (action == "fold") PlayerStatus.FOLDED
                                        else if (action == "allin" || action == "all-in" || action == "all_in") PlayerStatus.ALL_IN
                                        else seat.status,
                                    isActing = false,
                                    chipCount = maxOf(0, newChipCount),
                                    currentBet = if (action.lowercase() in listOf("raise", "bet", "call")) amount else seat.currentBet,
                                )
                            } else {
                                seat.copy(isActing = seat.seatIndex == actingIndex)
                            }
                        }
                    }
                    state.copy(
                        seats = updatedSeats,
                        pot = pot,
                        currentBet = currentBet,
                        actingIndex = actingIndex,
                    )
                }

                // Add to hand history
                addHistory(actionText, playerId)
            }

            ServerEvent.SHOWDOWN -> {
                val pot = data.optInt("pot", 0)
                val winnersArray = data.optJSONArray("winners")
                val winnerNames = mutableListOf<String>()
                if (winnersArray != null) {
                    for (i in 0 until winnersArray.length()) {
                        val w = winnersArray.optJSONObject(i)
                        if (w != null) {
                            val name = w.optString("handName", "")
                            if (name.isNotEmpty()) winnerNames.add(name)
                        }
                    }
                }
                _uiState.update { it.copy(stage = Stage.SHOWDOWN, pot = pot) }
            }

            ServerEvent.HAND_RESULT -> {
                val handNumber = data.optInt("handNumber", 0)
                val winnerId = data.optString("winnerId", "")
                val pot = data.optInt("pot", 0)
                val seats = parseSeats(data.optJSONArray("seats"))

                _uiState.update { state ->
                    val updatedSeats = if (seats.isNotEmpty()) {
                        // Use server seats (already updated with correct chips)
                        seats
                    } else {
                        // Fallback: add pot to winner locally
                        state.seats.map { seat ->
                            if (seat.playerId == winnerId) {
                                seat.copy(chipCount = seat.chipCount + pot)
                            } else {
                                seat
                            }
                        }
                    }
                    state.copy(
                        seats = updatedSeats,
                        pot = 0,
                        currentBet = 0,
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
                val phase = data.optString("phase", _uiState.value.phase).lowercase()
                val blindLevel = data.optInt("blindLevel", _uiState.value.blindLevel)
                val sb = data.optInt("sb", _uiState.value.sb)
                val bb = data.optInt("bb", _uiState.value.bb)
                val pot = data.optInt("pot", _uiState.value.pot)
                val currentBet = data.optInt("currentBet", _uiState.value.currentBet)
                val communityCards = parseCards(data.optJSONArray("communityCards"))
                val seats = parseSeats(data.optJSONArray("seats"))
                val actingIndex = data.optInt("actingIndex", -1)
                val dealerIndex = data.optInt("dealerIndex", 0)
                val handNumber = data.optInt("handNumber", _uiState.value.handNumber)
                val stage = data.optString("stage", _uiState.value.stage)
                val displayCode = data.optString("displayCode", _uiState.value.displayCode)
                val countdown = data.optInt("countdown", _uiState.value.countdown)

                _uiState.update {
                    it.copy(
                        phase = phase,
                        blindLevel = blindLevel,
                        sb = sb,
                        bb = bb,
                        pot = pot,
                        currentBet = currentBet,
                        communityCards = communityCards,
                        seats = seats,
                        actingIndex = actingIndex,
                        dealerIndex = dealerIndex,
                        handNumber = handNumber,
                        stage = stage,
                        displayCode = displayCode,
                        countdown = countdown,
                    )
                }
            }

            ServerEvent.SEAT_JOINED -> {
                val seatIndex = data.optInt("seatIndex", -1)
                val nickname = data.optString("nickname", "")
                val avatar = data.optString("avatar", "\uD83C\uDCCF")
                val playerId = data.optString("playerId", "")
                val chipCount = data.optInt("chipCount", 1000)
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
                            chipCount = chipCount,
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
