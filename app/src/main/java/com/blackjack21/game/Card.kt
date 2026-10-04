package com.blackjack21.game

/** 花色 */
enum class Suit(val symbol: String) {
    SPADES("♠"), HEARTS("♥"), CLUBS("♣"), DIAMONDS("♦")
}

/**
 * 点数。J/Q/K 记 10；A 的 1/11 由 [Hand.bestValue] 动态取最优（总方案第 3 节）。
 */
enum class Rank(val display: String, val blackjackValue: Int) {
    TWO("2", 2), THREE("3", 3), FOUR("4", 4), FIVE("5", 5),
    SIX("6", 6), SEVEN("7", 7), EIGHT("8", 8), NINE("9", 9),
    TEN("10", 10), JACK("J", 10), QUEEN("Q", 10), KING("K", 10),
    ACE("A", 11)
}

/** 一张扑克牌 */
data class Card(val rank: Rank, val suit: Suit) {
    override fun toString(): String = "${rank.display}${suit.symbol}"
}
