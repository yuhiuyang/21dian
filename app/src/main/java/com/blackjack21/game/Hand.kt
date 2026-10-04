package com.blackjack21.game

/**
 * 手牌与点数计算：A 自动按“最优且不爆牌”取 1 或 11（总方案第 3 节）。
 */
class Hand {

    private val _cards = mutableListOf<Card>()

    val cards: List<Card> get() = _cards

    val size: Int get() = _cards.size

    fun add(card: Card) {
        _cards.add(card)
    }

    fun clear() {
        _cards.clear()
    }

    /** A 按 11 计时的总点数与 11 计的 A 的个数 */
    private fun totals(): Pair<Int, Int> {
        var total = 0
        var aces = 0
        for (card in _cards) {
            if (card.rank == Rank.ACE) {
                aces++
                total += 11
            } else {
                total += card.rank.blackjackValue
            }
        }
        return total to aces
    }

    /** 最优点数：A 先按 11 计，超过 21 再逐个降为 1 */
    fun bestValue(): Int {
        var (total, aces) = totals()
        while (total > 21 && aces > 0) {
            total -= 10
            aces--
        }
        return total
    }

    /** 是否软点数（仍有 A 按 11 计） */
    fun isSoft(): Boolean {
        val (total, aces) = totals()
        return aces > 0 && total <= 21
    }

    /** Blackjack = 起手恰好两张且 21 点（即 A + 10/J/Q/K，总方案第 5 节） */
    fun isBlackjack(): Boolean = size == 2 && bestValue() == 21

    /** 爆牌 = 超过 21 点 */
    fun isBust(): Boolean = bestValue() > 21
}
