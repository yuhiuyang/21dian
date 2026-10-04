package com.blackjack21.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 音效合成器测试（音效优化方案驱动）：SfxSynth 是纯 Kotlin（无 Android 依赖）。
 * 这里把方案的硬指标固化：11 种音效齐全、每种有多个变体（消单调）、时长符合方案节拍、
 * 幅度有界且可闻、起音柔和、响度统一（第 4 节）、高频受限（第 5 节）、输出可复现且互不相同。
 */
class SfxSynthTest {

    private val all = SfxSynth.buildAll()

    private fun flat(): List<Pair<String, ShortArray>> =
        all.flatMap { (id, variants) -> variants.map { id to it } }

    @Test
    fun `方案第7节清单11种音效全部生成`() {
        assertEquals(
            setOf(
                "button", "deal", "flip", "chipPlace", "chipWin", "blackjack",
                "bust", "roundStart", "gameWin", "gameOver", "lose",
            ),
            all.keys,
        )
    }

    @Test
    fun `每种音效有多个变体且变体互不相同`() {
        for ((id, variants) in all) {
            assertTrue("$id 变体数应 ≥3（消单调）", variants.size >= 3)
            val distinct = variants.map { it.toList() }.toSet()
            assertEquals("$id 存在内容相同的变体", variants.size, distinct.size)
        }
    }

    @Test
    fun `每条音效时长在合理范围_发牌翻牌不超过半秒_blackjack零点五到一秒`() {
        val sr = SfxSynth.SAMPLE_RATE
        for ((id, pcm) in flat()) {
            assertTrue("$id 不应短于 40ms", pcm.size >= sr * 40 / 1000)
            assertTrue("$id 不应超过 1.2 秒", pcm.size <= sr * 1200 / 1000)
        }
        for (pcm in all.getValue("deal")) assertTrue("deal 应不超过 0.5s（方案 3.1）", pcm.size <= sr / 2)
        for (pcm in all.getValue("flip")) assertTrue("flip 应不超过 0.5s（方案 3.1）", pcm.size <= sr / 2)
        for (pcm in all.getValue("blackjack")) {
            assertTrue("blackjack 应在 0.5~1s（方案 3.4）：${pcm.size}", pcm.size >= sr / 2 && pcm.size <= sr)
        }
    }

    @Test
    fun `幅度有界且可闻`() {
        for ((id, pcm) in flat()) {
            val peak = pcm.maxOf { abs(it.toInt()) }
            assertTrue("$id 峰值越界: $peak", peak <= 32767)
            assertTrue("$id 几乎是静音（峰值 $peak）", peak > 2000)
        }
    }

    @Test
    fun `起音柔和_开头远小于峰值`() {
        for ((id, pcm) in flat()) {
            val first = abs(pcm[0].toInt())
            val peak = pcm.maxOf { abs(it.toInt()) }
            assertTrue("$id 首样本应远小于峰值（$first vs $peak）", first < peak / 3)
        }
    }

    @Test
    fun `响度统一_各音效RMS接近且按钮音最低`() {
        // 方案第 4 节：所有音效统一响度；第 3.2 节：按钮音量较低不抢游戏音
        fun rms(pcm: ShortArray): Double =
            sqrt(pcm.map { it.toDouble() * it.toDouble() }.average()) / 32767.0
        for ((id, pcm) in flat()) {
            val r = rms(pcm)
            assertTrue("$id RMS 偏离统一响度目标（$r）", r in 0.05..0.13)
        }
        val button = rms(all.getValue("button").first())
        val gameWin = rms(all.getValue("gameWin").first())
        assertTrue("按钮音应明显低于结算奖励音（$button vs $gameWin）", button < gameWin * 0.8)
    }

    @Test
    fun `高频受限_无白噪声式刺耳频段`() {
        // 方案第 5 节：压 3k~8kHz。一阶差分能量对高频极敏感：白噪声 ≈ 2.0，
        // 经 4kHz 两极低通母带处理的音效应显著更低
        fun hfRatio(pcm: ShortArray): Double {
            var e0 = 0.0
            var e1 = 0.0
            for (i in pcm.indices) {
                val x = pcm[i].toDouble()
                e0 += x * x
                if (i > 0) {
                    val d = x - pcm[i - 1].toDouble()
                    e1 += d * d
                }
            }
            return e1 / e0
        }
        for ((id, pcm) in flat()) {
            assertTrue("$id 高频能量过高（${hfRatio(pcm)}）", hfRatio(pcm) < 0.9)
        }
    }

    @Test
    fun `音高走向_胜利类上行_失败类下行_爆牌最低沉`() {
        // 取每条音效最后 40ms 的过零率比较主频（频率越高过零越密）
        fun zeroCrossTail(pcm: ShortArray): Int {
            var zc = 0
            for (i in (pcm.size - SfxSynth.SAMPLE_RATE * 40 / 1000) until pcm.size - 1) {
                if ((pcm[i] >= 0) != (pcm[i + 1] >= 0)) zc++
            }
            return zc
        }
        val bj = zeroCrossTail(all.getValue("blackjack").first())
        val win = zeroCrossTail(all.getValue("gameWin").first())
        val over = zeroCrossTail(all.getValue("gameOver").first())
        val lose = zeroCrossTail(all.getValue("lose").first())
        val bust = zeroCrossTail(all.getValue("bust").first())
        assertTrue("blackjack 尾音应高于 lose（$bj vs $lose）", bj > lose * 2)
        assertTrue("gameWin 尾音应高于 gameOver（$win vs $over）", win > over)
        assertTrue("bust 尾音应最低沉（$bust vs $lose）", bust < lose)
    }

    @Test
    fun `合成可复现_两次构建结果一致`() {
        val again = SfxSynth.buildAll()
        assertEquals(all.keys, again.keys)
        for ((id, variants) in all) {
            assertEquals("$id 变体数一致", variants.size, again.getValue(id).size)
            for (v in variants.indices) {
                assertEquals("$id 变体 $v 两次合成应逐样本一致", variants[v].toList(), again.getValue(id)[v].toList())
            }
        }
    }

    @Test
    fun `全部变体跨音效互不相同`() {
        val distinct = flat().map { it.second.toList() }.toSet()
        assertEquals("存在内容相同的音效/变体", flat().size, distinct.size)
    }
}
