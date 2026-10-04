package com.blackjack21.game

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 结算规则测试（总方案第 15 节：9 种情况必须全覆盖）。
 * 下注时筹码已扣除，settle 返回的返还额即局末筹码净增加量。
 */
class SettlementTest {

    private fun hand(vararg ranks: Rank): Hand {
        val h = Hand()
        ranks.forEach { h.add(Card(it, Suit.SPADES)) }
        return h
    }

    private fun settle(playerRanks: List<Rank>, bet: Int, dealerRanks: List<Rank>) =
        Settlement.settle(hand(*playerRanks.toTypedArray()), bet, hand(*dealerRanks.toTypedArray()))

    // 1. 玩家 > 庄家
    @Test
    fun `玩家比庄家大_赢`() {
        val (outcome, payout) = settle(listOf(Rank.KING, Rank.QUEEN), 10, listOf(Rank.KING, Rank.NINE))
        assertEquals(Outcome.WIN, outcome)
        assertEquals(20, payout)
    }

    // 2. 玩家 < 庄家
    @Test
    fun `玩家比庄家小_输`() {
        val (outcome, payout) = settle(listOf(Rank.NINE, Rank.NINE), 10, listOf(Rank.QUEEN, Rank.TEN))
        assertEquals(Outcome.LOSE, outcome)
        assertEquals(0, payout)
    }

    // 3. 玩家 = 庄家
    @Test
    fun `点数相同_平局退注`() {
        val (outcome, payout) = settle(listOf(Rank.TEN, Rank.NINE), 10, listOf(Rank.JACK, Rank.NINE))
        assertEquals(Outcome.PUSH, outcome)
        assertEquals(10, payout)
    }

    // 4. 玩家 Bust（庄家即便也爆，玩家也是先爆先输）
    @Test
    fun `玩家爆牌_输`() {
        val (outcome, payout) =
            settle(listOf(Rank.KING, Rank.NINE, Rank.FIVE), 10, listOf(Rank.KING, Rank.QUEEN, Rank.TWO))
        assertEquals(Outcome.BUST, outcome)
        assertEquals(0, payout)
    }

    // 5. 庄家 Bust
    @Test
    fun `庄家爆牌_玩家赢`() {
        val (outcome, payout) =
            settle(listOf(Rank.TEN, Rank.EIGHT), 10, listOf(Rank.KING, Rank.NINE, Rank.FIVE))
        assertEquals(Outcome.WIN, outcome)
        assertEquals(20, payout)
    }

    // 6. 玩家 Blackjack（总方案第 5 节示例：下注 20 → 得 20 本金 + 30 盈利）
    @Test
    fun `玩家Blackjack_庄家普通_赔3比2`() {
        val (outcome, payout) = settle(listOf(Rank.ACE, Rank.KING), 20, listOf(Rank.TEN, Rank.QUEEN))
        assertEquals(Outcome.BLACKJACK, outcome)
        assertEquals(50, payout)
    }

    // 7. 庄家 Blackjack
    @Test
    fun `庄家Blackjack_玩家普通_输`() {
        val (outcome, payout) = settle(listOf(Rank.TEN, Rank.QUEEN), 10, listOf(Rank.ACE, Rank.KING))
        assertEquals(Outcome.LOSE, outcome)
        assertEquals(0, payout)
    }

    // 8. 双方 Blackjack
    @Test
    fun `双方Blackjack_平局退注`() {
        val (outcome, payout) = settle(listOf(Rank.ACE, Rank.KING), 10, listOf(Rank.ACE, Rank.QUEEN))
        assertEquals(Outcome.PUSH, outcome)
        assertEquals(10, payout)
    }

    // 9. Double Down：结算按翻倍后的注额生效
    @Test
    fun `Double后的翻倍注按新注额结算`() {
        val (outcome, payout) = settle(listOf(Rank.TEN, Rank.NINE), 20, listOf(Rank.TEN, Rank.SEVEN))
        assertEquals(Outcome.WIN, outcome)
        assertEquals(40, payout)
    }

    @Test
    fun `Blackjack盈利为3比2_奇数注向下取整`() {
        assertEquals(15, Settlement.blackjackProfit(10))
        assertEquals(30, Settlement.blackjackProfit(20))
        assertEquals(22, Settlement.blackjackProfit(15))
    }

    @Test
    fun `玩家Blackjack遇庄家爆牌_仍按Blackjack赔付`() {
        val (outcome, payout) =
            settle(listOf(Rank.ACE, Rank.TEN), 10, listOf(Rank.KING, Rank.NINE, Rank.FIVE))
        assertEquals(Outcome.BLACKJACK, outcome)
        assertEquals(25, payout)
    }
}
