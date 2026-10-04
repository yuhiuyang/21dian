package com.blackjack21.ui

import android.os.SystemClock
import android.view.View

/**
 * 阶段6 极轻量动画层：一条动画 = 延迟 + 时长 + 每帧进度回调。
 * 不引属性系统、不建对象池——一局同时在播的动画不超过二十条，列表遍历足够（总方案第 19 节"不做重型效果"）。
 * 帧循环用 View.postOnAnimation 驱动，全部播完即停帧：无动画时零重绘（总方案第 22 节）。
 */
class AnimPlayer(private val view: View) {

    /** 一条动画；onUpdate 收到线性 0~1 进度，缓动由调用方按需套 [Ease] */
    private class Anim(
        val startAt: Long,
        val delayMs: Long,
        val durationMs: Long,
        val onUpdate: (Float) -> Unit,
        val onEnd: (() -> Unit)?,
    )

    private val anims = mutableListOf<Anim>()
    private var looping = false

    val isBusy: Boolean get() = anims.isNotEmpty()

    /** 播一条动画；delay 后开始，duration 内回调进度，结束时回调 onEnd */
    fun play(delayMs: Long, durationMs: Long, onUpdate: (Float) -> Unit, onEnd: (() -> Unit)? = null) {
        anims += Anim(SystemClock.elapsedRealtime(), delayMs, durationMs.coerceAtLeast(1L), onUpdate, onEnd)
        startLoop()
    }

    /** 丢弃全部未播完的动画（重开一局 / 换引擎时） */
    fun cancelAll() {
        anims.clear()
    }

    /**
     * 自愈：后台期间 Choreographer 可能丢弃挂起的帧回调，looping 标记悬空导致帧循环死锁
     * （表现为回前台后动画冻结、按钮因 turnAnimationsBusy 永久禁用）。
     * 回前台时调用一次：还有动画在单子上却没人驱动，就重新起帧。
     */
    fun ensureRunning() {
        if (anims.isNotEmpty() && !looping) startLoop()
    }

    private fun startLoop() {
        if (looping) return
        looping = true
        view.postOnAnimation(::frame)
    }

    private fun frame() {
        val now = SystemClock.elapsedRealtime()
        val iter = anims.iterator()
        while (iter.hasNext()) {
            val a = iter.next()
            val elapsed = now - a.startAt - a.delayMs
            if (elapsed < 0) continue
            val t = (elapsed.toFloat() / a.durationMs).coerceIn(0f, 1f)
            a.onUpdate(t)
            if (t >= 1f) {
                iter.remove()
                a.onEnd?.invoke()
            }
        }
        // 无论是否播完，本帧处理结果都要上屏（收尾帧：按钮解禁 / 牌落定等状态变化必须画出）
        view.invalidate()
        if (anims.isNotEmpty()) {
            view.postOnAnimation(::frame)
        } else {
            looping = false
        }
    }
}

/** 常用缓动 */
object Ease {
    /** 先快后慢 */
    fun outQuad(t: Float): Float = 1f - (1f - t) * (1f - t)

    /** 更明显的减速收尾 */
    fun outCubic(t: Float): Float {
        val u = 1f - t
        return 1f - u * u * u
    }

    /** 过冲回弹（弹窗 / 亮字入场） */
    fun outBack(t: Float): Float {
        val c = 1.70158f
        val u = t - 1f
        return 1f + (c + 1f) * u * u * u + c * u * u
    }
}
