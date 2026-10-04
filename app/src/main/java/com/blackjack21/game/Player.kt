package com.blackjack21.game

/** 参与者类型：真人 + 三种性格的 CPU（总方案第 8 节） */
enum class PlayerType { HUMAN, CPU_CONSERVATIVE, CPU_BALANCED, CPU_AGGRESSIVE }

/** 一局内座位的行动状态 */
enum class TurnStatus { WAITING, PLAYING, STANDING, BUST, BLACKJACK }

/**
 * 一名玩家（真人或 CPU）：筹码、下注、手牌与本局状态。
 * 下注与 Double 时即扣筹码，赢回的筹码在结算时加回（结算属阶段2）。
 */
class Player(
    val seat: Int,          // 1~4
    val name: String,
    val type: PlayerType,
) {
    var chips: Int = GameConfig.STARTING_CHIPS
        internal set

    /** 本局下注额（Double 后为翻倍值） */
    var bet: Int = 0
        internal set

    val hand = Hand()

    var status: TurnStatus = TurnStatus.WAITING
        internal set

    /** 本局已行动次数；Double 只允许第一次行动使用 */
    var actionsTaken: Int = 0
        internal set

    /** 筹码归零即出局（总方案第 3 节） */
    val isBankrupt: Boolean get() = chips <= 0

    fun resetForRound() {
        hand.clear()
        bet = 0
        status = TurnStatus.WAITING
        actionsTaken = 0
    }

    /** 回到初始筹码（新一场开始时） */
    fun resetChips() {
        chips = GameConfig.STARTING_CHIPS
    }

    fun validateBet(amount: Int) {
        require(amount in 1..chips) { "座位$seat 下注须在 1~$chips 之间，收到 $amount" }
    }

    /** 下注并扣筹码 */
    fun placeBet(amount: Int) {
        validateBet(amount)
        chips -= amount
        bet = amount
    }

    /** 结算返还筹码（赢注 / 平局退注 / Blackjack 3:2，规则见 [Settlement]） */
    fun receivePayout(amount: Int) {
        chips += amount
    }

    /** 收一张牌（初始发牌也走这里，不算行动次数） */
    fun takeCard(card: Card) {
        hand.add(card)
        if (status == TurnStatus.WAITING) status = TurnStatus.PLAYING
    }

    fun stand() {
        status = TurnStatus.STANDING
    }

    fun markBlackjack() {
        status = TurnStatus.BLACKJACK
    }

    fun hit(card: Card) {
        takeCard(card)
        actionsTaken++
        if (hand.isBust()) status = TurnStatus.BUST
    }

    /** Double：再扣一次当前注额 → 注翻倍 → 只补一张牌 → 强制停牌（总方案第 4 节） */
    fun doubleDown(card: Card) {
        chips -= bet
        bet *= 2
        takeCard(card)
        actionsTaken++
        status = if (hand.isBust()) TurnStatus.BUST else TurnStatus.STANDING
    }

    /** 可 Double 条件：第一次行动、手里两张牌、筹码还够再翻一倍 */
    fun canDouble(): Boolean = actionsTaken == 0 && hand.size == 2 && chips >= bet
}
