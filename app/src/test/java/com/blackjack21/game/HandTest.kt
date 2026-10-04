package com.blackjack21.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A 点数与爆牌规则（总方案第 14 节测试用例） */
class HandTest {

    private fun hand(vararg ranks: Rank): Hand {
        val h = Hand()
        ranks.forEach { h.add(Card(it, Suit.SPADES)) }
        return h
    }

    @Test
    fun `A+5 = 16`() {
        assertEquals(16, hand(Rank.ACE, Rank.FIVE).bestValue())
    }

    @Test
    fun `A+K = 21`() {
        assertEquals(21, hand(Rank.ACE, Rank.KING).bestValue())
    }

    @Test
    fun `A+A+9 = 21`() {
        assertEquals(21, hand(Rank.ACE, Rank.ACE, Rank.NINE).bestValue())
    }

    @Test
    fun `A+A+9+K = 21`() {
        assertEquals(21, hand(Rank.ACE, Rank.ACE, Rank.NINE, Rank.KING).bestValue())
    }

    @Test
    fun `K+9+5 = 24 爆牌`() {
        val h = hand(Rank.KING, Rank.NINE, Rank.FIVE)
        assertEquals(24, h.bestValue())
        assertTrue(h.isBust())
    }

    @Test
    fun `A+6 是软17`() {
        val h = hand(Rank.ACE, Rank.SIX)
        assertEquals(17, h.bestValue())
        assertTrue(h.isSoft())
    }

    @Test
    fun `A+6+K 是硬17`() {
        val h = hand(Rank.ACE, Rank.SIX, Rank.KING)
        assertEquals(17, h.bestValue())
        assertFalse(h.isSoft())
    }

    @Test
    fun `两张10值牌 20点不爆`() {
        assertFalse(hand(Rank.KING, Rank.QUEEN).isBust())
    }
}
