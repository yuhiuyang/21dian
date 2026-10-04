package com.blackjack21.ui

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import com.blackjack21.ai.CpuDriver
import com.blackjack21.game.BlackjackEngine
import com.blackjack21.game.Card
import com.blackjack21.game.GameState
import com.blackjack21.game.Outcome
import com.blackjack21.game.Player
import com.blackjack21.game.PlayerType
import com.blackjack21.game.SeatResult
import com.blackjack21.game.TurnStatus
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 阶段5美术 + 阶段6动画 + 阶段7音效 + 多人本地对战界面：蓝色弧形牌桌 + 麦穗座位环 + 卡通头像，
 * Canvas 全代码绘制，触屏操作。
 *
 * - 布局画在 1920×1080 逻辑画布上等比缩放居中（全屏适配方案第 5、6 节），元素坐标不写死屏幕像素；
 *   背景外扩铺满整个 Surface（第 7 节），顶部按钮 ≥40dp 安全边距（第 8 节）
 * - 多人对战（用户需求）：主菜单选 1~4 名真人（总席位固定 4，CPU 按性格补位），
 *   真人轮流下注/行动，当前玩家座位金环+金字高亮，各家独立筹码与结算
 * - 动画走 AnimPlayer 帧循环，播完即停帧：无动画时零重绘（总方案第 22 节）
 * - 动画清单（总方案第 19 节）：发牌飞行（发牌靴→座位）、要牌/加倍补牌飞行、庄家暗牌翻转、
 *   下注筹码飞入与结算飞行（赢 庄家→玩家 / 输 玩家→庄家）、BUST 震动红字、BLACKJACK 金字、结算面板渐入
 * - 音效（音效优化方案）：11 种桌游风音效 SfxSynth 纯代码合成（模态敲击/扫频纸牌声，母带统一响度+降高频），
 *   开始回合 chime、爆牌 boom、输赢分开反馈；设置弹窗可开关音效/震动、拖动音量滑杆
 *   （SharedPreferences 记忆，音量默认 70%），无背景音乐；重开/返回主菜单有二次确认
 * - CPU 行动与下注走 ai/ 包三种性格（保守/平衡/激进），与自动局测试共用 CpuDriver
 */
class BlackjackGameView(context: Context) : View(context) {

    private var engine = BlackjackEngine()
    private val table = TableRenderer()
    private val cardRenderer = CardRenderer()
    private val chipRenderer = ChipRenderer()
    private val buttonRenderer = ButtonRenderer()
    private val avatarRenderer = AvatarRenderer()
    private val sfx = SfxPlayer(context)

    /** 本帧各按钮命中区域，onDraw 重建、触控查表 */
    private val buttonRects = HashMap<String, RectF>()

    /** 下注阶段：轮流给每个未破产真人定注；真人全部确认后才开局 */
    private val pendingBets = HashMap<Int, Int>()
    private var bettingSeat = 1

    /** 主菜单选择状态（重开/返回后保留上次选择） */
    private var menuHumans = 1
    private var menuRounds = 5

    /** 顶部弹窗（打开时游戏画面冻结、CPU 暂停，关闭后原局继续） */
    private var overlay: Overlay? = null

    private enum class Overlay { RULES, SETTINGS, CONFIRM }

    /** 二次确认弹窗的目标动作（重新开始 / 返回主菜单） */
    private var confirmMessage = ""
    private var confirmAction: (() -> Unit)? = null

    /** 设置弹窗音量滑杆：视觉轨道（拖动映射 0~100%），触控命中区比轨道大一圈 */
    private var volumeTrack: RectF? = null
    private var volumeDrag = false

    // ---- 阶段6 动画状态 ----

    private val anims = AnimPlayer(this)

    /** 飞行中的牌：起点（发牌靴口）与落点（座位布局位）每帧现算，只记所有者与牌序 */
    private class CardFlight(val owner: Int, val index: Int, val faceUpCard: Boolean, var t: Float = 0f)

    private val cardFlights = mutableListOf<CardFlight>()

    private enum class ChipMode { DEAL, TO_DEALER, FROM_DEALER }

    private class ChipFly(val seat: Int, val amount: Int, val mode: ChipMode, var t: Float = 0f)

    private val chipFlies = mutableListOf<ChipFly>()

    /** 闪字特效（爆牌红 / BLACKJACK 金） */
    private class PopFx(val text: String, val gold: Boolean, var t: Float = 0f)

    private val pops = HashMap<Int, PopFx>() // key: 0=庄家，1~4=座位
    private val bustFxPlayed = mutableSetOf<Int>()

    /** 爆牌震动进度 0~1 */
    private val shakes = HashMap<Int, Float>()

    /** owner(0=庄家,1~4) 已落定的牌数 / 其中明牌数 */
    private val landed = IntArray(5)
    private val faceUp = IntArray(5)

    /** 庄家暗牌翻转进度；<0 表示未在翻 */
    private var holeFlipT = -1f

    /** 结算 / 排名面板渐入进度 */
    private var panelT = 1f

    /** 结算后被收走 / 收回的座位：本局不再画静态下注筹码 */
    private val betGone = mutableSetOf<Int>()

    /** 下注筹码飞行中：暂时压住静态筹码 */
    private val chipInFly = mutableSetOf<Int>()

    /** 引擎实例追踪：重开一局（新引擎）时清空全部动画状态 */
    private var trackedEngine = engine

    // ---- 阶段8 分辨率适配：刘海 / 圆角 / 导航栏的安全边距（px），画布在安全区内居中 ----

    private var padL = 0
    private var padT = 0
    private var padR = 0
    private var padB = 0

    // ---- 全屏适配（全屏显示与屏幕适配优化方案第 5~9 节）----
    // 所有元素画在 1920×1080 逻辑画布上：scale = min(可用宽/1920, 可用高/1080) 等比例缩放并在
    // 安全区内居中——任何手机比例下布局一致、不拉伸、按钮不乱跑；触控坐标做逆变换。

    /** 逻辑画布→屏幕的等比缩放与安全区内偏移（px）；onDraw 每帧更新，触控逆变换用 */
    private var viewScale = 1f
    private var viewOx = 0f
    private var viewOy = 0f

    /** 整个物理 Surface 在逻辑坐标系里的外扩矩形：背景与弹窗遮罩画到这里，不留未绘制黑边 */
    private val surfRect = RectF(0f, 0f, DESIGN_W, DESIGN_H)

    /** 发牌靴随顶部按钮安全边距下移的量（逻辑单位）；发牌动画起点 shoeMouth 同步 */
    private var shoeShift = 0f

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        var l = 0
        var t = 0
        var r = 0
        var b = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            insets.displayCutout?.let { c ->
                l = c.safeInsetLeft
                t = c.safeInsetTop
                r = c.safeInsetRight
                b = c.safeInsetBottom
            }
        }
        @Suppress("DEPRECATION")
        val s = listOf(
            l to insets.systemWindowInsetLeft,
            t to insets.systemWindowInsetTop,
            r to insets.systemWindowInsetRight,
            b to insets.systemWindowInsetBottom,
        )
        padL = maxOf(s[0].first, s[0].second)
        padT = maxOf(s[1].first, s[1].second)
        padR = maxOf(s[2].first, s[2].second)
        padB = maxOf(s[3].first, s[3].second)
        invalidate()
        return insets
    }

    // ---- CPU 自动行动 ----

    private val stepRunnable = Runnable { autoStep() }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        requestApplyInsets()
        scheduleStep()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(stepRunnable)
        sfx.release()
        super.onDetachedFromWindow()
    }

    /**
     * 阶段9"无后台运行"：退到后台立即停掉 CPU 节拍（引擎不偷偷推进），回前台续上。
     * onWindowVisibilityChanged 在部分 ROM（如 MuMu）会滞后 1~2 秒，所以
     * MainActivity 的 onStart/onStop 会直接调 [setGamePaused]，这里只做兜底。
     * 后台期间过期的动画会在回前台第一帧一次性结算到终态（ensureRunning 负责复活帧循环）。
     */
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        setGamePaused(visibility != VISIBLE)
    }

    /** 后台暂停 / 前台恢复：节拍停走、动画帧循环自愈 */
    fun setGamePaused(paused: Boolean) {
        if (paused) {
            removeCallbacks(stepRunnable)
        } else {
            scheduleStep()
            anims.ensureRunning()
        }
    }

    private fun scheduleStep() {
        removeCallbacks(stepRunnable)
        val seat = engine.currentSeat()
        if (overlay == null && seat >= 2 && engine.bySeat(seat).type != PlayerType.HUMAN) {
            postDelayed(stepRunnable, STEP_MS)
        }
    }

    /** 每个 CPU 回合节拍执行一次决策；真人回合、发牌 / 补牌飞行没落定前不出手 */
    private fun autoStep() {
        if (overlay != null) return
        val seat = engine.currentSeat()
        if (seat >= 2 && engine.bySeat(seat).type != PlayerType.HUMAN) {
            if (turnAnimationsBusy()) {
                scheduleStep()
                return
            }
            engineAct { CpuDriver.act(engine, seat) }
        }
        scheduleStep()
    }

    // ---- 引擎变更钩子：所有改引擎的地方都走这里，动画时序集中派发 ----

    private inline fun engineAct(action: () -> Unit) {
        val prevRound = engine.round
        val prevState = engine.state
        action()
        if (engine !== trackedEngine) {
            resetVisuals() // 新引擎（重开一场）：状态归零即可，不派发任何动画
            return
        }
        reconcile(prevRound, prevState)
    }

    private fun reconcile(prevRound: Int, prevState: GameState) {
        // 进入下注阶段（开局或下一局）：重置轮询，从第一个未破产真人开始
        if (engine.state == GameState.BETTING && prevState != GameState.BETTING) {
            pendingBets.clear()
            bettingSeat = engine.players
                .first { it.type == PlayerType.HUMAN && !it.isBankrupt }
                .seat
        }
        if (engine.round != prevRound) {
            playDealSequence()
            return
        }
        // 局中补牌（要牌 / 加倍）：谁的牌还没飞就补一条飞行；爆牌则加闪效
        for (seat in 1..4) {
            val p = engine.bySeat(seat)
            if (p.bet > 0 && faceUp[seat] > 0) {
                backfillFlights(seat)
                if (p.hand.isBust() && p.status == TurnStatus.BUST && seat !in bustFxPlayed) {
                    bustFxPlayed += seat
                    playBust(seat, delayMs = CARD_FLIGHT_MS + 80)
                }
            }
        }
        if (engine.state == GameState.ROUND_RESULT && prevState != GameState.ROUND_RESULT) playRevealSequence()
        if (engine.state == GameState.GAME_OVER && prevState != GameState.GAME_OVER) {
            // 整场结束：真人拿到第 1 名放最终胜利音，否则轻缓结束音
            cue(200L) {
                if (engine.finalRanking().firstOrNull()?.seat == 1) sfx.gameWin() else sfx.gameOver()
            }
            playPanelIn(120)
        }
    }

    // ---- 触控 ----

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = (event.x - viewOx) / viewScale // 触控坐标逆变换回逻辑画布坐标系
        val y = (event.y - viewOy) / viewScale
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> if (overlay == Overlay.SETTINGS && volumeHitContains(x, y)) {
                volumeDrag = true
                applyVolumeDrag(x)
                return true
            }
            MotionEvent.ACTION_MOVE -> if (volumeDrag) {
                applyVolumeDrag(x)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (volumeDrag) { // 拖音量的手势到此收尾，不再当按钮点击或点外关闭
                    applyVolumeDrag(x)
                    volumeDrag = false
                    return true
                }
                val hit = buttonRects.entries.firstOrNull { it.value.contains(x, y) }
                if (hit != null) {
                    onButton(hit.key)
                } else if (overlay != null) {
                    overlay = null // 点弹窗外部直接关闭
                    invalidate()
                    scheduleStep()
                }
            }
        }
        return true
    }

    /** 音量滑杆命中判定（触控区比视觉轨道四周各大一圈，好上手） */
    private fun volumeHitContains(x: Float, y: Float): Boolean {
        val t = volumeTrack ?: return false
        val pad = DESIGN_H * 0.045f
        return x >= t.left - pad && x <= t.right + pad && y >= t.top - pad && y <= t.bottom + pad
    }

    private fun applyVolumeDrag(x: Float) {
        val t = volumeTrack ?: return
        val pct = ((x - t.left) / t.width() * 100.0).roundToInt().coerceIn(0, 100)
        if (pct != sfx.volumePercent) {
            sfx.volumePercent = pct
            invalidate()
        }
    }

    private fun onButton(id: String) {
        sfx.button()
        if (overlay != null) {
            when (id) {
                "close" -> overlay = null
                "restart" -> openConfirm("当前游戏进度将丢失，确定重新开始吗？") {
                    engineAct { engine.startGame(menuRounds, menuHumans) }
                }
                "backMenu" -> openConfirm("返回主菜单将丢失当前进度，确定吗？") {
                    engineAct { engine = BlackjackEngine() }
                }
                "exit" -> (context as? Activity)?.finish()
                "toggleSound" -> {
                    sfx.soundOn = !sfx.soundOn
                    if (sfx.soundOn) sfx.button() // 打开时来一声确认
                }
                "toggleVibe" -> {
                    sfx.vibrationOn = !sfx.vibrationOn
                    if (sfx.vibrationOn) sfx.vibrateWin() // 打开时来一下短震确认
                }
                "confirmYes" -> {
                    val action = confirmAction
                    overlay = null
                    confirmAction = null
                    action?.invoke()
                }
                "confirmNo" -> { overlay = null; confirmAction = null }
            }
        } else {
            when (id) {
                "manual" -> overlay = Overlay.RULES
                "settings" -> overlay = Overlay.SETTINGS
            }
            when (engine.state) {
                GameState.MAIN_MENU -> when (id) {
                    "h1" -> menuHumans = 1
                    "h2" -> menuHumans = 2
                    "h3" -> menuHumans = 3
                    "h4" -> menuHumans = 4
                    "r5" -> menuRounds = 5
                    "r10" -> menuRounds = 10
                    "start" -> engineAct { engine.startGame(menuRounds, menuHumans) }
                }
                GameState.BETTING -> when (id) {
                    "bet5" -> addBet(5)
                    "bet10" -> addBet(10)
                    "bet20" -> addBet(20)
                    "bet50" -> addBet(50)
                    "allin" -> pendingBets[bettingSeat] = engine.bySeat(bettingSeat).chips
                    "clear" -> pendingBets.remove(bettingSeat)
                    "deal" -> confirmCurrentBet()
                }
                GameState.PLAYER_TURN, GameState.CPU_PLAYER_2, GameState.CPU_PLAYER_3, GameState.CPU_PLAYER_4 -> when (id) {
                    "hit" -> humanAct { engine.hit(it) }
                    "stand" -> humanAct { engine.stand(it) }
                    "double" -> {
                        val seat = engine.currentSeat()
                        if (engine.bySeat(seat).canDouble()) humanAct { engine.doubleDown(it) }
                    }
                }
                GameState.ROUND_RESULT -> if (id == "next") engineAct { engine.nextRound() }
                GameState.GAME_OVER -> if (id == "again") engineAct { engine = BlackjackEngine() }
                else -> {}
            }
        }
        invalidate()
        scheduleStep()
    }

    /** 轮到真人时以其座位执行操作（状态名 CPU_PLAYER_N 只表示"座位N行动中"） */
    private inline fun humanAct(action: (Int) -> Unit) {
        val seat = engine.currentSeat()
        if (seat > 0 && engine.bySeat(seat).type == PlayerType.HUMAN) engineAct { action(seat) }
    }

    /** 弹出二次确认；确定后执行 action */
    private fun openConfirm(message: String, action: () -> Unit) {
        confirmMessage = message
        confirmAction = action
        overlay = Overlay.CONFIRM
    }

    private fun addBet(amount: Int) {
        val p = engine.bySeat(bettingSeat)
        val cur = pendingBets[bettingSeat] ?: 0
        if (cur + amount <= p.chips) pendingBets[bettingSeat] = cur + amount
    }

    /** 确认当前真人下注：还有未下注真人则轮到下一位，否则连同 CPU 注一起开局 */
    private fun confirmCurrentBet() {
        val bet = pendingBets[bettingSeat] ?: 0
        if (bet !in 1..engine.bySeat(bettingSeat).chips) return
        val next = engine.players.firstOrNull {
            it.type == PlayerType.HUMAN && !it.isBankrupt && it.seat > bettingSeat
        }
        if (next != null) {
            bettingSeat = next.seat
            return
        }
        val bets = pendingBets.toMutableMap()
        engine.players
            .filter { it.type != PlayerType.HUMAN && !it.isBankrupt }
            .forEach { bets[it.seat] = CpuDriver.betFor(engine, it.seat) }
        pendingBets.clear()
        engineAct { engine.startRound(bets) }
    }

    // ---- 动画时序（阶段6） ----

    /** 换引擎（重开一场）：动画状态全部归零 */
    private fun resetVisuals() {
        trackedEngine = engine
        anims.cancelAll()
        cardFlights.clear()
        chipFlies.clear()
        pops.clear()
        shakes.clear()
        bustFxPlayed.clear()
        landed.fill(0)
        faceUp.fill(0)
        betGone.clear()
        chipInFly.clear()
        pendingBets.clear()
        bettingSeat = 1
        holeFlipT = -1f
        panelT = 1f
    }

    /** 牌 / 下注筹码还在飞时，真人按钮与 CPU 节拍都等待 */
    private fun turnAnimationsBusy(): Boolean = cardFlights.isNotEmpty() || chipInFly.isNotEmpty()

    /** 定时器：delay 毫秒后触发一次回调（音效与动画时刻对齐用） */
    private fun cue(delayMs: Long, block: () -> Unit) {
        anims.play(delayMs, 1, {}, block)
    }

    private fun playCardFlight(owner: Int, index: Int, faceUpCard: Boolean, delayMs: Long) {
        val f = CardFlight(owner, index, faceUpCard)
        cardFlights += f
        cue(delayMs) { sfx.deal() } // 牌从靴口起飞时出声
        anims.play(delayMs, CARD_FLIGHT_MS, { t -> f.t = Ease.outQuad(t) }) {
            cardFlights.remove(f)
            landed[owner]++
            if (faceUpCard) faceUp[owner]++
        }
    }

    /** 座位手牌里逻辑上已有、画面上还没落的牌逐张补飞行 */
    private fun backfillFlights(seat: Int) {
        val p = engine.bySeat(seat)
        while (faceUp[seat] + cardFlights.count { it.owner == seat } < p.hand.size) {
            playCardFlight(seat, faceUp[seat] + cardFlights.count { it.owner == seat }, true, 0)
        }
    }

    /** 开局：开始回合 chime → 下注筹码飞入 → 按引擎发牌顺序逐张飞 → Blackjack 亮字 */
    private fun playDealSequence() {
        resetVisuals()
        cue(30L) { sfx.roundStart() } // 每局开始轻声提示
        val participants = engine.players.filter { it.bet > 0 }
        participants.forEachIndexed { i, p ->
            val f = ChipFly(p.seat, p.bet, ChipMode.DEAL)
            chipFlies += f
            chipInFly += p.seat
            cue(110L + i * 70L) { sfx.chipPlace() }
            anims.play(110L + i * 70L, CHIP_FLIGHT_MS, { t -> f.t = Ease.outQuad(t) }) {
                chipFlies.remove(f)
                chipInFly.remove(p.seat)
            }
        }
        var cursor = 220L
        for (r in 0..1) {
            for (p in participants) {
                playCardFlight(p.seat, r, faceUpCard = true, delayMs = cursor)
                cursor += CARD_DEAL_STAGGER
            }
            playCardFlight(0, r, faceUpCard = r == 0, delayMs = cursor) // 庄家第二张是暗牌
            cursor += CARD_DEAL_STAGGER
        }
        participants.filter { it.status == TurnStatus.BLACKJACK }.forEach { p ->
            if (p.seat == 1) cue(cursor + 160) { sfx.blackjack(); sfx.vibrateBlackjack() } // 真人 BJ 出金字音响
            playPop(p.seat, "BLACKJACK!", gold = true, durationMs = 950, delayMs = cursor + 160)
        }
    }

    /** 进结算：庄家暗牌翻转 → 补牌逐张飞入 → 庄家爆牌 / 筹码收付 → 面板渐入 */
    private fun playRevealSequence() {
        var cursor = 140L
        cue(cursor) { sfx.flip() } // 翻牌起手出声
        anims.play(cursor, HOLE_FLIP_MS, { t -> holeFlipT = t }) {
            holeFlipT = -1f
            faceUp[0] = 2
        }
        cursor += HOLE_FLIP_MS + 60
        for (i in 2 until engine.dealer.hand.size) {
            playCardFlight(0, i, faceUpCard = true, delayMs = cursor)
            cursor += CARD_DEAL_STAGGER
        }
        if (engine.dealer.hand.isBlackjack()) {
            playPop(0, "BLACKJACK!", gold = true, durationMs = 950, delayMs = cursor)
        } else if (engine.dealer.hand.isBust()) {
            playBust(0, delayMs = cursor + 60)
        }
        engine.roundResults.forEachIndexed { i, r ->
            when (r.outcome) {
                Outcome.WIN, Outcome.BLACKJACK -> playSettleChip(r.seat, ChipMode.FROM_DEALER, cursor + i * 60L)
                Outcome.LOSE, Outcome.BUST -> playSettleChip(r.seat, ChipMode.TO_DEALER, cursor + i * 60L)
                Outcome.PUSH -> {}
            }
        }
        // 结算面板出现：震动跟真人结果走；赢家的筹码收集声已随筹码飞行播过，
        // 输 / 平再补一声轻提示（爆牌 boom 在爆的瞬间已放过）
        val humanOutcome = engine.roundResults.firstOrNull { it.seat == 1 }?.outcome
        cue(cursor + 340L) {
            when (humanOutcome) {
                Outcome.WIN, Outcome.BLACKJACK -> sfx.vibrateWin()
                Outcome.LOSE, Outcome.BUST -> { sfx.lose(); sfx.vibrateLose() }
                Outcome.PUSH -> sfx.chipPlace()
                null -> {}
            }
        }
        playPanelIn(cursor + 340L)
    }

    private fun playSettleChip(seat: Int, mode: ChipMode, delayMs: Long) {
        if (engine.bySeat(seat).bet <= 0) return
        betGone += seat
        val f = ChipFly(seat, engine.bySeat(seat).bet, mode)
        chipFlies += f
        cue(delayMs) { if (mode == ChipMode.FROM_DEALER) sfx.chipWin() else sfx.chipPlace() }
        anims.play(delayMs, CHIP_FLIGHT_MS + 120, { t -> f.t = Ease.outQuad(t) }) {
            chipFlies.remove(f)
        }
    }

    private fun playBust(owner: Int, delayMs: Long) {
        cue(delayMs) { sfx.bust() } // 爆的一瞬间低沉 boom（方案 3.5，不用警报声）
        playPop(owner, "爆牌!", gold = false, durationMs = 680, delayMs = delayMs, shake = true)
    }

    private fun playPop(owner: Int, text: String, gold: Boolean, durationMs: Long, delayMs: Long, shake: Boolean = false) {
        val fx = PopFx(text, gold)
        pops[owner] = fx
        if (shake) shakes[owner] = 0f
        anims.play(delayMs, durationMs, { t ->
            fx.t = t
            if (shake) shakes[owner] = t
        }) {
            pops.remove(owner)
            shakes.remove(owner)
        }
    }

    private fun playPanelIn(delayMs: Long) {
        panelT = 0f
        anims.play(delayMs, 240, { t -> panelT = Ease.outCubic(t) })
    }

    // ---- 布局与绘制 ----

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val availW = (width - padL - padR).toFloat() // 安全区内可用（刘海屏 / 圆角屏 / 导航栏，阶段8）
        val availH = (height - padT - padB).toFloat()
        // 逻辑画布等比例缩放（scale = min）并在安全区内居中（全屏适配方案第 5 节）：
        // 所有元素固定画在 1920×1080，任何手机比例下布局一致、不拉伸、按钮不乱跑
        val s = minOf(availW / DESIGN_W, availH / DESIGN_H)
        val ox = padL + (availW - DESIGN_W * s) / 2f
        val oy = padT + (availH - DESIGN_H * s) / 2f
        viewScale = s
        viewOx = ox
        viewOy = oy
        // 背景外扩矩形：盖住整个物理 Surface（含安全区外的刘海/导航条带），不允许未绘制黑边（第 7 节）
        surfRect.set(-ox / s, -oy / s, (width - ox) / s, (height - oy) / s)
        // 顶部按钮 ≥40dp 物理安全边距（第 8 节）：40dp 换算成逻辑单位；发牌靴同步下移让位
        val margin = maxOf(DESIGN_W * 0.02f, 40f * resources.displayMetrics.density / s)
        val btnH = DESIGN_H * 0.058f
        val shoeTop = DESIGN_H * 0.138f - DESIGN_H * 0.049f
        shoeShift = maxOf(0f, margin + btnH + DESIGN_H * 0.015f - shoeTop)

        canvas.save()
        canvas.translate(ox, oy)
        canvas.scale(s, s)
        val w = DESIGN_W
        val h = DESIGN_H
        buttonRects.clear()
        table.drawBackground(canvas, surfRect.left, surfRect.top, surfRect.right, surfRect.bottom)
        table.drawPrintings(canvas, w, h, menu = engine.state == GameState.MAIN_MENU, shoeShift = shoeShift)
        when (engine.state) {
            GameState.MAIN_MENU -> drawMainMenu(canvas, w, h)
            GameState.BETTING -> {
                drawTableSeats(canvas, w, h)
                drawBettingControls(canvas, w, h)
            }
            GameState.GAME_OVER -> {
                drawTableSeats(canvas, w, h)
                drawGameOver(canvas, w, h)
            }
            GameState.ROUND_RESULT -> {
                drawTableSeats(canvas, w, h)
                drawRoundResult(canvas, w, h)
            }
            else -> {
                drawTableSeats(canvas, w, h)
                drawInRoundStatus(canvas, w, h)
                drawActionButtons(canvas, w, h)
            }
        }
        // 顶部功能入口（弹窗打开时不绘制，由遮罩与弹窗接管整屏）
        if (overlay == null) {
            drawTopButtons(canvas, w, h, margin)
        } else {
            buttonRects.clear() // 弹窗打开时屏蔽桌面按钮，只响应弹窗内按钮
            when (val ov = overlay) {
                Overlay.RULES -> drawRulesPopup(canvas, w, h)
                Overlay.SETTINGS -> drawSettingsPopup(canvas, w, h)
                Overlay.CONFIRM -> drawConfirmPopup(canvas, w, h)
                null -> {}
            }
        }
        canvas.restore()
    }

    /** 顶部功能入口；边距 ≥40dp 物理像素（全屏适配方案第 8、9 节，防刘海/圆角裁切） */
    private fun drawTopButtons(canvas: Canvas, w: Float, h: Float, margin: Float) {
        buttonRenderer.draw(
            canvas, "manual", "说明书", margin + w * 0.052f, margin + h * 0.029f, w * 0.104f, h * 0.058f,
            true, buttonRects, icon = ButtonRenderer.Icon.QUESTION
        )
        buttonRenderer.draw(
            canvas, "settings", "设置", w - margin - w * 0.046f, margin + h * 0.029f, w * 0.092f, h * 0.058f,
            true, buttonRects, icon = ButtonRenderer.Icon.GEAR
        )
    }

    // ---- 规则说明弹窗 ----

    private fun drawRulesPopup(canvas: Canvas, w: Float, h: Float) {
        val rect = table.drawPopupBackdrop(canvas, w, h, w * 0.68f, h * 0.82f, surfRect)
        table.drawText(canvas, "游 戏 规 则", w * 0.5f, rect.top + h * 0.065f, h * 0.045f, TextColor.GOLD)
        val lines = listOf(
            "【基本玩法】" to TextColor.GOLD,
            "双方各发 2 张牌，手牌点数尽量接近 21 且不能超过" to TextColor.NORMAL,
            "2~10 按面值，J / Q / K 算 10，A 可算 1 或 11" to TextColor.NORMAL,
            "【你的操作】" to TextColor.GOLD,
            "要牌：再拿一张，超过 21 点即爆牌输掉本局" to TextColor.NORMAL,
            "停牌：不再拿牌，等庄家行动后结算" to TextColor.NORMAL,
            "加倍：仅前两张可用，注翻倍且只再拿一张" to TextColor.NORMAL,
            "【庄家规则】" to TextColor.GOLD,
            "庄家 ≤16 点必须要牌，≥17 点停牌（软 17 也停）" to TextColor.NORMAL,
            "【结算规则】" to TextColor.GOLD,
            "比庄家大：赢，返还 2 倍注；小或爆牌：输掉注" to TextColor.NORMAL,
            "点数相同：平局退注；Blackjack（A+10 牌）赔 3:2" to TextColor.NORMAL,
            "打满总局数按筹码排名，中途筹码耗尽即出局" to TextColor.NORMAL,
        )
        lines.forEachIndexed { i, (text, color) ->
            table.drawTextLeft(
                canvas, text, rect.left + w * 0.035f,
                rect.top + h * 0.135f + i * h * 0.038f, h * 0.028f, color
            )
        }
        buttonRenderer.draw(
            canvas, "close", "知道了", w * 0.5f, rect.bottom - h * 0.055f,
            w * 0.16f, h * 0.06f, true, buttonRects, accent = true
        )
    }

    // ---- 设置弹窗（音效/震动开关 + 音量滑杆；危险操作走二次确认） ----

    private fun drawSettingsPopup(canvas: Canvas, w: Float, h: Float) {
        val rect = table.drawPopupBackdrop(canvas, w, h, w * 0.38f, h * 0.72f, surfRect)
        table.drawText(canvas, "设 置", w * 0.5f, rect.top + h * 0.050f, h * 0.038f, TextColor.GOLD)
        table.drawText(canvas, "轻量桌游音效 · 无背景音乐", w * 0.5f, rect.top + h * 0.095f, h * 0.022f, TextColor.DIM)
        val bw = w * 0.25f
        val bh = h * 0.056f
        buttonRenderer.draw(
            canvas, "toggleSound", if (sfx.soundOn) "音效：开" else "音效：关",
            w * 0.5f, rect.top + h * 0.148f, bw, bh, true, buttonRects,
            accent = sfx.soundOn
        )
        // 音量滑杆（音效优化方案第 8 节：0~100%，默认 70%）
        table.drawText(canvas, "音量 ${sfx.volumePercent}%", w * 0.5f, rect.top + h * 0.228f, h * 0.026f, TextColor.NORMAL)
        val trackY = rect.top + h * 0.280f
        val trackH = h * 0.013f
        val left = w * 0.5f - w * 0.15f
        val right = w * 0.5f + w * 0.15f
        val fillRight = left + (right - left) * (sfx.volumePercent / 100f)
        val knobX = fillRight.coerceIn(left, right)
        canvas.drawRoundRect(RectF(left, trackY - trackH / 2f, right, trackY + trackH / 2f), trackH, trackH, volumeTrackPaint)
        if (fillRight > left) {
            canvas.drawRoundRect(
                RectF(left, trackY - trackH / 2f, fillRight.coerceAtMost(right), trackY + trackH / 2f),
                trackH, trackH, volumeFillPaint
            )
        }
        canvas.drawCircle(knobX, trackY, h * 0.017f, volumeKnobPaint)
        canvas.drawCircle(knobX, trackY, h * 0.017f, volumeKnobEdge)
        volumeTrack = RectF(left, trackY - trackH / 2f, right, trackY + trackH / 2f)
        buttonRenderer.draw(
            canvas, "toggleVibe", if (sfx.vibrationOn) "震动：开" else "震动：关",
            w * 0.5f, rect.top + h * 0.352f, bw, bh, true, buttonRects,
            accent = sfx.vibrationOn
        )
        buttonRenderer.draw(canvas, "backMenu", "返回主菜单", w * 0.5f, rect.top + h * 0.448f, bw, bh, true, buttonRects)
        buttonRenderer.draw(canvas, "restart", "重新开始游戏", w * 0.5f, rect.top + h * 0.540f, bw, bh, true, buttonRects)
        buttonRenderer.draw(canvas, "exit", "退出游戏", w * 0.5f, rect.top + h * 0.632f, bw, bh, true, buttonRects)
    }

    /** 二次确认弹窗（重新开始 / 返回主菜单前防误触） */
    private fun drawConfirmPopup(canvas: Canvas, w: Float, h: Float) {
        val rect = table.drawPopupBackdrop(canvas, w, h, w * 0.52f, h * 0.30f, surfRect)
        table.drawText(canvas, "确 认", w * 0.5f, rect.top + h * 0.085f, h * 0.040f, TextColor.GOLD)
        table.drawText(canvas, confirmMessage, w * 0.5f, rect.top + h * 0.18f, h * 0.030f, TextColor.NORMAL)
        buttonRenderer.draw(
            canvas, "confirmYes", "确 定", w * 0.34f, rect.bottom - h * 0.075f,
            w * 0.16f, h * 0.062f, true, buttonRects, accent = true
        )
        buttonRenderer.draw(
            canvas, "confirmNo", "取 消", w * 0.66f, rect.bottom - h * 0.075f,
            w * 0.16f, h * 0.062f, true, buttonRects
        )
    }

    // ---- 布局常量（比例） ----

    private fun cardW(w: Float, h: Float) = minOf(w * 0.062f, h * 0.105f)
    private fun cardH(cw: Float) = cw * 1.42f

    /** 座位麦穗环半径 */
    private fun ringR(w: Float, h: Float) = minOf(w * 0.064f, h * 0.108f)

    private fun seatPos(seat: Int, w: Float, h: Float): Pair<Float, Float> = when (seat) {
        1 -> 0.50f to 0.705f   // 玩家：底部中央
        2 -> 0.185f to 0.41f   // CPU2：左上
        3 -> 0.815f to 0.41f   // CPU3：右上
        else -> 0.185f to 0.78f // CPU4：左下（压低，避开 CPU2 的头像与筹码行）
    }

    private fun avatarPos(seat: Int, w: Float, h: Float, r: Float): Pair<Float, Float> {
        val (fx, fy) = seatPos(seat, w, h)
        return (w * fx) to (h * fy + r * 1.02f)
    }

    /** 下注筹码位（环右上角；CPU4 镜像到左上避让按钮排） */
    private fun betChipPos(seat: Int, w: Float, h: Float, r: Float): Pair<Float, Float> {
        val (fx, fy) = seatPos(seat, w, h)
        val dir = if (seat == 4) -1f else 1f
        return (w * fx + dir * r * 0.74f) to (h * fy - r * 0.72f)
    }

    private fun avatarStyle(p: Player) = when (p.type) {
        PlayerType.HUMAN -> when (p.seat) {
            1 -> AvatarRenderer.Style.PLAYER
            2 -> AvatarRenderer.Style.PLAYER_2
            3 -> AvatarRenderer.Style.PLAYER_3
            else -> AvatarRenderer.Style.PLAYER_4
        }
        PlayerType.CPU_CONSERVATIVE -> AvatarRenderer.Style.CONSERVATIVE
        PlayerType.CPU_BALANCED -> AvatarRenderer.Style.BALANCED
        PlayerType.CPU_AGGRESSIVE -> AvatarRenderer.Style.AGGRESSIVE
    }

    /** 手牌扇形小角度（参考图里散落的牌面感） */
    private val tilts = floatArrayOf(-7f, 5f, -3f, 7f, -5f, 3f)

    private fun cardStep(cw: Float, count: Int): Float =
        cw * (0.45f - 0.05f * (count - 2).coerceAtLeast(0)).coerceAtLeast(0.28f)

    /** 牌 i 在手牌布局中的目标矩形（发牌飞行落点与此处同一套计算） */
    private fun handCardRect(count: Int, index: Int, cx: Float, cy: Float, cw: Float, ch: Float): RectF {
        val step = cardStep(cw, count)
        val left = cx - (cw + (count - 1) * step) / 2f + index * step
        return RectF(left, cy - ch / 2f, left + cw, cy + ch / 2f)
    }

    private fun shakeOffset(owner: Int, h: Float): Float {
        val t = shakes[owner] ?: return 0f
        return sin(t * PI * 6.0).toFloat() * h * 0.007f * (1f - t)
    }

    // ---- 桌面：座位 + 手牌 + 动画元素 ----

    private fun drawTableSeats(canvas: Canvas, w: Float, h: Float) {
        val cw = cardW(w, h)
        val ch = cardH(cw)
        val r = ringR(w, h)
        val nameSize = h * 0.028f
        val pointSize = h * 0.040f
        val reveal = engine.state in REVEAL_STATES
        val flipActive = holeFlipT in 0f..1f
        // 当前焦点座位：下注阶段=正在定注的真人；局中=正在行动的座位（金环+金字提示）
        val activeSeat = when (engine.state) {
            GameState.BETTING -> bettingSeat
            GameState.PLAYER_TURN, GameState.CPU_PLAYER_2, GameState.CPU_PLAYER_3, GameState.CPU_PLAYER_4 -> engine.currentSeat()
            else -> -1
        }

        // 庄家（顶部中央；无座位环，暗牌翻转前背面朝上）
        val dealerCards = engine.dealer.hand.cards
        table.drawText(canvas, "庄家", w * 0.50f, h * 0.135f - ch / 2f - h * 0.030f, nameSize, TextColor.NORMAL)
        drawHand(
            canvas, dealerCards, w * 0.50f, h * 0.135f, cw, ch,
            faceUpCount = faceUp[0],
            shownCount = if (flipActive) 1 else landed[0],
            shakeDx = shakeOffset(0, h),
        )
        if (flipActive && dealerCards.size >= 2) drawHoleFlip(canvas, dealerCards[1], w, h, cw, ch)
        if (dealerCards.isNotEmpty() && landed[0] > 0 && cardFlights.none { it.owner == 0 }) {
            val allFace = reveal && faceUp[0] >= dealerCards.size && !flipActive
            val bust = allFace && engine.dealer.hand.isBust()
            val text = if (allFace) "${engine.dealer.hand.bestValue()}点" else "?点"
            table.drawText(
                canvas, text, w * 0.50f, h * 0.135f + ch / 2f + h * 0.040f, pointSize,
                if (bust) TextColor.RED else TextColor.DIM
            )
        }

        // 四个玩家座位：麦穗环 + 头像 + 手牌 + 点数徽章
        for (p in engine.players) {
            val (fx, fy) = seatPos(p.seat, w, h)
            val cx = w * fx
            val cy = h * fy
            val isCpu = p.seat != 1
            val scale = if (isCpu) 0.68f else 1f
            val cw2 = cw * scale
            val ch2 = ch * scale

            table.drawSeatRing(canvas, cx, cy, r, highlight = p.seat == activeSeat)

            // 名字（环上方；当前行动者金色，破产变暗并标注）
            val shownName = p.name + if (p.isBankrupt) " · 出局" else ""
            table.drawText(
                canvas, shownName, cx, cy - r - h * 0.055f, nameSize,
                when {
                    p.seat == activeSeat -> TextColor.GOLD
                    p.isBankrupt -> TextColor.DIM
                    else -> TextColor.NORMAL
                }
            )

            // 头像 + 筹码（环底开口处；筹码图标不带面额，数字在旁。
            // CPU4 靠近底部按钮排，筹码簇镜像到头像左侧避让）
            val avY = cy + r * 1.02f
            avatarRenderer.draw(canvas, cx, avY, r * 0.34f, avatarStyle(p))
            if (!p.isBankrupt) {
                if (p.seat == 4) {
                    val chipCx = cx - r * 0.66f
                    chipRenderer.drawPlain(canvas, chipCx, avY, r * 0.17f)
                    table.drawTextRight(canvas, "${p.chips}", chipCx - r * 0.17f - r * 0.12f, avY, r * 0.26f, TextColor.NORMAL)
                } else {
                    val chipCx = cx + r * 0.66f
                    chipRenderer.drawPlain(canvas, chipCx, avY, r * 0.17f)
                    table.drawTextLeft(canvas, "${p.chips}", chipCx + r * 0.17f + r * 0.12f, avY, r * 0.26f, TextColor.NORMAL)
                }
            }

            // 下注筹码（下注阶段显示各家已定注；发牌 / 结算飞行期间由飞行筹码顶替）
            val shownBet = if (engine.state == GameState.BETTING) pendingBets[p.seat] ?: 0 else p.bet
            if (shownBet > 0 && p.seat !in betGone && p.seat !in chipInFly) {
                val (bx, by) = betChipPos(p.seat, w, h, r)
                chipRenderer.draw(canvas, bx, by, r * 0.16f, shownBet)
            }

            // 手牌（只画已落定的牌；布局按逻辑张数算，落牌过程不重新居中）
            drawHand(
                canvas, p.hand.cards, cx, cy, cw2, ch2,
                faceUpCount = faceUp[p.seat],
                shownCount = faceUp[p.seat],
                layoutCount = p.hand.size,
                shakeDx = shakeOffset(p.seat, h),
            )

            // 点数徽章（皇冠位置；牌没落齐前不亮）
            if (p.hand.size > 0 && faceUp[p.seat] >= 2 && cardFlights.none { it.owner == p.seat }) {
                val v = p.hand.bestValue()
                val state = when {
                    p.hand.isBlackjack() -> 2
                    p.hand.isBust() -> 1
                    else -> 0
                }
                table.drawPointBadge(canvas, cx, cy - r * 1.02f, r * 0.56f, r * 0.44f, "$v", state)
            }
        }

        drawCardFlights(canvas, w, h)
        drawChipFlies(canvas, w, h)
        drawPops(canvas, w, h)
    }

    private fun drawHand(
        canvas: Canvas,
        cards: List<Card>,
        cx: Float,
        cy: Float,
        cw: Float,
        ch: Float,
        faceUpCount: Int = Int.MAX_VALUE,
        shownCount: Int = Int.MAX_VALUE,
        layoutCount: Int = cards.size,
        shakeDx: Float = 0f,
    ) {
        val shown = minOf(shownCount, cards.size)
        if (shown <= 0) return
        canvas.save()
        canvas.translate(shakeDx, 0f)
        // 牌越多张距越窄，保证 4~5 张的手牌也基本收在座位环内
        val step = cardStep(cw, layoutCount)
        var x = cx - (cw + (layoutCount - 1) * step) / 2f
        val top = cy - ch / 2f
        for (i in 0 until shown) {
            canvas.save()
            canvas.rotate(tilts[i % tilts.size], x + cw / 2f, cy)
            if (i < faceUpCount) cardRenderer.drawCard(canvas, cards[i], x, top, cw, ch)
            else cardRenderer.drawBack(canvas, x, top, cw, ch)
            canvas.restore()
            x += step
        }
        canvas.restore()
    }

    /** 庄家暗牌原地翻转：横向缩到 0 换面再展开，带一点上浮 */
    private fun drawHoleFlip(canvas: Canvas, hole: Card, w: Float, h: Float, cw: Float, ch: Float) {
        val rect = handCardRect(engine.dealer.hand.size, 1, w * 0.5f, h * 0.135f, cw, ch)
        canvas.save()
        canvas.translate(0f, -sin(PI * holeFlipT).toFloat() * h * 0.012f)
        canvas.rotate(tilts[1 % tilts.size], rect.centerX(), rect.centerY())
        val sx = abs(cos(PI * holeFlipT).toFloat()).coerceAtLeast(0.04f)
        canvas.scale(sx, 1f, rect.centerX(), rect.centerY())
        if (holeFlipT < 0.5f) cardRenderer.drawBack(canvas, rect.left, rect.top, cw, ch)
        else cardRenderer.drawCard(canvas, hole, rect.left, rect.top, cw, ch)
        canvas.restore()
    }

    private fun ownerCards(owner: Int): List<Card> =
        if (owner == 0) engine.dealer.hand.cards else engine.bySeat(owner).hand.cards

    /** 飞行中的牌：从发牌靴口沿弧线落到目标布局位，角度摆正 */
    private fun drawCardFlights(canvas: Canvas, w: Float, h: Float) {
        if (cardFlights.isEmpty()) return
        val (shoeX, shoeY) = table.shoeMouth(w, h, shoeShift)
        val baseW = cardW(w, h)
        val baseH = cardH(baseW)
        for (f in cardFlights) {
            val isDealer = f.owner == 0
            val scale = if (isDealer || f.owner == 1) 1f else 0.68f // 庄家与真人是全尺寸牌
            val cw = baseW * scale
            val ch = baseH * scale
            val (cx, cy) = if (isDealer) (w * 0.5f) to (h * 0.135f)
            else seatPos(f.owner, w, h).let { (fx, fy) -> w * fx to h * fy }
            val target = handCardRect(ownerCards(f.owner).size, f.index, cx, cy, cw, ch)
            val t = f.t
            val x = lerp(shoeX, target.centerX(), t)
            val y = lerp(shoeY, target.centerY(), t) - sin(PI * t).toFloat() * h * 0.045f
            val rot = lerp(-14f, tilts[f.index % tilts.size], t)
            canvas.save()
            canvas.rotate(rot, x, y)
            if (f.faceUpCard) cardRenderer.drawCard(canvas, ownerCards(f.owner)[f.index], x - cw / 2f, y - ch / 2f, cw, ch)
            else cardRenderer.drawBack(canvas, x - cw / 2f, y - ch / 2f, cw, ch)
            canvas.restore()
        }
    }

    /** 飞行中的筹码：开局下注（座位→下注位）；结算赢（庄家→座位）/ 输（座位→庄家） */
    private fun drawChipFlies(canvas: Canvas, w: Float, h: Float) {
        if (chipFlies.isEmpty()) return
        val r = ringR(w, h)
        for (f in chipFlies) {
            val avatar = avatarPos(f.seat, w, h, r)
            val bet = betChipPos(f.seat, w, h, r)
            val from: Pair<Float, Float>
            val to: Pair<Float, Float>
            when (f.mode) {
                ChipMode.DEAL -> { from = avatar; to = bet }
                ChipMode.TO_DEALER -> { from = bet; to = w * 0.5f to h * 0.135f }
                ChipMode.FROM_DEALER -> { from = w * 0.5f to h * 0.135f; to = bet }
            }
            val t = f.t
            val x = lerp(from.first, to.first, t)
            val y = lerp(from.second, to.second, t) - sin(PI * t).toFloat() * h * 0.05f
            val radius = r * 0.16f * when (f.mode) {
                ChipMode.DEAL -> lerp(0.65f, 1f, t)
                ChipMode.TO_DEALER -> lerp(1f, 0.55f, t)
                ChipMode.FROM_DEALER -> lerp(0.55f, 1f, t)
            }
            chipRenderer.draw(canvas, x, y, radius, f.amount)
        }
    }

    /** 闪字：爆牌红字 + 牌区震动；BLACKJACK 金字 + 扩散金环（总方案第 19 节，无粒子） */
    private fun drawPops(canvas: Canvas, w: Float, h: Float) {
        if (pops.isEmpty()) return
        val r = ringR(w, h)
        for ((owner, fx) in pops) {
            val (cx, cy) = if (owner == 0) (w * 0.5f) to (h * 0.135f)
            else seatPos(owner, w, h).let { (px, py) -> w * px to h * py }
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = h * 0.004f
                color = if (fx.gold) Color.argb(((1f - fx.t) * 150).toInt(), 255, 208, 96)
                else Color.argb(((1f - fx.t) * 150).toInt(), 255, 96, 80)
            }
            canvas.drawCircle(cx, cy, r * (0.95f + 0.8f * Ease.outCubic(fx.t)), ring)

            val popY = if (owner == 0) cy + cardH(cardW(w, h)) / 2f + h * 0.095f else cy
            val scale = 0.55f + 0.55f * Ease.outBack(fx.t)
            val alpha = if (fx.t < 0.72f) 255 else (((1f - fx.t) / 0.28f) * 255).toInt()
            canvas.save()
            canvas.translate(cx, popY)
            canvas.scale(scale, scale)
            popPaint.color = if (fx.gold) Color.rgb(255, 214, 96) else Color.rgb(255, 92, 74)
            popPaint.alpha = alpha.coerceIn(0, 255)
            popPaint.textSize = h * 0.056f
            val fm = popPaint.fontMetrics
            canvas.drawText(fx.text, 0f, -(fm.ascent + fm.descent) / 2f, popPaint)
            canvas.restore()
        }
    }

    // ---- 主菜单（层级：主标题 → 副标题 → 真人玩家选择 → 局数选择 → 开始游戏） ----

    private fun drawMainMenu(canvas: Canvas, w: Float, h: Float) {
        val bw = w * 0.105f
        val bh = h * 0.085f
        table.drawText(canvas, "Blackjack 21", w * 0.5f, h * 0.185f, h * 0.095f, TextColor.GOLD)
        table.drawText(canvas, "经典21点 · 本地1-4人游戏 · 100筹码起步", w * 0.5f, h * 0.295f, h * 0.034f, TextColor.DIM)

        table.drawText(canvas, "真人玩家", w * 0.5f, h * 0.40f, h * 0.036f, TextColor.NORMAL)
        for (i in 1..4) {
            buttonRenderer.draw(
                canvas, "h$i", "${i}人", w * (0.20f + 0.20f * (i - 1)), h * 0.495f, bw, bh,
                true, buttonRects, accent = menuHumans == i
            )
        }

        table.drawText(canvas, "整场局数", w * 0.5f, h * 0.625f, h * 0.036f, TextColor.NORMAL)
        buttonRenderer.draw(canvas, "r5", "5 局", w * 0.40f, h * 0.715f, bw, bh, true, buttonRects, accent = menuRounds == 5)
        buttonRenderer.draw(canvas, "r10", "10 局", w * 0.60f, h * 0.715f, bw, bh, true, buttonRects, accent = menuRounds == 10)

        buttonRenderer.draw(canvas, "start", "开 始 游 戏", w * 0.5f, h * 0.875f, w * 0.26f, h * 0.095f, true, buttonRects, accent = true)
    }

    // ---- 下注（多真人轮流定注） ----

    private fun drawBettingControls(canvas: Canvas, w: Float, h: Float) {
        val bw = w * 0.115f
        val bh = h * 0.085f
        val blockX = w * 0.775f
        val current = engine.bySeat(bettingSeat)
        val bet = pendingBets[bettingSeat] ?: 0
        val isLastBettor = engine.players.none {
            it.type == PlayerType.HUMAN && !it.isBankrupt && it.seat > bettingSeat
        }

        // 提示块压在 CPU·平衡头像行下方（0.625h），筹码预览放文字左侧；面额就写在筹码上
        table.drawText(canvas, "第${engine.round + 1}/${engine.totalRounds}局 · 请${current.name}下注", blockX, h * 0.625f, h * 0.036f, TextColor.GOLD)
        if (bet > 0) chipRenderer.draw(canvas, w * 0.681f, h * 0.625f, h * 0.045f, bet)

        buttonRenderer.draw(canvas, "bet5", "+5", w * 0.705f, h * 0.72f, bw, bh, true, buttonRects)
        buttonRenderer.draw(canvas, "bet10", "+10", w * 0.865f, h * 0.72f, bw, bh, true, buttonRects)
        buttonRenderer.draw(canvas, "bet20", "+20", w * 0.705f, h * 0.82f, bw, bh, true, buttonRects)
        buttonRenderer.draw(canvas, "bet50", "+50", w * 0.865f, h * 0.82f, bw, bh, true, buttonRects)
        buttonRenderer.draw(canvas, "allin", "全押", w * 0.705f, h * 0.92f, bw, bh, true, buttonRects)
        buttonRenderer.draw(canvas, "clear", "清零", w * 0.865f, h * 0.92f, bw, bh, true, buttonRects)
        buttonRenderer.draw(
            canvas, "deal", if (isLastBettor) "确认开局" else "下一位",
            w * 0.5f, h * 0.92f, w * 0.20f, bh,
            bet in 1..current.chips, buttonRects, accent = true
        )
    }

    // ---- 局中状态提示与操作按钮 ----

    private fun drawActionButtons(canvas: Canvas, w: Float, h: Float) {
        val bw = w * 0.13f
        val bh = h * 0.085f
        val isHumanTurn = engine.isHumanTurn() && !turnAnimationsBusy()
        buttonRenderer.draw(canvas, "hit", "要牌", w * 0.30f, h * 0.92f, bw, bh, isHumanTurn, buttonRects)
        buttonRenderer.draw(canvas, "stand", "停牌", w * 0.50f, h * 0.92f, bw, bh, isHumanTurn, buttonRects)
        buttonRenderer.draw(
            canvas, "double", "加倍", w * 0.70f, h * 0.92f, bw, bh,
            isHumanTurn && engine.bySeat(engine.currentSeat()).canDouble(), buttonRects
        )
    }

    private fun drawInRoundStatus(canvas: Canvas, w: Float, h: Float) {
        table.drawText(canvas, "回合 ${engine.round}/${engine.totalRounds}", w * 0.895f, h * 0.945f, h * 0.030f, TextColor.DIM)
        val seat = engine.currentSeat()
        val text = when {
            seat == -1 -> "庄家行动…"
            engine.bySeat(seat).type == PlayerType.HUMAN -> "轮到 ${engine.bySeat(seat).name}"
            else -> "其他玩家行动中…"
        }
        table.drawText(canvas, text, w * 0.655f, h * 0.705f, h * 0.036f, TextColor.GOLD)
    }

    // ---- 结算 ----

    private fun drawRoundResult(canvas: Canvas, w: Float, h: Float) {
        if (panelT <= 0f) return // 揭牌动画期间先露桌面，面板随后渐入
        canvas.saveLayerAlpha(0f, 0f, w, h, (panelT * 255).toInt())
        table.drawPanel(canvas, w, h)
        table.drawText(canvas, "第${engine.round}局结算", w * 0.5f, h * 0.16f, h * 0.060f, TextColor.GOLD)

        val dealerText = if (engine.dealer.hand.isBust()) "庄家爆牌（${engine.dealer.hand.bestValue()}点）"
        else "庄家 ${engine.dealer.hand.bestValue()}点"
        table.drawText(canvas, dealerText, w * 0.5f, h * 0.25f, h * 0.045f, TextColor.DIM)

        engine.roundResults.forEachIndexed { i, r ->
            val line = resultLine(r)
            val color = when (r.outcome) {
                Outcome.WIN, Outcome.BLACKJACK -> TextColor.GOLD
                Outcome.LOSE, Outcome.BUST -> TextColor.RED
                Outcome.PUSH -> TextColor.DIM
            }
            table.drawText(canvas, line, w * 0.5f, h * (0.36f + i * 0.08f), h * 0.048f, color)
        }
        if (panelT > 0.55f) {
            buttonRenderer.draw(canvas, "next", "继 续", w * 0.5f, h * 0.85f, w * 0.16f, h * 0.08f, true, buttonRects, accent = true)
        }
        canvas.restore()
    }

    private fun resultLine(r: SeatResult): String {
        val p = engine.bySeat(r.seat)
        val net = r.payout - p.bet
        val netText = when {
            net > 0 -> "+$net"
            net < 0 -> "$net"
            else -> "±0"
        }
        val outcome = when (r.outcome) {
            Outcome.WIN -> "赢"
            Outcome.LOSE -> "输"
            Outcome.PUSH -> "平"
            Outcome.BLACKJACK -> "BLACKJACK"
            Outcome.BUST -> "爆牌"
        }
        return "${p.name}  $outcome  $netText"
    }

    // ---- 整场结束 ----

    private fun drawGameOver(canvas: Canvas, w: Float, h: Float) {
        if (panelT <= 0f) return
        canvas.saveLayerAlpha(0f, 0f, w, h, (panelT * 255).toInt())
        table.drawPanel(canvas, w, h)
        table.drawText(canvas, "游戏结束", w * 0.5f, h * 0.18f, h * 0.075f, TextColor.GOLD)
        engine.finalRanking().forEachIndexed { i, p ->
            val color = if (i == 0) TextColor.GOLD else TextColor.NORMAL
            table.drawText(
                canvas, "第${i + 1}名  ${p.name}  ${p.chips}筹码",
                w * 0.5f, h * (0.33f + i * 0.09f), h * 0.050f, color
            )
        }
        if (panelT > 0.55f) {
            buttonRenderer.draw(canvas, "again", "再来一场", w * 0.5f, h * 0.82f, w * 0.20f, h * 0.085f, true, buttonRects, accent = true)
        }
        canvas.restore()
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private val popPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(4f, 0f, 2f, Color.argb(160, 4, 10, 20))
    }

    // 音量滑杆配色（金色填充与整体主题一致，方案第 10.9 条）
    private val volumeTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(80, 255, 255, 255) }
    private val volumeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 208, 96) }
    private val volumeKnobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(250, 252, 255) }
    private val volumeKnobEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f; color = Color.argb(150, 16, 28, 44)
    }

    private companion object {
        /** 逻辑画布设计基准（全屏适配优化方案第 5 节）：所有元素坐标固定在这块画布上，绘制时等比缩放 */
        const val DESIGN_W = 1920f
        const val DESIGN_H = 1080f

        /** 庄家暗牌揭晓的状态 */
        val REVEAL_STATES = setOf(GameState.SETTLEMENT, GameState.ROUND_RESULT, GameState.GAME_OVER)

        /** CPU 自动行动节拍间隔（发牌 / 补牌飞行期间自动让路） */
        const val STEP_MS = 600L

        /** 一张牌从发牌靴到座位的飞行时长 / 发牌间隔 */
        const val CARD_FLIGHT_MS = 170L
        const val CARD_DEAL_STAGGER = 110L

        /** 筹码飞行与庄家暗牌翻转 */
        const val CHIP_FLIGHT_MS = 280L
        const val HOLE_FLIP_MS = 320L
    }
}
