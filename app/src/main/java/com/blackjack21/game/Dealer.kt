package com.blackjack21.game

/**
 * 庄家：不使用 AI，按固定规则行动（总方案第 6 节）。
 */
class Dealer {
    val hand = Hand()

    /** ≤16 要牌，≥17 停牌（软 17 也停） */
    fun shouldHit(): Boolean = hand.bestValue() <= GameConfig.DEALER_HIT_LIMIT

    fun resetForRound() {
        hand.clear()
    }
}
