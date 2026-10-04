package com.blackjack21.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 整场流程回归：从第一局到最终排名全程无死循环、无非法状态。
 * CPU 行动与下注走正式的 ai.CpuDriver（与界面层 BlackjackGameView 完全同一套逻辑），
 * 大规模跑批与性格专项见 ai 包的 CpuMassSimTest / CpuStrategyTest。
 */
class GameFlowTest {

    @Test
    fun `整场5局能跑到最终排名`() = runFullGame(5)

    @Test
    fun `整场10局能跑到最终排名`() = runFullGame(10)

    @Test
    fun `多场连续运行不出错`() {
        repeat(20) { runFullGame(if (it % 2 == 0) 5 else 10, seed = it.toLong()) }
    }

    private fun runFullGame(rounds: Int, seed: Long = 2026L) {
        val e = BlackjackEngine(Deck(random = Random(seed)))
        val betRandom = Random(seed * 31 + 7)
        val actRandom = Random(seed * 977 + 13)
        e.startGame(rounds)

        var guard = 0
        while (e.state != GameState.GAME_OVER && guard++ < 2000) {
            when (e.state) {
                GameState.BETTING -> {
                    val bets = e.players.filter { !it.isBankrupt }.associate {
                        it.seat to if (it.type == PlayerType.HUMAN) minOf(10, it.chips)
                        else com.blackjack21.ai.CpuDriver.betFor(e, it.seat, betRandom)
                    }
                    e.startRound(bets)
                }
                GameState.PLAYER_TURN, GameState.CPU_PLAYER_2, GameState.CPU_PLAYER_3, GameState.CPU_PLAYER_4 -> {
                    // 占位真人：轮到真人（可能是多个座位）直接停牌；CPU 走正式策略
                    if (e.isHumanTurn()) e.stand(e.currentSeat())
                    else com.blackjack21.ai.CpuDriver.act(e, e.currentSeat(), actRandom)
                }
                GameState.ROUND_RESULT -> e.nextRound()
                else -> {} // DEALER_TURN 等由引擎内部原子完成，不会停留
            }
            // 任何时刻筹码都不应为负
            assertTrue(e.players.all { it.chips >= 0 })
        }

        assertEquals("2000 步内未结束，疑似死循环", GameState.GAME_OVER, e.state)
        assertTrue("局数异常：${e.round}", e.round in 1..rounds)

        val ranking = e.finalRanking()
        assertEquals(4, ranking.size)
        assertTrue(ranking.all { it.chips >= 0 })
        assertTrue("排名应按筹码降序", ranking.zipWithNext().all { (a, b) -> a.chips >= b.chips })
        // 未破产者下一局还要下注，破产者筹码必为 0
        assertTrue(ranking.all { !it.isBankrupt || it.chips == 0 })
    }
}
