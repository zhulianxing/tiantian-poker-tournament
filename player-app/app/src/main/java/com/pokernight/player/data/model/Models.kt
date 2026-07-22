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

/** 牌面唯一键（rank + 规范化花色符号），对齐网页 cKey()，用于摊牌最佳 5 张判等 */
fun Card.key(): String {
    val s = when (suit.lowercase()) {
        "spades", "spade", "♠" -> "♠"
        "hearts", "heart", "♥" -> "♥"
        "diamonds", "diamond", "♦" -> "♦"
        "clubs", "club", "♣" -> "♣"
        else -> suit
    }
    return rank + s
}

/** 每手结果（hand_result 事件），用于结果浮层；下一手开始清除 */
data class HandResultInfo(
    val winnerId: String = "",
    val winnerName: String = "",
    val winAmount: Int = 0,
    val handName: String = "",
)

/** 赛事最终排名（tournament_finished 事件的 rankings），用于结算浮层 */
data class FinalRankInfo(
    val playerId: String = "",
    val nickname: String = "",
    val rank: Int = 0,
    val chips: Int = 0,
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
    val stage: String = "",
    /** 操作流水（最新在末尾），最多保留 3 条 */
    val actionLog: List<String> = emptyList(),
    /** 轮到我时的操作时限（毫秒），由 action_timer_started 下发 */
    val turnTimeoutMs: Int = 15000,
    val handResult: HandResultInfo? = null,
    /** 我被淘汰时的最终名次；null = 未淘汰 */
    val myEliminatedRank: Int? = null,
    /** 赛事结束后的最终排名表（tournament_finished 下发），空 = 未结束 */
    val finalRankings: List<FinalRankInfo> = emptyList(),
    /** 摊牌亮出的底牌：seatIndex → 底牌（showdown 的 allResults/winners），新一手清空 */
    val showdownHands: Map<Int, List<Card>> = emptyMap(),
    /** 赢家最佳 5 张牌（Card.key 判等），用于金色描边高亮 */
    val winCards: Set<String> = emptySet(),
    /** 赢家座位集合（showdown winners 解析） */
    val winSeats: Set<Int> = emptySet(),
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
