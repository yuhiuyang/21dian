package com.blackjack21.game

/** 全局规则常量（对应总方案第 3、5、6、9 节 + 多人需求） */
object GameConfig {
    /** 每人初始筹码 */
    const val STARTING_CHIPS = 100

    /** Blackjack 赔率 3 : 2（盈利 = 下注 × 1.5） */
    const val BLACKJACK_PAYOUT_NUM = 3
    const val BLACKJACK_PAYOUT_DEN = 2

    /** 庄家要牌上限：≤16 要牌，≥17（含软 17）停牌 */
    const val DEALER_HIT_LIMIT = 16

    /** 主菜单可选总局数 */
    val ROUND_OPTIONS = listOf(5, 10)

    /** 可选本地真人玩家数：总席位固定 4 个，真人按数量依次占据座位 1..n，其余为 CPU */
    val HUMAN_COUNT_OPTIONS = listOf(1, 2, 3, 4)
}
