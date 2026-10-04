package com.blackjack21.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface

/**
 * 磨砂玻璃风圆角按钮：绘制并登记命中区域，禁用态不登记（不可点）。
 * accent=true 时用金色填充（确认开局 / 菜单主按钮）。按钮区域每帧统一清空重建，触控时查表命中。
 * 可选小图标（? / ⚙）：代码绘制，不依赖特殊字体字形。
 */
class ButtonRenderer {

    /** 顶部入口小图标 */
    enum class Icon { QUESTION, GEAR }

    private val fillOn = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(172, 240, 248, 255) }
    private val fillOff = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 225, 238, 250) }
    private val fillAccent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 198, 88) }
    private val borderOn = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.argb(200, 255, 255, 255)
    }
    private val borderOff = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.argb(90, 255, 255, 255)
    }
    private val textOn = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(20, 36, 56); typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
    }
    private val textOff = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 235, 244, 252); typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
    }
    private val iconRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.rgb(20, 36, 56); strokeWidth = 2.5f
    }
    private val iconFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(20, 36, 56) }
    private val iconHole = Paint(Paint.ANTI_ALIAS_FLAG)

    fun draw(
        canvas: Canvas,
        id: String,
        label: String,
        cx: Float,
        cy: Float,
        w: Float,
        h: Float,
        enabled: Boolean,
        registry: MutableMap<String, RectF>,
        accent: Boolean = false,
        icon: Icon? = null,
    ) {
        val rect = RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
        val r = h * 0.35f
        when {
            !enabled -> { canvas.drawRoundRect(rect, r, r, fillOff); canvas.drawRoundRect(rect, r, r, borderOff) }
            accent -> { canvas.drawRoundRect(rect, r, r, fillAccent); canvas.drawRoundRect(rect, r, r, borderOn) }
            else -> { canvas.drawRoundRect(rect, r, r, fillOn); canvas.drawRoundRect(rect, r, r, borderOn) }
        }
        val paint = if (enabled) textOn else textOff
        paint.textSize = h * 0.42f
        val fm = paint.fontMetrics
        val baseline = cy - (fm.ascent + fm.descent) / 2f
        if (icon == null) {
            canvas.drawText(label, cx, baseline, paint)
        } else {
            // 图标占左侧一小格，文字整体右移保持视觉居中
            val iconR = h * 0.20f
            val iconCx = cx - paint.measureText(label) / 2f - w * 0.045f
            drawIcon(canvas, iconCx, cy, iconR, icon, paint)
            canvas.drawText(label, cx + iconR * 0.9f, baseline, paint)
        }
        if (enabled) registry[id] = rect
    }

    private fun drawIcon(canvas: Canvas, cx: Float, cy: Float, r: Float, icon: Icon, paint: Paint) {
        when (icon) {
            Icon.QUESTION -> {
                iconRing.color = paint.color
                canvas.drawCircle(cx, cy, r, iconRing)
                paint.textSize = r * 1.5f
                val fm = paint.fontMetrics
                canvas.drawText("?", cx, cy - (fm.ascent + fm.descent) / 2f, paint)
            }
            Icon.GEAR -> {
                iconFill.color = paint.color
                val tooth = r * 0.42f
                for (i in 0 until 8) {
                    canvas.save()
                    canvas.rotate(i * 45f, cx, cy)
                    canvas.drawRect(cx - tooth / 2f, cy - r * 1.22f, cx + tooth / 2f, cy - r * 0.72f, iconFill)
                    canvas.restore()
                }
                canvas.drawCircle(cx, cy, r * 0.95f, iconFill)
                iconHole.color = Color.argb(150, 240, 248, 255)
                canvas.drawCircle(cx, cy, r * 0.40f, iconHole)
            }
        }
    }
}
