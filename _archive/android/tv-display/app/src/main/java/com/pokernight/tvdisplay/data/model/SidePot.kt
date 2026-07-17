package com.pokernight.tvdisplay.data.model

data class SidePot(
    val amount: Int,
    val eligiblePlayerIds: List<String> = emptyList()
)
