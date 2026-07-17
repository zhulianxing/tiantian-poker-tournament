package com.pokernight.tvdisplay.data.model

data class PlayerSeat(
    val seatIndex: Int = 0,
    val playerId: String = "",
    val nickname: String = "",
    val chipCount: Int = 0,
    val status: String = "empty",
    val isDealer: Boolean = false,
    val isActing: Boolean = false,
    val lastAction: String = "",
    val currentBet: Int = 0,
) {
    companion object {
        fun fromMap(map: Map<String, Any?>, seatIndex: Int): PlayerSeat {
            return PlayerSeat(
                seatIndex = seatIndex,
                playerId = map["playerId"] as? String ?: "",
                nickname = map["nickname"] as? String ?: "",
                chipCount = (map["chipCount"] as? Number)?.toInt() ?: 0,
                status = map["status"] as? String ?: "empty",
                isDealer = (map["isDealer"] as? Boolean) ?: false,
                isActing = (map["isActing"] as? Boolean) ?: false,
                lastAction = map["lastAction"] as? String ?: "",
                currentBet = (map["currentBet"] as? Number)?.toInt() ?: 0,
            )
        }
    }
}
