package com.blackjack21.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Blackjack 判定（总方案第 5、14 节） */
class BlackjackTest {

    private fun hand(vararg ranks: Rank): Hand {
        val h = Hand()
        ranks.forEach { h.add(Card(it, Suit.SPADES)) }
        return h
    }

    @Test
    fun `A+K 是 Blackjack`() {
        assertTrue(hand(Rank.ACE, Rank.KING).isBlackjack())
    }

    @Test
    fun `A+10 是 Blackjack`() {
        assertTrue(hand(Rank.ACE, Rank.TEN).isBlackjack())
    }

    @Test
    fun `A+J 与 A+Q 也是 Blackjack`() {
        assertTrue(hand(Rank.ACE, Rank.JACK).isBlackjack())
        assertTrue(hand(Rank.ACE, Rank.QUEEN).isBlackjack())
    }

    @Test
    fun `7+7+7 是21点但不是 Blackjack`() {
        val h = hand(Rank.SEVEN, Rank.SEVEN, Rank.SEVEN)
        assertEquals(21, h.bestValue())
        assertFalse(h.isBlackjack())
    }

    @Test
    fun `A+5+5 是21点但不是 Blackjack`() {
        val h = hand(Rank.ACE, Rank.FIVE, Rank.FIVE)
        assertEquals(21, h.bestValue())
        assertFalse(h.isBlackjack())
    }

    @Test
    fun `K+Q 是20点不是 Blackjack`() {
        assertFalse(hand(Rank.KING, Rank.QUEEN).isBlackjack())
    }
}
