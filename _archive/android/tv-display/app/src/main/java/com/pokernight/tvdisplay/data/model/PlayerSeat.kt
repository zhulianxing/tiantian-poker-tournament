package com.pokernight.tvdisplay.data.model

data class PlayerSeat(
    val seatIndex: Int,
    val playerId: String? = null,
    val nickname: String = "",
    val chipCount: Int = 0,
    val currentBet: Int = 0,
    val status: SeatStatus = SeatStatus.EMPTY,
    val isDealer: Boolean = false,
    val isCurrentActor: Boolean = false,
    val lastAction: String? = null,
    val avatarColor: String = ""
)

enum class SeatStatus(val label: String, val emoji: String) {
    EMPTY("空座", "⚪"),
    WAITING("坐席中", "🟢"),
    ACTING("行动中", "💬"),
    FOLDED("已弃牌", "✋"),
    ALL_IN("All-In", "🟡"),
    DISCONNECTED("断线", "🔴"),
    BUSTED("已淘汰", "🏆"),
    SITOUT("暂离", "⏸️")
}
