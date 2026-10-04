package com.blackjack21.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 多人需求验收：1~4 名本地真人、总席位固定 4、真人按数量依次占座 1..n，
 * CPU 按 保守→平衡→激进 顺序补位；真人全部破产即整场提前结束。
 */
class BlackjackMultiplayerTest {

    // ---- 座位重建 ----

    @Test
    fun `默认单真人布局与阶段4一致`() {
        val e = BlackjackEngine()
        e.startGame(5)
        // 真人统一命名"玩家N"（多人需求），单真人时座位2~4仍为 保守/平衡/激进
        assertEquals(listOf("玩家1", "CPU·保守", "CPU·平衡", "CPU·激进"), e.players.map { it.name })
        assertEquals(
            listOf(PlayerType.HUMAN, PlayerType.CPU_CONSERVATIVE, PlayerType.CPU_BALANCED, PlayerType.CPU_AGGRESSIVE),
            e.players.map { it.type },
        )
    }

    @Test
    fun `按人数依次占座_真人命名玩家N_CPU按顺序补位`() {
        val e = BlackjackEngine()
        e.startGame(5, humanCount = 2)
        assertEquals(listOf("玩家1", "玩家2", "CPU·保守", "CPU·平衡"), e.players.map { it.name })
        assertEquals(PlayerType.HUMAN, e.bySeat(2).type)
        assertEquals(PlayerType.CPU_CONSERVATIVE, e.bySeat(3).type)

        e.startGame(10, humanCount = 3)
        assertEquals(listOf("玩家1", "玩家2", "玩家3", "CPU·保守"), e.players.map { it.name })

        e.startGame(5, humanCount = 4)
        assertTrue(e.players.all { it.type == PlayerType.HUMAN })
        assertEquals(listOf("玩家1", "玩家2", "玩家3", "玩家4"), e.players.map { it.name })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `真人数不允许0人`() {
        BlackjackEngine().startGame(5, humanCount = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `真人数不允许超过4人`() {
        BlackjackEngine().startGame(5, humanCount = 5)
    }

    // ---- 真人全部破产提前结束（预置牌序强制真人全输）----

    @Test
    fun `两名真人全押输给庄家20点_本场立即结束`() {
        // 发牌顺序：各座第1张 → 庄家第1张 → 各座第2张 → 庄家第2张。
        // 前两名真人全押 100 后停牌只得 5/9 点，庄家 K+K=20 → 真人全破产。
        val preset = listOf(
            Card(Rank.TWO, Suit.SPADES), Card(Rank.FOUR, Suit.SPADES),
            Card(Rank.TWO, Suit.HEARTS), Card(Rank.TWO, Suit.DIAMONDS),
            Card(Rank.KING, Suit.CLUBS),
            Card(Rank.THREE, Suit.SPADES), Card(Rank.FIVE, Suit.SPADES),
            Card(Rank.THREE, Suit.HEARTS), Card(Rank.THREE, Suit.DIAMONDS),
            Card(Rank.KING, Suit.SPADES),
        ) + Deck.allCards().drop(10)
        val e = BlackjackEngine(Deck(presetOrder = preset))
        e.startGame(5, humanCount = 2)
        e.startRound(mapOf(1 to 100, 2 to 100, 3 to 10, 4 to 10))

        var guard = 0
        while (e.state != GameState.ROUND_RESULT && guard++ < 100) {
            val seat = e.currentSeat()
            if (seat > 0) e.stand(seat) // 全员停牌
        }
        assertEquals(GameState.ROUND_RESULT, e.state)
        assertTrue("两名真人应已破产", e.bySeat(1).isBankrupt && e.bySeat(2).isBankrupt)

        e.nextRound()
        assertEquals("真人全部破产应立即整场结束", GameState.GAME_OVER, e.state)
        assertEquals(1, e.round)
    }

    // ---- 四人全真人完整一场 ----

    @Test
    fun `四名真人轮流停牌可完整跑完10局`() {
        val e = BlackjackEngine(Deck(random = Random(7L)))
        e.startGame(10, humanCount = 4)
        var guard = 0
        while (e.state != GameState.GAME_OVER && guard++ < 2000) {
            when (e.state) {
                GameState.BETTING ->
                    e.startRound(e.players.filter { !it.isBankrupt }.associate { it.seat to minOf(10, it.chips) })
                GameState.PLAYER_TURN, GameState.CPU_PLAYER_2, GameState.CPU_PLAYER_3, GameState.CPU_PLAYER_4 -> {
                    assertTrue("全真人场次每一手都应是真人", e.isHumanTurn())
                    e.stand(e.currentSeat())
                }
                GameState.ROUND_RESULT -> e.nextRound()
                else -> {}
            }
            assertTrue(e.players.all { it.chips >= 0 })
        }
        assertEquals("2000 步内应跑完整场", GameState.GAME_OVER, e.state)
        assertEquals(10, e.round)
        assertEquals(4, e.finalRanking().size)
    }
}
