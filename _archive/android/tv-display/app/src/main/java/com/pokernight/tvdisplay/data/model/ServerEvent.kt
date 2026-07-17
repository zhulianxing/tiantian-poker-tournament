package com.pokernight.tvdisplay.data.model

import com.google.gson.annotations.SerializedName

// Raw DTO from server
data class ServerTableState(
    val tableCode: String? = null,
    val status: String? = null,
    val handCount: Int? = null,
    val players: List<ServerPlayer>? = emptyList(),
    val communityCards: List<String>? = emptyList(),
    val pot: Int? = null,
    val currentBet: Int? = null,
    val currentTurn: Int? = null,
    val minRaise: Int? = null,
    val smallBlind: Int? = null,
    val bigBlind: Int? = null,
    val level: Int? = null
)

data class ServerPlayer(
    val id: String? = null,
    val name: String? = null,
    val nickname: String? = null,
    val chips: Int? = null,
    val chipCount: Int? = null,
    val bet: Int? = null,
    val currentBet: Int? = null,
    val folded: Boolean? = null,
    val allIn: Boolean? = null,
    val isDealer: Boolean? = null,
    val seatIndex: Int? = null,
    val disconnected: Boolean? = null
)

data class ServerHandResult(
    val handNumber: Int? = null,
    val winner: String? = null,
    val winnerId: String? = null,
    val winAmount: Int? = null,
    val winningHand: String? = null,
    val playerId: String? = null,
    val winnerName: String? = null,
    val amount: Int? = null
)

fun ServerTableState.toTableState(): TableState {
    val seatList = (players ?: emptyList()).mapIndexed { index, p ->
        val status = when {
            p.folded == true -> SeatStatus.FOLDED
            p.allIn == true -> SeatStatus.ALL_IN
            p.disconnected == true -> SeatStatus.DISCONNECTED
            p.id != null -> SeatStatus.WAITING
            else -> SeatStatus.EMPTY
        }
        PlayerSeat(
            seatIndex = p.seatIndex ?: index,
            playerId = p.id,
            nickname = p.name ?: p.nickname ?: "",
            chipCount = p.chips ?: p.chipCount ?: 0,
            currentBet = p.bet ?: p.currentBet ?: 0,
            status = if (p.id == null && status == SeatStatus.WAITING) SeatStatus.EMPTY else status,
            isDealer = p.isDealer ?: false,
            isCurrentActor = (p.seatIndex ?: -1) == (currentTurn ?: -1)
        )
    }
    
    // Pad to 6 seats
    val paddedSeats = (0 until 6).map { idx ->
        seatList.find { it.seatIndex == idx } ?: PlayerSeat(seatIndex = idx)
    }
    
    return TableState(
        tableCode = tableCode ?: "",
        level = level ?: 1,
        smallBlind = smallBlind ?: 10,
        bigBlind = bigBlind ?: 20,
        seats = paddedSeats,
        communityCards = (communityCards ?: emptyList()).mapNotNull { Card.fromCode(it) },
        mainPot = pot ?: 0,
        playerCount = seatList.count { it.playerId != null },
        phase = parsePhase(status),
        currentPlayer = currentTurn?.toString() ?: ""
    )
}

fun ServerHandResult.toHandResult(): HandResult = HandResult(
    handNumber = handNumber ?: 0,
    winnerId = winnerId ?: playerId ?: "",
    winnerNickname = winnerName ?: winner ?: "",
    winAmount = amount ?: winAmount ?: 0,
    winningHand = winningHand ?: ""
)

private fun parsePhase(status: String?): TablePhase = when (status) {
    "waiting", "registering" -> TablePhase.WAITING
    "preflop" -> TablePhase.PREFLOP
    "flop" -> TablePhase.FLOP
    "turn" -> TablePhase.TURN
    "river" -> TablePhase.RIVER
    "showdown" -> TablePhase.SHOWDOWN
    else -> TablePhase.IDLE
}
