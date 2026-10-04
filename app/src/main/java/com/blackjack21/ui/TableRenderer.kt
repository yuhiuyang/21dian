package com.blackjack21.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.cos
import kotlin.math.sin

/** 文字配色 */
enum class TextColor { NORMAL, DIM, GOLD, RED }

/**
 * 桌面与文字绘制：蓝色渐变弧形牌桌 + 桌印（弧形标语）+ 麦穗座位环 + 皇冠 + 发牌靴。
 * 风格参照用户提供的派对式 21 点牌桌参考图；全部代码绘制，无外部素材（阶段5美术）。
 */
class TableRenderer {

    private val outer = Paint().apply { color = Color.rgb(11, 16, 26) }
    private val felt = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(22, 32, 50) }
    private val piping = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.argb(110, 215, 235, 255); strokeWidth = 2.5f
    }
    private val glass = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(24, 255, 255, 255) }
    private val logo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(215, 240, 248, 255); typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
        textAlign = Paint.Align.CENTER
    }
    private val printBig = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(225, 238, 247, 255); typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val printSmall = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 225, 240, 255); typeface = Typeface.SERIF; textAlign = Paint.Align.CENTER
    }
    private val fanStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.argb(120, 235, 245, 255); strokeWidth = 2f
    }
    private val leaf = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(100, 224, 240, 252) }
    private val ringLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.argb(65, 224, 240, 252); strokeWidth = 2f
    }
    private val crownPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(160, 232, 242, 255) }
    private val shoeBox = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(42, 255, 255, 255) }
    private val shoeEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.argb(110, 255, 255, 255); strokeWidth = 2f
    }
    private val shoeDeck = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(184, 48, 58) }
    private val panel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(216, 9, 18, 32) }
    private val scrim = Paint().apply { color = Color.argb(125, 5, 9, 16) }
    private val popupBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.argb(140, 255, 255, 255); strokeWidth = 2f
    }

    private val texts = mapOf(
        TextColor.NORMAL to Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(242, 248, 253); typeface = Typeface.DEFAULT_BOLD
        },
        TextColor.DIM to Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(170, 226, 238, 250) },
        TextColor.GOLD to Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 208, 96); typeface = Typeface.DEFAULT_BOLD
        },
        TextColor.RED to Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 122, 104); typeface = Typeface.DEFAULT_BOLD
        },
    )

    // ---- 背景：暗色外圈 + 蓝色渐变弧形桌面 + 玻璃角饰 ----

    /**
     * 背景：暗色外圈 + 蓝色渐变弧形桌面 + 玻璃角饰。
     * (l,t,r,b) 是绝对矩形：GameView 传入覆盖整个物理 Surface 的外扩矩形（全屏适配方案第 7 节），
     * 超出 1920×1080 逻辑画布的部分继续画桌面延展，任何屏幕比例下都没有未绘制的黑边。
     */
    fun drawBackground(canvas: Canvas, l: Float, t: Float, r: Float, b: Float) {
        canvas.drawRect(l, t, r, b, outer)

        val w = r - l
        val h = b - t
        val path = tablePath(l, t, w, h)
        val rimW = h * 0.024f
        val rimStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = rimW * 2f; color = rim.color
        }
        canvas.drawPath(path, rimStroke)
        felt.shader = RadialGradient(
            l + w * 0.5f, t + h * 0.34f, w * 0.72f,
            Color.rgb(76, 162, 226), Color.rgb(20, 82, 148), Shader.TileMode.CLAMP
        )
        canvas.drawPath(path, felt)
        canvas.drawPath(path, piping)

        // 顶部两角磨砂玻璃饰片（参考图角落的透明装饰）
        canvas.save(); canvas.rotate(36f, l + w * 0.075f, t + h * 0.115f)
        canvas.drawRoundRect(RectF(l + w * 0.02f, t + h * 0.055f, l + w * 0.135f, t + h * 0.175f), h * 0.018f, h * 0.018f, glass)
        canvas.restore()
        canvas.save(); canvas.rotate(-36f, l + w * 0.925f, t + h * 0.115f)
        canvas.drawRoundRect(RectF(l + w * 0.865f, t + h * 0.055f, l + w * 0.98f, t + h * 0.175f), h * 0.018f, h * 0.018f, glass)
        canvas.restore()
    }

    /** 桌形：顶边外弧微鼓 + 大圆角，四边留暗色木边；(l,t) 为绘制区原点 */
    private fun tablePath(l: Float, t: Float, w: Float, h: Float): Path {
        val rimW = h * 0.024f
        val left = l + rimW; val right = l + w - rimW
        val top = t + rimW; val bottom = t + h - rimW
        val k = w * 0.055f
        return Path().apply {
            moveTo(left, bottom)
            lineTo(left, top + k)
            cubicTo(left, top + k * 0.2f, left + k * 0.2f, top, left + k, top)
            quadTo(l + w * 0.5f, top - h * 0.016f, right - k, top)
            cubicTo(right - k * 0.2f, top, right, top + k * 0.2f, right, top + k)
            lineTo(right, bottom)
            close()
        }
    }

    // ---- 桌印：BlackJack 标志 + 规则小字弧 + 大标语弧 ----

    /**
     * 桌印：顶部小牌扇 + BlackJack 标志 + 弧形印刷字 + 发牌靴。
     * menu=true 为主菜单：只留顶部小牌扇 + BlackJack 标志 + 发牌靴，跳过弧形印刷字避免与标题重叠。
     * shoeShift：发牌靴下移量（逻辑单位）——顶部按钮按 40dp 安全边距下移时同步让位（全屏适配方案第 8 节）。
     */
    fun drawPrintings(canvas: Canvas, w: Float, h: Float, menu: Boolean = false, shoeShift: Float = 0f) {
        // 顶部三张小牌扇形装饰
        val fanCx = w * 0.5f; val fanCy = h * 0.048f
        val fw = w * 0.020f; val fh = h * 0.032f
        for ((i, a) in listOf(-22f, 0f, 22f).withIndex()) {
            canvas.save(); canvas.rotate(a, fanCx, fanCy + fh)
            val off = (i - 1) * fw * 0.55f
            canvas.drawRoundRect(
                RectF(fanCx - fw / 2 + off, fanCy - fh / 2, fanCx + fw / 2 + off, fanCy + fh / 2),
                fw * 0.18f, fw * 0.18f, fanStroke
            )
            canvas.restore()
        }
        logo.textSize = h * 0.048f
        canvas.drawText("BlackJack", fanCx, h * 0.102f, logo)
        if (menu) {
            drawShoe(canvas, w, h, shoeShift)
            return
        }

        drawArcText(canvas, "庄家 16 点必须要牌 · 17 点停牌", w * 0.5f, h * 0.298f, w * 0.9f, h * 0.026f, printSmall)
        drawArcText(canvas, "BLACKJACK PAYS 3 TO 2", w * 0.5f, h * 0.382f, w * 0.62f, h * 0.052f, printBig)

        drawShoe(canvas, w, h, shoeShift)
    }

    /** 沿上凸圆弧居中写字（参考图桌面弧形印刷字） */
    private fun drawArcText(canvas: Canvas, text: String, cx: Float, apexY: Float, radius: Float, size: Float, paint: Paint) {
        paint.textSize = size
        val halfRad = (paint.measureText(text) / 2f / radius).coerceAtMost(1.2f)
        val halfDeg = Math.toDegrees(halfRad.toDouble()).toFloat()
        val oval = RectF(cx - radius, apexY, cx + radius, apexY + 2 * radius)
        val path = Path()
        path.addArc(oval, 270f - halfDeg, halfDeg * 2f)
        canvas.drawTextOnPath(text, path, 0f, 0f, paint)
    }

    /** 发牌靴出牌口位置（发牌动画的飞行起点，阶段6）；随安全边距下移 [shoeShift] */
    fun shoeMouth(w: Float, h: Float, shoeShift: Float = 0f): Pair<Float, Float> =
        (w * 0.885f) to (h * 0.180f + shoeShift)

    /** 发牌靴：右上角磨砂盒 + 红色牌背（位置避开右上角设置按钮，必要时随 [shoeShift] 下移） */
    private fun drawShoe(canvas: Canvas, w: Float, h: Float, shoeShift: Float = 0f) {
        val bw = w * 0.07f; val bh = h * 0.098f
        val cx = w * 0.9f; val cy = h * 0.138f + shoeShift
        canvas.save(); canvas.rotate(-9f, cx, cy)
        val rect = RectF(cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2)
        canvas.drawRoundRect(rect, h * 0.012f, h * 0.012f, shoeBox)
        canvas.drawRoundRect(rect, h * 0.012f, h * 0.012f, shoeEdge)
        val dw = bw * 0.56f; val dh = bh * 0.72f
        val deck = RectF(cx - dw / 2, cy - dh / 2, cx + dw / 2, cy + dh / 2)
        canvas.drawRoundRect(deck, dw * 0.12f, dw * 0.12f, shoeDeck)
        shoeEdge.color = Color.argb(170, 255, 255, 255)
        canvas.drawRoundRect(
            RectF(deck.left + dw * 0.08f, deck.top + dh * 0.08f, deck.right - dw * 0.08f, deck.bottom - dh * 0.08f),
            dw * 0.1f, dw * 0.1f, shoeEdge
        )
        shoeEdge.color = Color.argb(110, 255, 255, 255)
        canvas.restore()
    }

    // ---- 座位：麦穗环 + 皇冠 ----

    private val ringActive = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.argb(235, 255, 206, 92); strokeWidth = 5f
    }

    /** 麦穗座位环：底部留口给头像，顶部皇冠；highlight=true 时外圈描金（当前行动玩家） */
    fun drawSeatRing(canvas: Canvas, cx: Float, cy: Float, r: Float, highlight: Boolean = false) {
        if (highlight) {
            canvas.drawCircle(cx, cy, r * 1.06f, ringActive)
        }
        canvas.drawCircle(cx, cy, r * 0.86f, ringLine)
        val n = 30
        val start = 128f; val sweep = 284f
        for (i in 0 until n) {
            val ang = start + i * sweep / (n - 1)
            val rad = Math.toRadians(ang.toDouble())
            val lx = cx + (r * cos(rad)).toFloat()
            val ly = cy + (r * sin(rad)).toFloat()
            canvas.save()
            canvas.rotate(ang + 90f + if (i % 2 == 0) 16f else -16f, lx, ly)
            canvas.drawOval(RectF(lx - r * 0.105f, ly - r * 0.038f, lx + r * 0.105f, ly + r * 0.038f), leaf)
            canvas.restore()
        }
        // 皇冠（顶部）
        val s = r * 0.30f
        val cyTop = cy - r - s * 0.42f
        val p = Path().apply {
            moveTo(cx - s, cyTop + s * 0.5f)
            lineTo(cx - s, cyTop - s * 0.3f)
            lineTo(cx - s * 0.48f, cyTop + s * 0.05f)
            lineTo(cx, cyTop - s * 0.55f)
            lineTo(cx + s * 0.48f, cyTop + s * 0.05f)
            lineTo(cx + s, cyTop - s * 0.3f)
            lineTo(cx + s, cyTop + s * 0.5f)
            close()
        }
        canvas.drawPath(p, crownPaint)
    }

    // ---- 点数徽章（放在皇冠位置） ----

    /** state: 0 普通（白底） 1 爆牌（红底+爆牌标签） 2 Blackjack（金底） */
    fun drawPointBadge(canvas: Canvas, cx: Float, cy: Float, bw: Float, bh: Float, text: String, state: Int) {
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = when (state) {
                1 -> Color.rgb(228, 72, 60)
                2 -> Color.rgb(255, 198, 66)
                else -> Color.rgb(246, 250, 254)
            }
        }
        val fg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (state == 1) Color.WHITE else Color.rgb(22, 36, 54)
            typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
        }
        val rect = RectF(cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2)
        canvas.drawRoundRect(rect, bh * 0.42f, bh * 0.42f, bg)
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.argb(140, 16, 28, 44)
        }
        canvas.drawRoundRect(rect, bh * 0.42f, bh * 0.42f, edge)
        fg.textSize = bh * 0.62f
        val fm = fg.fontMetrics
        canvas.drawText(text, cx, cy - (fm.ascent + fm.descent) / 2f, fg)
        if (state == 1) {
            texts.getValue(TextColor.RED).textSize = bh * 0.46f
            canvas.drawText("爆牌", cx, cy - bh * 0.85f, texts.getValue(TextColor.RED))
        }
    }

    // ---- 面板与文字 ----

    /** 半透明圆角遮罩面板（结算 / 排名用）；alpha 供面板渐入动画用 */
    fun drawPanel(canvas: Canvas, w: Float, h: Float, alpha: Float = 1f) {
        panel.alpha = (216 * alpha.coerceIn(0f, 1f)).toInt()
        canvas.drawRoundRect(
            RectF(w * 0.05f, h * 0.07f, w * 0.95f, h * 0.93f),
            h * 0.035f, h * 0.035f, panel
        )
        panel.alpha = 216
    }

    /**
     * 弹窗 backdrop：压暗整屏 + 居中圆角面板，返回面板矩形供内容排版。
     * [scrimRect] 为压暗范围：传 Surface 外扩矩形让超宽屏的桌面延展区也一起压暗（全屏适配方案第 7 节）。
     */
    fun drawPopupBackdrop(canvas: Canvas, w: Float, h: Float, pw: Float, ph: Float, scrimRect: RectF? = null): RectF {
        canvas.drawRect(scrimRect ?: RectF(0f, 0f, w, h), scrim)
        val rect = RectF(w / 2f - pw / 2f, h / 2f - ph / 2f, w / 2f + pw / 2f, h / 2f + ph / 2f)
        canvas.drawRoundRect(rect, h * 0.028f, h * 0.028f, panel)
        canvas.drawRoundRect(rect, h * 0.028f, h * 0.028f, popupBorder)
        return rect
    }

    /** 居中文字 */
    fun drawText(canvas: Canvas, text: String, cx: Float, cy: Float, size: Float, color: TextColor) {
        val paint = texts.getValue(color)
        paint.textSize = size
        canvas.drawText(text, cx - paint.measureText(text) / 2f, centeredBaseline(paint, cy), paint)
    }

    /** 左对齐文字（头像旁的筹码数） */
    fun drawTextLeft(canvas: Canvas, text: String, x: Float, cy: Float, size: Float, color: TextColor) {
        val paint = texts.getValue(color)
        paint.textSize = size
        canvas.drawText(text, x, centeredBaseline(paint, cy), paint)
    }

    /** 右对齐文字 */
    fun drawTextRight(canvas: Canvas, text: String, x: Float, cy: Float, size: Float, color: TextColor) {
        val paint = texts.getValue(color)
        paint.textSize = size
        canvas.drawText(text, x - paint.measureText(text), centeredBaseline(paint, cy), paint)
    }

    private fun centeredBaseline(p: Paint, cy: Float): Float {
        val fm = p.fontMetrics
        return cy - (fm.ascent + fm.descent) / 2f
    }
}
