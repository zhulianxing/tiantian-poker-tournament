package com.pokernight.tvdisplay.data.model

data class Card(
    val suit: String,  // spades, hearts, diamonds, clubs
    val rank: String,  // 2-10, J, Q, K, A
    val faceUp: Boolean = true
) {
    val symbol: String get() = when (suit) {
        "spades" -> "♠"; "hearts" -> "♥"; "diamonds" -> "♦"; "clubs" -> "♣"; else -> "?"
    }
    val color: String get() = if (suit in listOf("hearts", "diamonds")) "#E53935" else "#1A1A1A"
    val display: String get() = "$rank$symbol"
    
    companion object {
        fun fromCode(code: String): Card? {
            if (code.isBlank() || code.length < 2) return null
            val rank = code.dropLast(1)
            val suitChar = code.last().toString()
            val suitMap = mapOf("♠" to "spades", "♥" to "hearts", "♦" to "diamonds", "♣" to "clubs")
            return Card(suitMap[suitChar] ?: return null, rank)
        }
    }
}
