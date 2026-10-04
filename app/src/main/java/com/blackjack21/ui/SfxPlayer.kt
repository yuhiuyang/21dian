package com.blackjack21.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 音效合成（音效优化方案重制）：11 种桌游风音效全部纯 Kotlin 合成 PCM，零音频素材，
 * 与"全代码绘制"同一路线。本对象无 Android 依赖，可直接单元测试。
 *
 * 风格基调（方案第 2、5 节）——像真实桌游，而不是赌场机器：
 * - 敲击类用模态合成（一组指数衰减正弦），木质 / 树脂质感，不用高频电子音；
 * - 纸牌类用低通白噪声，截止频率从高往低扫，压掉刺耳频段；发牌主体放在 1kHz 上下
 *   的低频"呼"声（200~800Hz 温暖区），牌离手的"嚓"压暗减半增益（用户反馈"尖锐"后调暗）；
 * - 乐音类只用基频 + 低幅度二次 / 三次泛音 + 低八度垫底，暖而不尖；
 * - 每种音效预合成 [VARIANT_COUNT] 个变体（音高 / 噪声 / 时序微抖动），播放时随机选一条——
 *   真实桌游每次动作的声音都略有不同，消除"每次一模一样"的机械单调感；
 * - 母带统一：轻两极低通（4kHz）压 3k~8kHz（方案第 5 节）→ RMS 归一到统一响度（第 4 节）
 *   → 按音效角色乘响度权重（按钮类最低，不抢游戏音）→ 软限幅。
 *
 * 清单（方案第 7 节，另加 lose 结算失败音补全胜负反馈）：
 * button 点击 / deal 发牌 / flip 翻牌 / chipPlace 下注 / chipWin 赢钱 / blackjack 21点 /
 * bust 爆牌 / roundStart 开始回合 / gameWin 最终胜利 / gameOver 游戏结束 / lose 结算失败。
 * 不做背景音乐。噪声用固定种子（变体号参与种子），输出可复现（测试比对用）。
 */
object SfxSynth {

    const val SAMPLE_RATE = 44100

    /** 每种音效预合成的变体数：播放时随机选一条，消单调 */
    const val VARIANT_COUNT = 3

    /** 统一响度目标（满幅 RMS，方案第 4 节）；各音效再乘角色权重：界面音 < 桌面动效 < 结算奖励 */
    private const val TARGET_RMS = 0.11
    private val LOUDNESS = linkedMapOf(
        "button" to 0.60,
        "roundStart" to 0.72,
        "flip" to 0.78,
        "deal" to 0.80,
        "chipPlace" to 0.82,
        "lose" to 0.85,
        "bust" to 0.88,
        "gameOver" to 0.90,
        "chipWin" to 0.92,
        "blackjack" to 0.95,
        "gameWin" to 1.00,
    )

    fun buildAll(): Map<String, List<ShortArray>> =
        LOUDNESS.mapValues { (id, weight) ->
            List(VARIANT_COUNT) { v -> master(render(id, v), weight) }
        }

    // ---- 各音效配方（rnd 只吃变体号与 id 的固定函数，保证可复现） ----

    private fun render(id: String, variant: Int): DoubleArray {
        val rnd = Random(SEED + 977L * variant + 131L * LOUDNESS.keys.indexOf(id))
        return when (id) {
            "button" -> { // 木质按钮"嗒"（方案 3.2：音量较低、柔和确认）
                val b = blank(110)
                addAt(b, 0, tap(105, listOf(
                    Triple(jitter(rnd, 312.0, 14.0), 1.00, jitter(rnd, 46.0, 5.0)),
                    Triple(jitter(rnd, 634.0, 28.0), 0.48, jitter(rnd, 34.0, 4.0)),
                    Triple(jitter(rnd, 1015.0, 45.0), 0.20, 24.0),
                    Triple(jitter(rnd, 1470.0, 60.0), 0.08, 16.0),
                ), seed = SEED + 1 + variant))
                b
            }
            "deal" -> { // 纸牌滑动（方案 3.1）：低频"呼"为主体 + 减半增益的短"嚓"
                val b = blank(230)
                addAt(b, 0, swish(200, jitter(rnd, 1050.0, 130.0), jitter(rnd, 380.0, 60.0),
                    26.0, 8.0, SEED + 2 + variant))
                addAt(b, 14, swish(90, jitter(rnd, 2450.0, 250.0), jitter(rnd, 820.0, 100.0),
                    9.0, 15.0, SEED + 50 + variant, gain = 0.45))
                b
            }
            "flip" -> { // 翻牌：小甩 + 落拍
                val b = blank(220)
                addAt(b, 0, swish(65, jitter(rnd, 2500.0, 200.0), jitter(rnd, 1100.0, 120.0),
                    8.0, 15.0, SEED + 3 + variant))
                addAt(b, 88, swish(85, jitter(rnd, 2100.0, 180.0), jitter(rnd, 900.0, 100.0),
                    10.0, 13.0, SEED + 30 + variant))
                addAt(b, 88, tap(45, listOf(
                    Triple(jitter(rnd, 860.0, 40.0), 0.50, 55.0),
                    Triple(jitter(rnd, 1580.0, 70.0), 0.16, 34.0),
                ), seed = SEED + 5 + variant, noiseGain = 0.0))
                b
            }
            "chipPlace" -> { // 筹码轻碰三连 + 收尾低频落定（方案 3.3：树脂质感，不用金属硬币）
                val b = blank(135)
                val modes = chipModes()
                addAt(b, jitterL(rnd, 0, 6), tap(55, detune(modes, jitter(rnd, 0.94, 0.02)), seed = SEED + 6 + variant))
                addAt(b, jitterL(rnd, 30, 7), tap(55, detune(modes, jitter(rnd, 1.06, 0.02)), seed = SEED + 20 + variant))
                addAt(b, jitterL(rnd, 62, 7), tap(62,
                    detune(modes, jitter(rnd, 1.00, 0.015)) + listOf(Triple(285.0, 0.55, 36.0)),
                    seed = SEED + 8 + variant))
                b
            }
            "chipWin" -> { // 筹码收集渐快 + 上行两音轻奖励（方案 3.3 赢钱）
                val b = blank(610)
                val modes = chipModes()
                addAt(b, jitterL(rnd, 0, 6), tap(50, detune(modes, jitter(rnd, 0.90, 0.02)), seed = SEED + 9 + variant))
                addAt(b, jitterL(rnd, 70, 7), tap(50, detune(modes, jitter(rnd, 0.97, 0.02)), seed = SEED + 10 + variant))
                addAt(b, jitterL(rnd, 125, 7), tap(50, detune(modes, jitter(rnd, 1.04, 0.02)), seed = SEED + 11 + variant))
                addAt(b, jitterL(rnd, 168, 6), tap(55, detune(modes, jitter(rnd, 1.11, 0.02)), seed = SEED + 12 + variant))
                addAt(b, jitterL(rnd, 250, 9), note(jitter(rnd, 783.99, 8.0), 150, 9.0, gain = 0.50))
                addAt(b, jitterL(rnd, 370, 9), note(jitter(rnd, 1046.50, 10.0), 230, 7.0, gain = 0.55))
                b
            }
            "blackjack" -> { // do-mi-sol 上行琶音（方案 3.4，0.5~1s，简单上升旋律）
                val b = blank(610)
                addAt(b, jitterL(rnd, 0, 8), note(jitter(rnd, 523.25, 5.0), 165, 9.0))
                addAt(b, jitterL(rnd, 130, 9), note(jitter(rnd, 659.25, 6.0), 165, 9.0))
                addAt(b, jitterL(rnd, 260, 10), note(jitter(rnd, 783.99, 8.0), 340, 6.5))
                addAt(b, jitterL(rnd, 260, 10), note(jitter(rnd, 392.00, 4.0), 340, 6.5, gain = 0.20)) // 低八度垫底
                b
            }
            "bust" -> { // 短促低沉 boom（方案 3.5：正弦 165→62Hz 下滑 + 闷响，不用警报）
                val b = blank(350)
                val n = samples(350)
                val sweep = DoubleArray(n)
                val fStart = jitter(rnd, 165.0, 12.0)
                val drop = fStart - 62.0
                var phase = 0.0
                for (i in 0 until n) {
                    val t = i.toDouble() / SAMPLE_RATE
                    val f = fStart - (drop * t / 0.30).coerceAtMost(drop)
                    phase += 2.0 * PI * f / SAMPLE_RATE
                    sweep[i] = sin(phase) * min(1.0, t / 0.006) * exp(-7.0 * t) * tailFade(t, 350, 30.0)
                }
                addAt(b, 0, sweep)
                addAt(b, 0, note(jitter(rnd, 55.0, 3.0), 180, 9.0, gain = 0.45))
                addAt(b, 0, swish(70, 400.0, 220.0, 4.0, 26.0, SEED + 13 + variant))
                b
            }
            "roundStart" -> { // 开始回合：柔和两音 chime
                val b = blank(450)
                addAt(b, jitterL(rnd, 0, 5), note(jitter(rnd, 392.00, 3.5), 165, 8.0, gain = 0.50))
                addAt(b, jitterL(rnd, 155, 9), note(jitter(rnd, 523.25, 5.0), 285, 6.0, gain = 0.55))
                b
            }
            "gameWin" -> { // 最终胜利：温暖上行短旋律 + 收尾和弦
                val b = blank(810)
                addAt(b, jitterL(rnd, 0, 6), note(jitter(rnd, 523.25, 5.0), 150, 9.0, gain = 0.50))
                addAt(b, jitterL(rnd, 120, 8), note(jitter(rnd, 659.25, 6.0), 150, 9.0, gain = 0.50))
                addAt(b, jitterL(rnd, 240, 9), note(jitter(rnd, 783.99, 7.0), 150, 9.0, gain = 0.50))
                addAt(b, jitterL(rnd, 360, 10), note(jitter(rnd, 1046.50, 10.0), 440, 5.5, gain = 0.60))
                addAt(b, jitterL(rnd, 360, 10), note(jitter(rnd, 783.99, 7.0), 440, 5.5, gain = 0.25))
                addAt(b, jitterL(rnd, 360, 10), note(jitter(rnd, 523.25, 5.0), 440, 5.5, gain = 0.15))
                b
            }
            "gameOver" -> { // 游戏结束：轻缓下行，不刺耳
                val b = blank(760)
                addAt(b, jitterL(rnd, 0, 7), note(jitter(rnd, 659.25, 6.0), 165, 8.5, gain = 0.50))
                addAt(b, jitterL(rnd, 170, 8), note(jitter(rnd, 523.25, 5.0), 165, 8.5, gain = 0.50))
                addAt(b, jitterL(rnd, 340, 10), note(jitter(rnd, 392.00, 4.0), 410, 5.5, gain = 0.55))
                addAt(b, jitterL(rnd, 340, 10), note(jitter(rnd, 196.00, 2.0), 410, 5.5, gain = 0.15))
                b
            }
            "lose" -> { // 结算失败：低沉两音下行，表达失败即可
                val b = blank(530)
                addAt(b, jitterL(rnd, 0, 7), note(jitter(rnd, 329.63, 3.5), 195, 7.5, gain = 0.50))
                addAt(b, jitterL(rnd, 210, 9), note(jitter(rnd, 246.94, 2.5), 310, 6.0, gain = 0.55))
                b
            }
            else -> error("未知音效: $id")
        }
    }

    /** 树脂筹码的模态频率（高频成分给小增益，方案第 5 节） */
    private fun chipModes() = listOf(
        Triple(920.0, 1.00, 50.0),
        Triple(1440.0, 0.55, 38.0),
        Triple(2130.0, 0.24, 28.0),
        Triple(2950.0, 0.08, 20.0),
    )

    private fun detune(modes: List<Triple<Double, Double, Double>>, k: Double) =
        modes.map { (f, g, d) -> Triple(f * k, g, d) }

    private fun jitter(rnd: Random, base: Double, spread: Double): Double =
        base + (rnd.nextDouble() * 2.0 - 1.0) * spread

    private fun jitterL(rnd: Random, baseMs: Long, spreadMs: Long): Long =
        (baseMs + (rnd.nextDouble() * 2.0 - 1.0) * spreadMs).roundToLong().coerceAtLeast(0L)

    // ---- 合成原语 ----

    /**
     * 柔和乐音：基频 + 低幅度二次 / 三次泛音 + 低八度垫底（暖而不尖），
     * 8ms 软起音 + 指数衰减 + 收尾渐弱（避免截断爆点）。
     */
    private fun note(freq: Double, ms: Long, decay: Double, gain: Double = 0.55): DoubleArray {
        val n = samples(ms)
        val out = DoubleArray(n)
        val w = 2.0 * PI * freq / SAMPLE_RATE
        for (i in 0 until n) {
            val t = i.toDouble() / SAMPLE_RATE
            val env = min(1.0, t / 0.008) * exp(-decay * t) * tailFade(t, ms, 28.0)
            val s = sin(w * i) + 0.22 * sin(2 * w * i) + 0.06 * sin(3 * w * i) + 0.12 * sin(w * i / 2)
            out[i] = s * env * gain
        }
        return out
    }

    /**
     * 模态敲击：一组指数衰减正弦（木质 / 树脂的"嗒"）+ 一点低通噪声初始瞬态，
     * 比纯正弦 Click 温润，天然不含刺耳高频。
     */
    private fun tap(
        ms: Long,
        modes: List<Triple<Double, Double, Double>>,
        seed: Long,
        noiseGain: Double = 0.15,
    ): DoubleArray {
        val n = samples(ms)
        val out = DoubleArray(n)
        val rnd = Random(seed)
        var ny = 0.0
        val na = 1.0 - exp(-2.0 * PI * 2000.0 / SAMPLE_RATE)
        for (i in 0 until n) {
            val t = i.toDouble() / SAMPLE_RATE
            var s = 0.0
            for ((f, g, d) in modes) s += g * sin(2.0 * PI * f / SAMPLE_RATE * i) * exp(-d * t)
            ny += na * ((rnd.nextDouble() * 2.0 - 1.0) - ny)
            s += ny * noiseGain * exp(-90.0 * t)
            out[i] = s * min(1.0, t / 0.0015) * tailFade(t, ms, 10.0)
        }
        return out
    }

    /** 纸牌滑动：低通白噪声扫频（f0 → f1 往低扫），软起音 + 收尾渐弱 */
    private fun swish(
        ms: Long,
        f0: Double,
        f1: Double,
        attackMs: Double,
        decay: Double,
        seed: Long,
        gain: Double = 1.0,
    ): DoubleArray {
        val n = samples(ms)
        val out = DoubleArray(n)
        val rnd = Random(seed)
        var y = 0.0
        for (i in 0 until n) {
            val fc = f0 + (f1 - f0) * (i.toDouble() / n)
            val a = 1.0 - exp(-2.0 * PI * fc / SAMPLE_RATE)
            y += a * ((rnd.nextDouble() * 2.0 - 1.0) - y)
            val t = i.toDouble() / SAMPLE_RATE
            out[i] = y * min(1.0, t / (attackMs / 1000.0)) * exp(-decay * t) * tailFade(t, ms, 14.0) * gain
        }
        return out
    }

    private fun samples(ms: Long) = (SAMPLE_RATE * ms / 1000.0).toInt()

    private fun blank(ms: Long) = DoubleArray(samples(ms))

    /** 把 src 叠加进 dst 的 atMs 处（原地混音） */
    private fun addAt(dst: DoubleArray, atMs: Long, src: DoubleArray) {
        val off = samples(atMs)
        for (i in src.indices) {
            val j = off + i
            if (j >= dst.size) break
            dst[j] += src[i]
        }
    }

    /** 结尾 [releaseMs] 线性渐弱到 0：任何分段结束时都不落在非零幅度上（防爆点） */
    private fun tailFade(t: Double, ms: Long, releaseMs: Double): Double {
        val left = ms / 1000.0 - t
        val release = releaseMs / 1000.0
        return if (left >= release) 1.0 else (left / release).coerceIn(0.0, 1.0)
    }

    /**
     * 母带：轻两极低通（4kHz，-12dB/倍频程，压 3k~8kHz 刺耳频段，方案第 5 节）
     * → RMS 归一到统一响度（方案第 4 节）→ 响度权重 → 软限幅 0.92 → int16。
     */
    private fun master(buf: DoubleArray, weight: Double): ShortArray {
        val a = 1.0 - exp(-2.0 * PI * 4000.0 / SAMPLE_RATE)
        var y1 = 0.0
        var y2 = 0.0
        var sumSq = 0.0
        val soft = DoubleArray(buf.size)
        for (i in buf.indices) {
            y1 += a * (buf[i] - y1)
            y2 += a * (y1 - y2)
            soft[i] = y2
            sumSq += y2 * y2
        }
        val rms = sqrt(sumSq / buf.size.coerceAtLeast(1))
        val g = if (rms > 1e-6) TARGET_RMS / rms * weight else 0.0
        val out = ShortArray(buf.size)
        for (i in out.indices) {
            val v = (soft[i] * g).coerceIn(-0.92, 0.92)
            out[i] = (v * 32767.0).toInt().toShort()
        }
        return out
    }

    private const val SEED = 20261002L
}

/**
 * 音效与震动播放：每条音效一条 MODE_STATIC 的 AudioTrack，播放时回卷重放；
 * 每种音效有 [SfxSynth.VARIANT_COUNT] 个变体，播放时随机选一条（真实桌游每次动作声音都略有差异）；
 * 音效开关 / 震动开关 / 音量（0~100，默认 70%——音效优化方案第 8 节，不默认最大）
 * 写入 SharedPreferences 跨次启动记忆。
 */
class SfxPlayer(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val pcm = SfxSynth.buildAll()
    private val tracks = HashMap<String, HashMap<Int, AudioTrack>>()

    var soundOn: Boolean
        get() = prefs.getBoolean(KEY_SOUND, true)
        set(value) = prefs.edit().putBoolean(KEY_SOUND, value).apply()

    var vibrationOn: Boolean
        get() = prefs.getBoolean(KEY_VIBRATION, true)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATION, value).apply()

    /** 主音量 0~100（默认 70%），即时作用于已建轨道 */
    var volumePercent: Int
        get() = prefs.getInt(KEY_VOLUME, DEFAULT_VOLUME)
        set(value) {
            prefs.edit().putInt(KEY_VOLUME, value.coerceIn(0, 100)).apply()
            for (group in tracks.values) for (t in group.values) applyGain(t)
        }

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    // ---- 音效入口（音效优化方案第 7 节清单） ----

    fun button() = play("button")
    fun deal() = play("deal")
    fun flip() = play("flip")
    fun chipPlace() = play("chipPlace")
    fun chipWin() = play("chipWin")
    fun blackjack() = play("blackjack")
    fun bust() = play("bust")
    fun roundStart() = play("roundStart")
    fun gameWin() = play("gameWin")
    fun gameOver() = play("gameOver")
    fun lose() = play("lose")

    // ---- 震动（毫秒节奏，仅结算类事件用） ----

    fun vibrateWin() = vibrate(longArrayOf(0, 40, 60, 40))
    fun vibrateLose() = vibrate(longArrayOf(0, 70))
    fun vibrateBlackjack() = vibrate(longArrayOf(0, 40, 50, 40, 50, 90))

    private fun vibrate(pattern: LongArray) {
        if (!vibrationOn) return
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(pattern, -1)
        }
    }

    // ---- AudioTrack 播放 ----

    private fun play(id: String) {
        if (!soundOn) return
        try {
            val variants = pcm.getValue(id)
            val v = Random.Default.nextInt(variants.size)
            val track = tracks.getOrPut(id) { HashMap() }.getOrPut(v) { buildTrack(variants[v]) }
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
            track.setPlaybackHeadPosition(0)
            track.play()
        } catch (_: IllegalStateException) {
            // 音频设备被占用 / 释放后误触：静默跳过，音效不该影响游戏
        }
    }

    private fun buildTrack(buffer: ShortArray): AudioTrack {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SfxSynth.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(buffer.size * 2)
            .build()
        track.write(buffer, 0, buffer.size)
        applyGain(track)
        return track
    }

    private fun applyGain(track: AudioTrack) {
        val g = volumePercent / 100f
        track.setStereoVolume(g, g)
    }

    /** 界面销毁时释放音频资源 */
    fun release() {
        for (group in tracks.values) for (t in group.values) t.release()
        tracks.clear()
    }

    private companion object {
        const val PREFS = "blackjack21_sfx"
        const val KEY_SOUND = "sound_on"
        const val KEY_VIBRATION = "vibration_on"
        const val KEY_VOLUME = "volume_percent"
        const val DEFAULT_VOLUME = 70
    }
}
