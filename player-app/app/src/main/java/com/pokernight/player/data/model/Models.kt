package com.pokernight.player.data.model

data class Player(
    val id: String,
    val nickname: String,
    val email: String,
    val avatar: String = "🃏",
)

data class SendCodeRequest(
    val email: String,
    val purpose: String, // "login" | "register"
)

data class SendCodeResponse(
    val success: Boolean,
    val message: String? = null,
)

data class LoginRequest(
    val email: String,
    val code: String,
)

data class RegisterRequest(
    val email: String,
    val code: String,
    val nickname: String,
)

data class AuthResponse(
    val player: Player,
    val token: String,
)

data class TableInfo(
    val id: String,
    val code: String,
    val label: String,
    val venueName: String,
    val launchFee: Int,
    val maxPlayers: Int,
    val status: String,
)

data class TableStatus(
    val table: TableInfo,
    val tournament: Tournament?,
    val players: List<TournamentPlayer>,
)

data class Tournament(
    val id: String,
    val displayCode: String,
    val status: String,
    val playerCount: Int,
    val maxPlayers: Int,
    val launchFee: Int,
    val startChips: Int = 1000,
    val sb: Int = 10,
    val bb: Int = 20,
)

data class TournamentPlayer(
    val id: String,
    val playerId: String,
    val seatIndex: Int,
    val chipCount: Int,
    val status: String,
    val nickname: String,
    val avatar: String,
)

data class JoinResponse(
    val success: Boolean = true,
    val seatIndex: Int = 0,
    val tournamentId: String = "",
    val chipCount: Int = 0,
    val startChips: Int = 0,
)

data class Card(
    val suit: String = "",
    val rank: String = "",
    val value: Int = 0,
)

data class GameState(
    val tableCode: String = "",
    val tournamentId: String = "",
    val phase: String = "idle",
    val handNumber: Int = 0,
    val blindLevel: Int = 1,
    val sb: Int = 10,
    val bb: Int = 20,
    val pot: Int = 0,
    val currentBet: Int = 0,
    val communityCards: List<Card> = emptyList(),
    val holeCards: List<Card> = emptyList(),
    val seats: List<SeatInfo> = emptyList(),
    val actingIndex: Int = -1,
    val dealerIndex: Int = 0,
    val mySeatIndex: Int = -1,
    val myChips: Int = 0,
    val myCurrentBet: Int = 0,
    val isMyTurn: Boolean = false,
    val countdown: Int = 0,
)

data class SeatInfo(
    val seatIndex: Int = 0,
    val playerId: String = "",
    val nickname: String = "",
    val chipCount: Int = 0,
    val currentBet: Int = 0,
    val status: String = "empty",
    val isDealer: Boolean = false,
    val isActing: Boolean = false,
    val lastAction: String = "",
)

data class GameHistory(
    val id: String,
    val tournamentId: String,
    val tableCode: String,
    val rank: Int,
    val playerCount: Int,
    val prize: Int,
    val date: String,
)
