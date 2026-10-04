package com.blackjack21.game

/**
 * 21点游戏引擎：驱动状态机与规则。纯 Kotlin、无 Android 依赖，便于单元测试。
 *
 * 阶段1：下注校验、发牌、玩家行动（HIT / STAND / DOUBLE）、庄家固定规则行动。
 * 阶段2：结算与筹码变动、局间流转（ROUND_RESULT → BETTING / GAME_OVER）、最终排名。
 * 阶段4：CPU 按策略自动行动。
 * 多人需求：总席位固定 4 个，真人玩家 1~4 名按数量依次占据座位 1..n，其余座位为 CPU；
 * 真人全部破产或打满总局数才整场结束。
 */
class BlackjackEngine(val deck: Deck = Deck()) {

    val dealer = Dealer()

    /** 座位 1..humanCount = 真人（玩家1..N），其余为 CPU；startGame 时按人数重建 */
    var players: List<Player> = buildPlayers(1)
        private set

    var state: GameState = GameState.MAIN_MENU
        private set

    /** 当前局数，从 1 开始 */
    var round: Int = 0
        private set

    /** 一场总局数（5 或 10） */
    var totalRounds: Int = 0
        private set

    /** 本局结算结果；进入 ROUND_RESULT 后可读，进入下一局时清空 */
    var roundResults: List<SeatResult> = emptyList()
        private set

    /**
     * 开新一场（MAIN_MENU → BETTING），座位按真人数重建、筹码全部重置。
     * CPU 补位顺序固定为 保守 → 平衡 → 激进（保持阶段4 策略不变）。
     */
    fun startGame(rounds: Int, humanCount: Int = 1) {
        require(rounds in GameConfig.ROUND_OPTIONS) { "总局数只能是 5 或 10" }
        require(humanCount in GameConfig.HUMAN_COUNT_OPTIONS) { "真人玩家数只能是 1~4" }
        totalRounds = rounds
        round = 0
        players = buildPlayers(humanCount)
        state = GameState.BETTING
    }

    private fun buildPlayers(humanCount: Int): List<Player> = (1..4).map { seat ->
        when {
            seat <= humanCount -> Player(seat, "玩家$seat", PlayerType.HUMAN)
            else -> when (seat - humanCount) {
                1 -> Player(seat, "CPU·保守", PlayerType.CPU_CONSERVATIVE)
                2 -> Player(seat, "CPU·平衡", PlayerType.CPU_BALANCED)
                else -> Player(seat, "CPU·激进", PlayerType.CPU_AGGRESSIVE)
            }
        }
    }

    /**
     * 开新一局：校验全部下注 → 重洗一副牌 → 收注 → 只给下注的座位发牌。
     * 未破产座位必须下注；已破产（出局）座位不能下注、不参与本局。
     */
    fun startRound(bets: Map<Int, Int>) {
        check(state == GameState.BETTING) { "当前状态 $state，不能开局" }
        players.forEach { p ->
            val bet = bets[p.seat]
            if (p.isBankrupt) {
                check(bet == null) { "座位${p.seat} 已出局，不能下注" }
            } else {
                checkNotNull(bet) { "座位${p.seat} 未下注" }
                p.validateBet(bet)
            }
        }
        round++
        deck.reset()
        dealer.resetForRound()
        players.forEach { it.resetForRound() }
        players.forEach { p -> bets[p.seat]?.let { p.placeBet(it) } }
        state = GameState.INITIAL_DEAL
        dealInitialCards()
    }

    // ---- 玩家行动（座位通用，真人与 CPU 走同一入口） ----

    /** HIT：要一张牌；爆牌则本座位回合立即结束 */
    fun hit(seat: Int) {
        beginAction(seat)
        bySeat(seat).hit(deck.draw())
        if (bySeat(seat).status == TurnStatus.BUST) nextTurnState(seat)
    }

    /** STAND：停牌，本座位回合结束 */
    fun stand(seat: Int) {
        beginAction(seat)
        bySeat(seat).stand()
        nextTurnState(seat)
    }

    /** DOUBLE：首次行动时双倍下注，只补一张牌 */
    fun doubleDown(seat: Int) {
        beginAction(seat)
        val p = bySeat(seat)
        check(p.canDouble()) { "座位$seat 不能 Double：必须第一次行动且筹码足够" }
        p.doubleDown(deck.draw())
        nextTurnState(seat)
    }

    /** 当前该行动的座位（1~4）；不在任何玩家回合时返回 -1 */
    fun currentSeat(): Int = when (state) {
        GameState.PLAYER_TURN -> 1
        GameState.CPU_PLAYER_2 -> 2
        GameState.CPU_PLAYER_3 -> 3
        GameState.CPU_PLAYER_4 -> 4
        else -> -1
    }

    /** 当前行动座位是否真人（界面据此等待输入或驱动 CPU；状态名 CPU_PLAYER_N 仅表示"座位N行动中"） */
    fun isHumanTurn(): Boolean {
        val seat = currentSeat()
        return seat > 0 && bySeat(seat).type == PlayerType.HUMAN
    }

    fun bySeat(seat: Int): Player = players.first { it.seat == seat }

    // ---- 结算与局间流转（阶段2） ----

    /** 展示完本局结果后调用：回到下注阶段，或整场结束（真人全部破产即提前收场） */
    fun nextRound() {
        check(state == GameState.ROUND_RESULT) { "当前状态 $state，不能进入下一局" }
        roundResults = emptyList()
        val humansAllBankrupt = players.filter { it.type == PlayerType.HUMAN }.all { it.isBankrupt }
        state = if (round >= totalRounds || humansAllBankrupt) {
            GameState.GAME_OVER
        } else {
            GameState.BETTING
        }
    }

    /** 整场结束后的最终排名：筹码多者在前，同筹码按座位号 */
    fun finalRanking(): List<Player> {
        check(state == GameState.GAME_OVER) { "整场尚未结束" }
        return players.sortedWith(compareByDescending<Player> { it.chips }.thenBy { it.seat })
    }

    // ---- 内部流转 ----

    private fun beginAction(seat: Int) {
        check(currentSeat() == seat) { "当前轮到座位 ${currentSeat()}，不是 $seat" }
    }

    /** 本局参与者 = 下了注的座位 */
    private fun participants(): List<Player> = players.filter { it.bet > 0 }

    private fun dealInitialCards() {
        repeat(2) {
            participants().forEach { it.takeCard(deck.draw()) }
            dealer.hand.add(deck.draw())
        }
        participants().forEach { p -> if (p.hand.isBlackjack()) p.markBlackjack() }
        nextTurnState(fromSeat = 0)
    }

    /** 一个座位结束后轮到下一个；跳过已终结或未参与的座位；全部结束进入庄家回合 */
    private fun nextTurnState(fromSeat: Int) {
        var seat = fromSeat + 1
        while (seat <= players.size && bySeat(seat).status.isTurnOver()) seat++
        state = if (seat <= players.size) {
            when (seat) {
                1 -> GameState.PLAYER_TURN
                2 -> GameState.CPU_PLAYER_2
                3 -> GameState.CPU_PLAYER_3
                else -> GameState.CPU_PLAYER_4
            }
        } else {
            GameState.DEALER_TURN
        }
        if (state == GameState.DEALER_TURN) dealerTurn()
    }

    /** PLAYING 之外的任何状态都不再行动（WAITING = 本局未参与） */
    private fun TurnStatus.isTurnOver(): Boolean = this != TurnStatus.PLAYING

    /** 庄家固定规则：≤16 要牌，≥17（含软 17）停；参与玩家全爆时庄家不补牌 */
    private fun dealerTurn() {
        if (participants().any { !it.hand.isBust() }) {
            while (dealer.shouldHit()) dealer.hand.add(deck.draw())
        }
        state = GameState.SETTLEMENT
        settleRound()
    }

    /** 结算：按 3:2 与比牌规则给各座位返还筹码（总方案第 15 节） */
    private fun settleRound() {
        roundResults = participants().map { p ->
            val (outcome, payout) = Settlement.settle(p.hand, p.bet, dealer.hand)
            p.receivePayout(payout)
            SeatResult(p.seat, outcome, payout)
        }
        state = GameState.ROUND_RESULT
    }
}
