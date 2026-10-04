package com.blackjack21.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** 牌堆：52 张无大小王、洗牌、抽牌（总方案第 3 节） */
class DeckTest {

    @Test
    fun `整副52张且无重复`() {
        val cards = Deck.allCards()
        assertEquals(52, cards.size)
        assertEquals(52, cards.toSet().size)
        assertTrue(cards.all { it.suit in Suit.entries && it.rank in Rank.entries })
    }

    @Test
    fun `新牌堆抽52次正好抽空`() {
        val d = Deck()
        repeat(52) { d.draw() }
        assertEquals(0, d.remaining())
    }

    @Test
    fun `同一种子洗牌结果一致`() {
        val d1 = Deck(random = Random(7))
        val d2 = Deck(random = Random(7))
        val a = (1..52).map { d1.draw() }
        val b = (1..52).map { d2.draw() }
        assertEquals(a, b)
    }

    @Test
    fun `不同种子洗牌结果不同`() {
        val d1 = Deck(random = Random(1))
        val d2 = Deck(random = Random(2))
        val a = (1..52).map { d1.draw() }
        val b = (1..52).map { d2.draw() }
        assertNotEquals(a, b)
    }

    @Test
    fun `reset后恢复整副且顺序改变`() {
        val d = Deck(random = Random(9))
        val firstRun = (1..52).map { d.draw() }
        d.reset()
        assertEquals(52, d.remaining())
        val secondRun = (1..52).map { d.draw() }
        assertNotEquals(firstRun, secondRun)
    }

    @Test
    fun `固定牌序可注入并随reset复原`() {
        val order = listOf(Card(Rank.ACE, Suit.SPADES), Card(Rank.KING, Suit.HEARTS))
        val d = Deck(presetOrder = order)
        assertEquals(Card(Rank.ACE, Suit.SPADES), d.draw())
        assertEquals(Card(Rank.KING, Suit.HEARTS), d.draw())
        d.reset()
        assertEquals(Card(Rank.ACE, Suit.SPADES), d.draw())
    }
}
