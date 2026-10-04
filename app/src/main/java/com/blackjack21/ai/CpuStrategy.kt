package com.blackjack21.ai

import com.blackjack21.game.BlackjackEngine
import com.blackjack21.game.Hand
import com.blackjack21.game.PlayerType
import kotlin.random.Random

/** CPU 本回合的行动选择 */
enum class AiAction { HIT, STAND, DOUBLE }

/**
 * CPU 策略（总方案第 8 节）：纯决策函数，不持有引擎状态。
 *
 * 三种性格共用同一骨架（≤11 必要 / 12~15 看庄家明牌 / 16 偏向停牌 / 17+ 停牌），
 * 只差"多大胆"的阈值与下注区间；不引入概率模拟（总方案第 27 节）。
 */
interface CpuStrategy {

    /**
     * 本回合行动。
     * @param hand 当前手牌
     * @param dealerUpcard 庄家明牌点数（A 按 11，即 2~11）
     * @param canDouble 引擎判定能否加倍（首次行动、两张牌、筹码足够）
     */
    fun decide(hand: Hand, dealerUpcard: Int, canDouble: Boolean): AiAction

    /** 本局下注额：必须落在 1~chips；筹码低于性格区间下限时全押，绝不越界 */
    fun bet(chips: Int, random: Random): Int

    companion object {
        /** 按座位类型取策略（真人座位没有策略） */
        fun of(type: PlayerType): CpuStrategy = when (type) {
            PlayerType.CPU_CONSERVATIVE -> ConservativeStrategy
            PlayerType.CPU_BALANCED -> BalancedStrategy
            PlayerType.CPU_AGGRESSIVE -> AggressiveStrategy
            PlayerType.HUMAN -> error("真人玩家不走 CPU 策略")
        }
    }
}

/**
 * 保守型：下注固定 10。只有庄家明牌极强（≥9）时才在 15 点以下追牌，
 * 16 点一律停牌；只在硬 11 且庄家明牌弱时加倍。
 */
object ConservativeStrategy : CpuStrategy {
    override fun decide(hand: Hand, dealerUpcard: Int, canDouble: Boolean): AiAction {
        val v = hand.bestValue()
        if (canDouble && v == 11 && dealerUpcard <= 6) return AiAction.DOUBLE
        return when {
            v <= 11 -> AiAction.HIT
            v <= 15 -> if (dealerUpcard >= 9) AiAction.HIT else AiAction.STAND
            else -> AiAction.STAND
        }
    }

    override fun bet(chips: Int, random: Random): Int = minOf(10, chips)
}

/**
 * 平衡型：下注 10~20。教科书式打法——庄家明牌强（≥7）追点、弱则停牌等庄家爆；
 * 16 点仅在明牌 10/A 时搏一把；10/11 点常规加倍，9 点挑弱庄加倍。
 */
object BalancedStrategy : CpuStrategy {
    override fun decide(hand: Hand, dealerUpcard: Int, canDouble: Boolean): AiAction {
        val v = hand.bestValue()
        if (canDouble && (v == 10 || v == 11) && dealerUpcard <= 9) return AiAction.DOUBLE
        if (canDouble && v == 9 && dealerUpcard <= 6) return AiAction.DOUBLE
        return when {
            v <= 11 -> AiAction.HIT
            v <= 15 -> if (dealerUpcard >= 7) AiAction.HIT else AiAction.STAND
            v == 16 -> if (dealerUpcard >= 10) AiAction.HIT else AiAction.STAND
            else -> AiAction.STAND
        }
    }

    override fun bet(chips: Int, random: Random): Int = (10..20).random(random).coerceAtMost(chips)
}

/**
 * 激进型：下注 20~30。庄家明牌不弱（≥4）就一路追到 17，甚至 17 点还敢对 10/A 要牌；
 * 9/10/11 点只要能加倍就加倍。
 */
object AggressiveStrategy : CpuStrategy {
    override fun decide(hand: Hand, dealerUpcard: Int, canDouble: Boolean): AiAction {
        val v = hand.bestValue()
        if (canDouble && v in 9..11) return AiAction.DOUBLE
        return when {
            v <= 11 -> AiAction.HIT
            v <= 16 -> if (dealerUpcard >= 4) AiAction.HIT else AiAction.STAND
            v == 17 -> if (dealerUpcard >= 10) AiAction.HIT else AiAction.STAND
            else -> AiAction.STAND
        }
    }

    override fun bet(chips: Int, random: Random): Int = (20..30).random(random).coerceAtMost(chips)
}

/**
 * CPU 驱动：界面行动节拍与自动局测试共用的唯一入口，保证"测的逻辑 = 跑的逻辑"。
 * 全部约束能兜住的兜住（下注范围、行动合法性），其余交给引擎 check。
 */
object CpuDriver {

    /** 执行当前 CPU 座位的一步行动（仅在该座位的回合状态调用；真人座位会抛错） */
    fun act(engine: BlackjackEngine, seat: Int, random: Random = Random.Default) {
        val p = engine.bySeat(seat)
        check(p.type != PlayerType.HUMAN) { "座位$seat 是真人，不走 CPU 策略" }
        val upcard = engine.dealer.hand.cards.firstOrNull()?.rank?.blackjackValue ?: 0
        when (CpuStrategy.of(p.type).decide(p.hand, upcard, p.canDouble())) {
            AiAction.HIT -> engine.hit(seat)
            AiAction.STAND -> engine.stand(seat)
            AiAction.DOUBLE -> engine.doubleDown(seat)
        }
    }

    /** 下注阶段为某 CPU 座位定注（结果保证在 1~chips 内） */
    fun betFor(engine: BlackjackEngine, seat: Int, random: Random = Random.Default): Int {
        val p = engine.bySeat(seat)
        return CpuStrategy.of(p.type).bet(p.chips, random)
    }
}
