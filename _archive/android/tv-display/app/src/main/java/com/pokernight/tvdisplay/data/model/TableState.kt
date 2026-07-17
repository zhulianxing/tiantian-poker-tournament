package com.pokernight.tvdisplay.data.model

data class TableState(
    val tableCode: String,
    val phase: TablePhase = TablePhase.IDLE,
    val level: Int = 1,
    val maxLevel: Int = 10,
    val smallBlind: Int = 10,
    val bigBlind: Int = 20,
    val ante: Int = 0,
    val blindCountdown: Int = 600,
    val seats: List<PlayerSeat> = List(6) { PlayerSeat(seatIndex = it) },
    val communityCards: List<Card> = emptyList(),
    val mainPot: Int = 0,
    val sidePots: List<SidePot> = emptyList(),
    val currentPlayer: String = "",
    val dealerSeat: Int = 0,
    val playerCount: Int = 0
)

enum class TablePhase(val label: String) {
    IDLE("等待中"),
    WAITING("等待玩家"),
    PREFLOP("翻牌前"),
    FLOP("翻牌"),
    TURN("转牌"),
    RIVER("河牌"),
    SHOWDOWN("摊牌")
}
