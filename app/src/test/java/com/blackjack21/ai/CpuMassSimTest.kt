package com.blackjack21.ai

import com.blackjack21.game.BlackjackEngine
import com.blackjack21.game.Deck
import com.blackjack21.game.GameState
import com.blackjack21.game.PlayerType
import com.blackjack21.game.TurnStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 阶段4验收：三种正式策略驱动的"大量自动局"（总方案第 17 节）。
 * 与界面层共用 [CpuDriver]，跑完整一场并守住四条硬约束：
 * 不死循环 / 破产不再下注 / 爆牌不再要牌 / Double 后不再行动；
 * 另外核对各性格的下注区间与"激进 > 平衡 > 保守"的注额梯度。
 */
class CpuMassSimTest {

    @Test
    fun `500场自动局全部正常结束且约束零违反`() {
        repeat(500) { i -> runFullGame(if (i % 2 == 0) 5 else 10, seed = i.toLong()) }
    }

    @Test
    fun `多真人组合混跑不出错`() {
        var seed = 500_000L
        for (humans in listOf(1, 2, 3, 4)) {
            repeat(40) {
                seed += 17
                runFullGame(if (it % 2 == 0) 5 else 10, seed = seed, humanCount = humans)
            }
        }
    }

    @Test
    fun `下注梯度为激进大于平衡大于保守`() {
        val sum = IntArray(5)
        val count = IntArray(5)
        repeat(150) { i ->
            runFullGame(10, seed = 10_000L + i, onBet = { seat, bet ->
                sum[seat] += bet
                count[seat]++
            })
        }
        val avgConservative = sum[2].toDouble() / count[2]
        val avgBalanced = sum[3].toDouble() / count[3]
        val avgAggressive = sum[4].toDouble() / count[4]
        assertTrue("保守型平均注应约 10，实际 $avgConservative", avgConservative in 8.0..10.0)
        assertTrue("平衡型平均注应约 15，实际 $avgBalanced", avgBalanced in 12.0..18.0)
        assertTrue("激进型平均注应约 25，实际 $avgAggressive", avgAggressive in 20.0..30.0)
        assertTrue("注额梯度应为 激进>平衡>保守", avgAggressive > avgBalanced && avgBalanced > avgConservative)
    }

    // ---- 单场驱动：与 BlackjackGameView 的 autoStep/confirmBets 同一套 CpuDriver ----

    /** 跑完整一场；onBet 在每次开局时收到（座位, 注额），用于统计 */
    private fun runFullGame(rounds: Int, seed: Long, onBet: (Int, Int) -> Unit = { _, _ -> }, humanCount: Int = 1) {
        val e = BlackjackEngine(Deck(random = Random(seed)))
        val betRandom = Random(seed * 31 + 7)
        val actRandom = Random(seed * 977 + 13)
        e.startGame(rounds, humanCount)

        var guard = 0
        while (e.state != GameState.GAME_OVER && guard++ < 2000) {
            when (e.state) {
                GameState.BETTING -> {
                    val bets = collectBets(e, betRandom)
                    bets.forEach { (seat, bet) -> onBet(seat, bet) }
                    e.startRound(bets)
                }
                GameState.PLAYER_TURN, GameState.CPU_PLAYER_2, GameState.CPU_PLAYER_3, GameState.CPU_PLAYER_4 -> {
                    val seat = e.currentSeat()
                    if (e.isHumanTurn()) {
                        e.stand(seat) // 占位真人：直接停牌（真人可在任意座位）
                    } else {
                        val p = e.bySeat(seat)
                        // 能被询问行动的座位必然还在回合中且未到 21（爆牌/停牌/Double 引擎都已终结回合）
                        assertEquals("座位${p.seat} 状态异常", TurnStatus.PLAYING, p.status)
                        assertTrue("座位${p.seat} 点数超 21 仍被要求行动", p.hand.bestValue() <= 21)
                        CpuDriver.act(e, seat, actRandom)
                    }
                }
                GameState.ROUND_RESULT -> e.nextRound()
                else -> {} // DEALER_TURN / SETTLEMENT 由引擎内部原子完成，不会停留
            }
            // 任何时刻筹码都不应为负
            assertTrue(e.players.all { it.chips >= 0 })
        }

        assertEquals("seed=$seed 2000 步内未结束，疑似死循环", GameState.GAME_OVER, e.state)
        assertTrue("局数异常：${e.round}", e.round in 1..rounds)

        val ranking = e.finalRanking()
        assertEquals(4, ranking.size)
        assertTrue(ranking.all { it.chips >= 0 })
        assertTrue("排名应按筹码降序", ranking.zipWithNext().all { (a, b) -> a.chips >= b.chips })
    }

    /** 为所有未破产座位定注：真人固定 10，CPU 按性格；并校验各性格注额上限 */
    private fun collectBets(e: BlackjackEngine, random: Random): Map<Int, Int> {
        val bets = mutableMapOf<Int, Int>()
        e.players.filter { !it.isBankrupt }.forEach { p ->
            val bet = if (p.type == PlayerType.HUMAN) minOf(10, p.chips)
            else CpuDriver.betFor(e, p.seat, random)
            val maxByType = when (p.type) {
                PlayerType.CPU_CONSERVATIVE -> 10
                PlayerType.CPU_BALANCED -> 20
                PlayerType.CPU_AGGRESSIVE -> 30
                PlayerType.HUMAN -> 10
            }
            assertTrue(
                "座位${p.seat}(${p.type}) 下注 $bet 超出性格上限 $maxByType",
                bet in 1..minOf(maxByType, p.chips),
            )
            bets[p.seat] = bet
        }
        // 破产座位绝不下注（引擎 check 兜底，这里显式再验一遍）
        e.players.filter { it.isBankrupt }.forEach { assertTrue(it.seat !in bets) }
        return bets
    }
}
