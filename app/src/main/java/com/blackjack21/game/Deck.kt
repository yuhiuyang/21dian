package com.blackjack21.game

import kotlin.random.Random

/**
 * 一副标准 52 张扑克牌（无大小王，总方案第 3 节）。
 *
 * @param presetOrder 测试注入用固定牌序：传入后每局 reset 都回到该牌序，保证测试可复现
 */
class Deck(private val random: Random = Random.Default, presetOrder: List<Card>? = null) {

    private val presetOrder: List<Card>? = presetOrder
    private var cards = ArrayDeque<Card>()

    init {
        reset()
    }

    /** 重洗整副牌（每局开始时调用） */
    fun reset() {
        cards = ArrayDeque(presetOrder ?: allCards())
        if (presetOrder == null) cards.shuffle(random)
    }

    fun remaining(): Int = cards.size

    /** 抽一张牌；抽空前牌堆不应耗尽（单局最多用二十张出头），耗尽说明实现有 bug */
    fun draw(): Card = cards.removeFirstOrNull()
        ?: throw IllegalStateException("牌堆已空，不应发生")

    companion object {
        /** 整副 52 张：4 花色 × 13 点数 */
        fun allCards(): List<Card> =
            Suit.entries.flatMap { suit -> Rank.entries.map { rank -> Card(rank, suit) } }
    }
}
