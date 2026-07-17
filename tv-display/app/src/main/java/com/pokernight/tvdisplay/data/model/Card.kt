package com.pokernight.tvdisplay.data.model

data class Card(
    val suit: String = "",
    val rank: String = "",
    val value: Int = 0,
) {
    val suitSymbol: String
        get() = when (suit.lowercase()) {
            "spades", "s", "♠" -> "♠"
            "hearts", "h", "♥" -> "♥"
            "diamonds", "d", "♦" -> "♦"
            "clubs", "c", "♣" -> "♣"
            else -> suit
        }

    val isRed: Boolean
        get() = suitSymbol == "♥" || suitSymbol == "♦"

    val displayRank: String
        get() = rank.ifEmpty { "?" }

    companion object {
        fun fromMap(map: Map<String, Any?>): Card {
            return Card(
                suit = map["suit"] as? String ?: "",
                rank = map["rank"] as? String ?: "",
                value = (map["value"] as? Number)?.toInt() ?: 0,
            )
        }

        fun fromList(list: List<*>): List<Card> {
            return list.mapNotNull { item ->
                (item as? Map<String, Any?>)?.let { fromMap(it) }
            }
        }
    }
}
