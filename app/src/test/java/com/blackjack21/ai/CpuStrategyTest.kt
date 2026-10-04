package com.blackjack21.ai

import com.blackjack21.game.Card
import com.blackjack21.game.Hand
import com.blackjack21.game.Rank
import com.blackjack21.game.Suit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 阶段4：三种 CPU 性格的决策表测试（总方案第 8 节）。
 * 逐条验证骨架规则（≤11 必要 / 12~15 看庄家明牌 / 16 偏停 / 17+ 停）、
 * Double 时机（9/10/11）与下注性格区间；并守住永不越界的下限。
 */
class CpuStrategyTest {

    // ---- 构造工具 ----

    private fun handOf(vararg ranks: Rank): Hand {
        val h = Hand()
        ranks.forEach { h.add(Card(it, Suit.SPADES)) }
        return h
    }

    /** 两张非 A 硬牌组成指定点数（4~20），21 用 K+A（A 按 11） */
    private fun hardHand(v: Int): Hand {
        if (v == 21) return handOf(Rank.KING, Rank.ACE)
        require(v in 4..20) { "两点数硬牌构造不支持 $v" }
        val a = minOf(v - 2, 10)
        return handOf(
            Rank.entries.first { it.blackjackValue == a && it != Rank.ACE },
            Rank.entries.first { it.blackjackValue == v - a && it != Rank.ACE },
        )
    }

    private fun allStrategies() = listOf(
        "保守" to ConservativeStrategy,
        "平衡" to BalancedStrategy,
        "激进" to AggressiveStrategy,
    )

    // ---- 三性格共用的底线 ----

    @Test
    fun `11点及以下必须要牌`() {
        for ((_, s) in allStrategies()) {
            assertEquals(AiAction.HIT, s.decide(handOf(Rank.THREE, Rank.TWO), 2, false))
            assertEquals(AiAction.HIT, s.decide(handOf(Rank.SIX, Rank.FIVE), 11, false))
        }
    }

    @Test
    fun `任何点数都不会在21点继续要牌`() {
        for ((_, s) in allStrategies()) {
            for (up in 2..11) {
                assertEquals(AiAction.STAND, s.decide(hardHand(21), up, false))
            }
        }
    }

    @Test
    fun `不能加倍时任何策略都不会选DOUBLE`() {
        for ((_, s) in allStrategies()) {
            for (v in 9..11) {
                for (up in 2..11) {
                    val hand = hardHand(v)
                    assertTrue(
                        "v=$v up=$up 不应选 DOUBLE",
                        s.decide(hand, up, canDouble = false) != AiAction.DOUBLE,
                    )
                }
            }
        }
    }

    @Test
    fun `18点及以上全部停牌`() {
        for ((_, s) in allStrategies()) {
            for (v in 18..21) {
                for (up in 2..11) {
                    assertEquals("v=$v up=$up 应停牌", AiAction.STAND, s.decide(hardHand(v), up, false))
                }
            }
        }
    }

    // ---- 保守型：明牌极强才追、几乎不加倍、固定 10 注 ----

    @Test
    fun `保守型_12到15点只在庄家明牌9以上追牌`() {
        for (v in 12..15) {
            for (up in 2..8) assertEquals("v=$v up=$up 应停牌", AiAction.STAND, ConservativeStrategy.decide(hardHand(v), up, false))
            for (up in 9..11) assertEquals("v=$v up=$up 应要牌", AiAction.HIT, ConservativeStrategy.decide(hardHand(v), up, false))
        }
    }

    @Test
    fun `保守型_16点起一律停牌`() {
        for (up in 2..11) {
            assertEquals(AiAction.STAND, ConservativeStrategy.decide(hardHand(16), up, false))
            assertEquals(AiAction.STAND, ConservativeStrategy.decide(hardHand(17), up, false))
        }
    }

    @Test
    fun `保守型_只在硬11且庄家明牌弱时加倍`() {
        assertEquals(AiAction.DOUBLE, ConservativeStrategy.decide(handOf(Rank.SIX, Rank.FIVE), 6, true))
        assertEquals(AiAction.HIT, ConservativeStrategy.decide(handOf(Rank.SIX, Rank.FIVE), 7, true))
        assertEquals(AiAction.HIT, ConservativeStrategy.decide(handOf(Rank.SIX, Rank.FOUR), 6, true)) // 10 点不加倍
    }

    @Test
    fun `保守型_下注固定10_筹码不足时全押`() {
        assertEquals(10, ConservativeStrategy.bet(100, Random(1)))
        assertEquals(7, ConservativeStrategy.bet(7, Random(1)))
        assertEquals(1, ConservativeStrategy.bet(1, Random(1)))
    }

    // ---- 平衡型：明牌 7 以上追牌、16 偏停、10/11 加倍、下注 10~20 ----

    @Test
    fun `平衡型_12到15点跟随庄家明牌强弱`() {
        for (v in 12..15) {
            for (up in 2..6) assertEquals("v=$v up=$up 应停牌", AiAction.STAND, BalancedStrategy.decide(hardHand(v), up, false))
            for (up in 7..11) assertEquals("v=$v up=$up 应要牌", AiAction.HIT, BalancedStrategy.decide(hardHand(v), up, false))
        }
    }

    @Test
    fun `平衡型_16点偏向停牌_仅对10或A追一张`() {
        assertEquals(AiAction.STAND, BalancedStrategy.decide(hardHand(16), 9, false))
        assertEquals(AiAction.HIT, BalancedStrategy.decide(hardHand(16), 10, false))
        assertEquals(AiAction.HIT, BalancedStrategy.decide(hardHand(16), 11, false))
    }

    @Test
    fun `平衡型_10与11点常规加倍_9点挑弱庄`() {
        assertEquals(AiAction.DOUBLE, BalancedStrategy.decide(hardHand(10), 9, true))
        assertEquals(AiAction.DOUBLE, BalancedStrategy.decide(hardHand(11), 9, true))
        assertEquals(AiAction.HIT, BalancedStrategy.decide(hardHand(10), 10, true))
        assertEquals(AiAction.DOUBLE, BalancedStrategy.decide(handOf(Rank.FIVE, Rank.FOUR), 6, true))
        assertEquals(AiAction.HIT, BalancedStrategy.decide(handOf(Rank.FIVE, Rank.FOUR), 7, true))
    }

    @Test
    fun `平衡型_下注落在10到20_筹码不足时封顶`() {
        repeat(20) { seed ->
            val b = BalancedStrategy.bet(100, Random(seed))
            assertTrue("下注 $b 应在 10~20", b in 10..20)
        }
        assertEquals(15, BalancedStrategy.bet(15, Random(1)))
        assertEquals(5, BalancedStrategy.bet(5, Random(1)))
    }

    // ---- 激进型：明牌不弱就追到 17、9/10/11 全加倍、下注 20~30 ----

    @Test
    fun `激进型_12到16点庄家明牌4以上就追`() {
        for (v in 12..16) {
            for (up in 2..3) assertEquals("v=$v up=$up 应停牌", AiAction.STAND, AggressiveStrategy.decide(hardHand(v), up, false))
            for (up in 4..11) assertEquals("v=$v up=$up 应要牌", AiAction.HIT, AggressiveStrategy.decide(hardHand(v), up, false))
        }
    }

    @Test
    fun `激进型_17点还敢对10或A要牌`() {
        assertEquals(AiAction.STAND, AggressiveStrategy.decide(hardHand(17), 9, false))
        assertEquals(AiAction.HIT, AggressiveStrategy.decide(hardHand(17), 10, false))
        assertEquals(AiAction.HIT, AggressiveStrategy.decide(hardHand(17), 11, false))
    }

    @Test
    fun `激进型_9到11点只要能加倍就加倍`() {
        for (v in 9..11) {
            for (up in 2..11) {
                assertEquals("v=$v up=$up 应加倍", AiAction.DOUBLE, AggressiveStrategy.decide(hardHand(v), up, true))
            }
        }
    }

    @Test
    fun `激进型_下注落在20到30_筹码不足时封顶`() {
        repeat(20) { seed ->
            val b = AggressiveStrategy.bet(100, Random(seed))
            assertTrue("下注 $b 应在 20~30", b in 20..30)
        }
        assertEquals(25, AggressiveStrategy.bet(25, Random(1)))
        assertEquals(3, AggressiveStrategy.bet(3, Random(1)))
    }
}
