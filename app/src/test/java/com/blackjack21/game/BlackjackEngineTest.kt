package com.blackjack21.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 引擎流程测试：下注校验、发牌、行动、庄家规则（阶段1），
 * 结算入账、局间流转、出局与排名（阶段2）。
 * 用固定牌序注入保证结果可复现。
 */
class BlackjackEngineTest {

    private val allBets = mapOf(1 to 10, 2 to 10, 3 to 10, 4 to 10)

    /** 按轮发顺序 [座位1,2,3,4,庄] × 2 构造牌序的辅助 */
    private fun dealOrder(
        p1: Pair<Rank, Suit>, p2: Pair<Rank, Suit>, p3: Pair<Rank, Suit>, p4: Pair<Rank, Suit>,
        dealer1: Pair<Rank, Suit>,
        p1b: Pair<Rank, Suit>, p2b: Pair<Rank, Suit>, p3b: Pair<Rank, Suit>, p4b: Pair<Rank, Suit>,
        dealer2: Pair<Rank, Suit>,
        extra: List<Card> = emptyList(),
    ): List<Card> = listOf(p1, p2, p3, p4, dealer1, p1b, p2b, p3b, p4b, dealer2)
        .map { Card(it.first, it.second) } + extra

    /** 常规牌局（无人 Blackjack）：座位1=5点 座位2=9点 座位3=13点 座位4=17点 庄家=5点 */
    private fun simpleEngine() = BlackjackEngine(
        Deck(
            presetOrder = dealOrder(
                Rank.TWO to Suit.SPADES, Rank.FOUR to Suit.DIAMONDS,
                Rank.SIX to Suit.SPADES, Rank.EIGHT to Suit.DIAMONDS,
                Rank.TWO to Suit.HEARTS,
                Rank.THREE to Suit.HEARTS, Rank.FIVE to Suit.CLUBS,
                Rank.SEVEN to Suit.HEARTS, Rank.NINE to Suit.CLUBS,
                Rank.THREE to Suit.DIAMONDS,
                extra = listOf(
                    Card(Rank.TWO, Suit.CLUBS), Card(Rank.THREE, Suit.CLUBS),
                    Card(Rank.FOUR, Suit.HEARTS), Card(Rank.FIVE, Suit.DIAMONDS),
                    Card(Rank.SIX, Suit.CLUBS), Card(Rank.TWO, Suit.DIAMONDS),
                    Card(Rank.THREE, Suit.DIAMONDS), Card(Rank.FOUR, Suit.CLUBS),
                    Card(Rank.FIVE, Suit.HEARTS), Card(Rank.SIX, Suit.HEARTS),
                    Card(Rank.SEVEN, Suit.DIAMONDS), Card(Rank.EIGHT, Suit.CLUBS),
                )
            )
        )
    ).apply {
        startGame(5)
        startRound(allBets)
    }

    /** 座位1 起手 A♠+K♠ = Blackjack；庄家 5♥9♥=14（补一张 Q♥ 后爆牌） */
    private fun blackjackSeat1Engine() = BlackjackEngine(
        Deck(
            presetOrder = dealOrder(
                Rank.ACE to Suit.SPADES, Rank.TWO to Suit.DIAMONDS,
                Rank.THREE to Suit.SPADES, Rank.FOUR to Suit.DIAMONDS,
                Rank.FIVE to Suit.HEARTS,
                Rank.KING to Suit.SPADES, Rank.SIX to Suit.DIAMONDS,
                Rank.SEVEN to Suit.SPADES, Rank.EIGHT to Suit.DIAMONDS,
                Rank.NINE to Suit.HEARTS,
                extra = listOf(Card(Rank.QUEEN, Suit.HEARTS))
            )
        )
    ).apply {
        startGame(5)
        startRound(allBets)
    }

    /** 四人起手全是 12 点、补牌全是 10 值牌 → 各补一张即全部爆牌；庄家 5 点不参与补牌 */
    private fun allBustEngine(bets: Map<Int, Int> = allBets) = BlackjackEngine(
        Deck(
            presetOrder = dealOrder(
                Rank.FIVE to Suit.SPADES, Rank.SIX to Suit.HEARTS,
                Rank.EIGHT to Suit.CLUBS, Rank.NINE to Suit.DIAMONDS,
                Rank.TWO to Suit.HEARTS,
                Rank.SEVEN to Suit.SPADES, Rank.SIX to Suit.DIAMONDS,
                Rank.FOUR to Suit.CLUBS, Rank.THREE to Suit.DIAMONDS,
                Rank.THREE to Suit.HEARTS,
                extra = listOf(
                    Card(Rank.JACK, Suit.SPADES), Card(Rank.QUEEN, Suit.SPADES),
                    Card(Rank.KING, Suit.SPADES), Card(Rank.TEN, Suit.SPADES),
                )
            )
        )
    ).apply {
        startGame(5)
        startRound(bets)
    }

    /** 座位1 起手 19 点，Double 补 2♥ 到 21 点；庄家 17 点不再补牌 → 座位1 每局稳赢。只 startGame，不开局 */
    private fun doubleWinEngine() = BlackjackEngine(
        Deck(
            presetOrder = dealOrder(
                Rank.NINE to Suit.SPADES, Rank.TWO to Suit.DIAMONDS,
                Rank.THREE to Suit.SPADES, Rank.FOUR to Suit.DIAMONDS,
                Rank.EIGHT to Suit.HEARTS,
                Rank.KING to Suit.SPADES, Rank.FIVE to Suit.DIAMONDS,
                Rank.SIX to Suit.SPADES, Rank.SEVEN to Suit.DIAMONDS,
                Rank.NINE to Suit.HEARTS,
                extra = listOf(Card(Rank.TWO, Suit.HEARTS))
            )
        )
    ).apply { startGame(5) }

    /** 座位2 起手后下注 100 输光出局；庄家 20 点，其余座位全输 */
    private fun bankruptCpuEngine() = BlackjackEngine(
        Deck(
            presetOrder = dealOrder(
                Rank.ACE to Suit.SPADES, Rank.TWO to Suit.DIAMONDS,
                Rank.THREE to Suit.DIAMONDS, Rank.FOUR to Suit.DIAMONDS,
                Rank.KING to Suit.HEARTS,
                Rank.KING to Suit.SPADES, Rank.FIVE to Suit.DIAMONDS,
                Rank.SIX to Suit.DIAMONDS, Rank.SEVEN to Suit.DIAMONDS,
                Rank.QUEEN to Suit.HEARTS,
            )
        )
    ).apply {
        startGame(5)
        startRound(mapOf(1 to 10, 2 to 100, 3 to 10, 4 to 10))
    }

    // ---- 阶段1：下注与发牌 ----

    @Test
    fun `下注超过筹码被拒且状态不变`() {
        val e = BlackjackEngine().apply { startGame(5) }
        assertEquals(GameState.BETTING, e.state)
        assertThrows(IllegalArgumentException::class.java) {
            e.startRound(mapOf(1 to 101, 2 to 10, 3 to 10, 4 to 10))
        }
        assertEquals(GameState.BETTING, e.state)
    }

    @Test
    fun `下注0被拒`() {
        val e = BlackjackEngine().apply { startGame(5) }
        assertThrows(IllegalArgumentException::class.java) {
            e.startRound(mapOf(1 to 0, 2 to 10, 3 to 10, 4 to 10))
        }
    }

    @Test
    fun `未破产座位漏下注被拒`() {
        val e = BlackjackEngine().apply { startGame(5) }
        assertThrows(IllegalStateException::class.java) {
            e.startRound(mapOf(1 to 10, 2 to 10, 3 to 10))
        }
    }

    @Test
    fun `开局后每人两张庄家两张`() {
        val e = simpleEngine()
        e.players.forEach { assertEquals(2, it.hand.size) }
        assertEquals(2, e.dealer.hand.size)
        assertEquals(GameState.PLAYER_TURN, e.state)
        e.players.forEach { assertEquals(90, it.chips) } // 100 - 下注10
    }

    @Test
    fun `不是自己的座位行动被拒`() {
        val e = simpleEngine()
        assertThrows(IllegalStateException::class.java) { e.hit(2) }
        assertThrows(IllegalStateException::class.java) { e.stand(4) }
    }

    @Test
    fun `爆牌立即结束该座位回合`() {
        val e = simpleEngine()
        var guard = 0
        while (e.state == GameState.PLAYER_TURN && guard++ < 12) e.hit(1)
        assertEquals(TurnStatus.BUST, e.bySeat(1).status)
        assertEquals(GameState.CPU_PLAYER_2, e.state)
    }

    // ---- 阶段1：Blackjack 与 Double ----

    @Test
    fun `Blackjack座位跳过行动`() {
        val e = blackjackSeat1Engine()
        assertEquals(TurnStatus.BLACKJACK, e.bySeat(1).status)
        assertEquals(GameState.CPU_PLAYER_2, e.state)
        assertThrows(IllegalStateException::class.java) { e.hit(1) }
    }

    @Test
    fun `Double翻倍下注只补一张并停牌`() {
        val e = simpleEngine()
        e.doubleDown(1)
        val p = e.bySeat(1)
        assertEquals(20, p.bet)
        assertEquals(80, p.chips) // 100 - 10 - 10
        assertEquals(3, p.hand.size)
        assertEquals(TurnStatus.STANDING, p.status)
        assertEquals(GameState.CPU_PLAYER_2, e.state)
    }

    @Test
    fun `Double只能在第一次行动使用`() {
        val e = simpleEngine()
        e.hit(1) // 第一次行动改为要牌
        if (e.bySeat(1).status == TurnStatus.PLAYING) {
            assertThrows(IllegalStateException::class.java) { e.doubleDown(1) }
        }
    }

    // ---- 阶段1：庄家规则 ----

    @Test
    fun `全员爆牌庄家不补牌`() {
        val e = allBustEngine()
        e.hit(1)
        e.hit(2)
        e.hit(3)
        e.hit(4)
        assertTrue(e.players.all { it.hand.isBust() })
        assertEquals(GameState.ROUND_RESULT, e.state)
        assertEquals(2, e.dealer.hand.size) // 庄家没有补牌
    }

    @Test
    fun `全部停牌庄家按规则补到17点以上`() {
        val e = simpleEngine()
        for (seat in 1..4) {
            if (e.currentSeat() == seat) e.stand(seat)
        }
        assertEquals(GameState.ROUND_RESULT, e.state)
        assertTrue(
            "庄家终值应为 ≥17 或爆牌，实际 ${e.dealer.hand.bestValue()}",
            e.dealer.hand.bestValue() >= 17 || e.dealer.hand.isBust()
        )
    }

    // ---- 阶段2：结算入账 ----

    @Test
    fun `Blackjack按3比2赔付入账`() {
        val e = blackjackSeat1Engine()
        e.stand(2)
        e.stand(3)
        e.stand(4)
        assertEquals(GameState.ROUND_RESULT, e.state)
        assertTrue(e.dealer.hand.isBust()) // 庄家 14 补 Q♥ = 24
        val r1 = e.roundResults.first { it.seat == 1 }
        assertEquals(Outcome.BLACKJACK, r1.outcome)
        assertEquals(25, r1.payout) // 10 本金 + 15 盈利
        assertEquals(115, e.bySeat(1).chips)
        assertEquals(110, e.bySeat(2).chips) // 8 点 vs 庄家爆 → 赢 20
    }

    @Test
    fun `双方Blackjack平局退注`() {
        val e = BlackjackEngine(
            Deck(
                presetOrder = dealOrder(
                    Rank.ACE to Suit.SPADES, Rank.TWO to Suit.DIAMONDS,
                    Rank.THREE to Suit.DIAMONDS, Rank.FOUR to Suit.DIAMONDS,
                    Rank.ACE to Suit.HEARTS,
                    Rank.KING to Suit.SPADES, Rank.FIVE to Suit.DIAMONDS,
                    Rank.SIX to Suit.DIAMONDS, Rank.SEVEN to Suit.DIAMONDS,
                    Rank.QUEEN to Suit.HEARTS,
                )
            )
        ).apply {
            startGame(5)
            startRound(allBets)
        }
        e.stand(2)
        e.stand(3)
        e.stand(4)
        val r1 = e.roundResults.first { it.seat == 1 }
        assertEquals(Outcome.PUSH, r1.outcome)
        assertEquals(10, r1.payout)
        assertEquals(100, e.bySeat(1).chips) // 退注，不增不减
        assertEquals(90, e.bySeat(2).chips)  // 7 vs 21 → 输
    }

    @Test
    fun `Double后的赢牌按翻倍注赔付`() {
        val e = doubleWinEngine()
        e.startRound(allBets)
        e.doubleDown(1)
        e.stand(2)
        e.stand(3)
        e.stand(4)
        assertEquals(GameState.ROUND_RESULT, e.state)
        val r1 = e.roundResults.first { it.seat == 1 }
        assertEquals(Outcome.WIN, r1.outcome)
        assertEquals(40, r1.payout) // 翻倍注 20 → 返 40
        assertEquals(120, e.bySeat(1).chips) // 100 - 10 - 10 + 40
        assertEquals(90, e.bySeat(2).chips)  // 7 vs 17 → 输
    }

    @Test
    fun `随机牌序整局能跑到结算`() {
        val e = BlackjackEngine(Deck(random = Random(123))).apply {
            startGame(5)
            startRound(allBets)
        }
        for (seat in 1..4) {
            if (e.currentSeat() == seat) e.stand(seat)
        }
        assertEquals(GameState.ROUND_RESULT, e.state)
        assertTrue(
            "庄家终值应为 ≥17 或爆牌，实际 ${e.dealer.hand.bestValue()}",
            e.dealer.hand.bestValue() >= 17 || e.dealer.hand.isBust()
        )
        assertEquals(4, e.roundResults.size)
    }

    // ---- 阶段2：局间流转、出局与排名 ----

    @Test
    fun `结算后进入下一局下注`() {
        val e = doubleWinEngine()
        e.startRound(allBets)
        e.doubleDown(1)
        e.stand(2)
        e.stand(3)
        e.stand(4)
        e.nextRound()
        assertEquals(GameState.BETTING, e.state)
        assertEquals(1, e.round)
        assertEquals(0, e.roundResults.size) // 结果已清空
    }

    @Test
    fun `整场5局打完进入GAME_OVER并可排名`() {
        val e = doubleWinEngine()
        repeat(5) {
            e.startRound(e.players.filter { !it.isBankrupt }.associate { it.seat to 10 })
            e.doubleDown(1) // 19 → 21 点，赢庄家 17
            e.stand(2)
            e.stand(3)
            e.stand(4)
            assertEquals(GameState.ROUND_RESULT, e.state)
            e.nextRound()
        }
        assertEquals(GameState.GAME_OVER, e.state)
        assertEquals(5, e.round)
        assertEquals(200, e.bySeat(1).chips) // 每局净赢 20
        assertEquals(50, e.bySeat(2).chips)  // 每局净输 10
        assertEquals(listOf(1, 2, 3, 4), e.finalRanking().map { it.seat })
    }

    @Test
    fun `破产的CPU下一局不能下注且不参与`() {
        val e = bankruptCpuEngine()
        e.stand(2)
        e.stand(3)
        e.stand(4)
        assertEquals(0, e.bySeat(2).chips) // 下注 100 全输
        e.nextRound()
        assertEquals(GameState.BETTING, e.state)
        assertThrows(IllegalStateException::class.java) {
            e.startRound(mapOf(1 to 10, 2 to 10, 3 to 10, 4 to 10)) // 座位2已出局
        }
        e.startRound(mapOf(1 to 10, 3 to 10, 4 to 10))
        assertEquals(2, e.round)
        assertEquals(0, e.bySeat(2).hand.size) // 没有参与发牌
        assertEquals(TurnStatus.WAITING, e.bySeat(2).status)
        // 座位1 又拿 Blackjack 跳过，座位2 未参与，轮到座位3
        assertEquals(GameState.CPU_PLAYER_3, e.state)
    }

    @Test
    fun `真人破产提前结束整场并可排名`() {
        val e = allBustEngine(mapOf(1 to 100, 2 to 10, 3 to 10, 4 to 10))
        e.hit(1)
        e.hit(2)
        e.hit(3)
        e.hit(4)
        assertEquals(0, e.bySeat(1).chips) // 100 全下注且爆牌
        e.nextRound()
        assertEquals(GameState.GAME_OVER, e.state)
        assertEquals(listOf(2, 3, 4, 1), e.finalRanking().map { it.seat })
    }
}
