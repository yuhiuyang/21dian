package com.blackjack21.game

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 庄家固定规则（总方案第 6、14 节）：≤16 要牌，≥17 停（软 17 也停） */
class DealerTest {

    private fun dealer(vararg ranks: Rank): Dealer {
        val d = Dealer()
        ranks.forEach { d.hand.add(Card(it, Suit.SPADES)) }
        return d
    }

    @Test
    fun `16点要牌`() {
        assertTrue(dealer(Rank.TEN, Rank.SIX).shouldHit())
    }

    @Test
    fun `17点停牌`() {
        assertFalse(dealer(Rank.TEN, Rank.SEVEN).shouldHit())
    }

    @Test
    fun `软17停牌`() {
        assertFalse(dealer(Rank.ACE, Rank.SIX).shouldHit())
    }

    @Test
    fun `20点停牌`() {
        assertFalse(dealer(Rank.TEN, Rank.QUEEN).shouldHit())
    }

    @Test
    fun `10点要牌`() {
        assertTrue(dealer(Rank.TEN).shouldHit())
    }
}
