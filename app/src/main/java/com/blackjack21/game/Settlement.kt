package com.blackjack21.game

/** 单座位一局的结算结果类型 */
enum class Outcome {
    WIN,        // 普通获胜
    LOSE,       // 普通落败
    PUSH,       // 平局（含双方 Blackjack）
    BLACKJACK,  // 玩家 Blackjack 获胜（3:2）
    BUST        // 玩家爆牌落败
}

/** 单座位的本局结算结果 */
data class SeatResult(
    val seat: Int,
    val outcome: Outcome,
    /** 结算时返还到筹码的金额（下注时筹码已扣除） */
    val payout: Int,
)

/**
 * 结算规则（总方案第 5、6、15 节）。纯函数，便于穷举自动测试。
 *
 * 判定优先级：
 * 双方 Blackjack 平 → 玩家 Blackjack（3:2）→ 玩家爆 → 庄家 Blackjack → 庄家爆 → 比点数。
 *
 * 下注时筹码已从玩家扣除，settle 返回的返还额即局末加回筹码的金额：
 * WIN 返 2×注，PUSH 返 1×注，BLACKJACK 返 注 + 盈利(注×3/2)，LOSE / BUST 返 0。
 */
object Settlement {

    /** 3:2 赔付的盈利部分；奇数注向下取整（如 15 注盈利 22） */
    fun blackjackProfit(bet: Int): Int =
        bet * GameConfig.BLACKJACK_PAYOUT_NUM / GameConfig.BLACKJACK_PAYOUT_DEN

    /** 返回 (结果, 返还筹码) */
    fun settle(playerHand: Hand, bet: Int, dealerHand: Hand): Pair<Outcome, Int> {
        require(bet > 0) { "bet 必须为正" }
        val playerBJ = playerHand.isBlackjack()
        val dealerBJ = dealerHand.isBlackjack()
        return when {
            playerBJ && dealerBJ -> Outcome.PUSH to bet
            playerBJ -> Outcome.BLACKJACK to bet + blackjackProfit(bet)
            playerHand.isBust() -> Outcome.BUST to 0
            dealerBJ -> Outcome.LOSE to 0
            dealerHand.isBust() -> Outcome.WIN to bet * 2
            playerHand.bestValue() > dealerHand.bestValue() -> Outcome.WIN to bet * 2
            playerHand.bestValue() < dealerHand.bestValue() -> Outcome.LOSE to 0
            else -> Outcome.PUSH to bet
        }
    }
}
