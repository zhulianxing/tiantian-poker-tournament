package com.pokernight.tvdisplay.data.model

data class HandResult(
    val handNumber: Int,
    val winnerId: String,
    val winnerNickname: String,
    val winAmount: Int,
    val winningHand: String
)
