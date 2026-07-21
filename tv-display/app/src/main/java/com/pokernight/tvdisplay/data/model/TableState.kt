package com.pokernight.tvdisplay.data.model

/** 赢家横幅信息（hand_result 后展示，下一手开始清除） */
data class WinnerBanner(
    val winnerName: String = "",
    val winAmount: Int = 0,
    val handName: String = "",
)

data class TableState(
    val tableCode: String = "",
    val phase: String = "idle",
    val displayCode: String = "",
    val blindLevel: Int = 1,
    val sb: Int = 10,
    val bb: Int = 20,
    val pot: Int = 0,
    val communityCards: List<Card> = emptyList(),
    val seats: List<PlayerSeat> = emptyList(),
    val actingIndex: Int = -1,
    val dealerIndex: Int = 0,
    val countdown: Int = 0,
    val handNumber: Int = 0,
    val stage: String = "",
    val currentBet: Int = 0,
    val handHistory: List<String> = emptyList(),
    val rankings: List<Map<String, Any>> = emptyList(),
    val connected: Boolean = false,
    val errorMessage: String = "",
    val winnerBanner: WinnerBanner? = null,
    /** 摊牌时各座位的明牌底牌：seatIndex → 2 张底牌 */
    val showdownHands: Map<Int, List<Card>> = emptyMap(),
) {
    val isRegistering: Boolean
        get() = phase == "registering"

    val isStarted: Boolean
        get() = phase == "started"

    val isFinished: Boolean
        get() = phase == "finished"

    val activePlayerCount: Int
        get() = seats.count {
            it.status != "empty" && it.status != "eliminated"
        }

    val totalPlayerCount: Int
        get() = seats.count { it.status != "empty" }
}
