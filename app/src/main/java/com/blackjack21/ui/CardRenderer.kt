package com.blackjack21.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.blackjack21.game.Card
import com.blackjack21.game.Suit

/** 扑克牌绘制：米白圆角卡面 + 点数花色 + 中央花色水印；卡背红色格纹。卡尺寸由调用方给定 */
class CardRenderer {

    private val face = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(252, 250, 244) }
    private val back = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(186, 48, 58) }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.rgb(148, 144, 132); strokeWidth = 2f
    }
    private val backInner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.argb(200, 255, 244, 238); strokeWidth = 2f
    }
    private val lattice = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.argb(70, 255, 244, 238); strokeWidth = 1.5f
    }
    private val watermark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(42, 120, 120, 128) }
    private val black = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(32, 34, 36); typeface = Typeface.DEFAULT_BOLD
    }
    private val red = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(198, 46, 46); typeface = Typeface.DEFAULT_BOLD
    }

    fun drawCard(canvas: Canvas, card: Card, left: Float, top: Float, w: Float, h: Float) {
        val r = w * 0.14f
        val rect = RectF(left, top, left + w, top + h)
        val paint = if (card.suit == Suit.HEARTS || card.suit == Suit.DIAMONDS) red else black
        canvas.drawRoundRect(rect, r, r, face)
        // 中央花色水印
        watermark.textSize = h * 0.46f
        watermark.textAlign = Paint.Align.CENTER
        val fm = watermark.fontMetrics
        canvas.drawText(
            card.suit.symbol, left + w / 2f,
            top + h * 0.60f - (fm.ascent + fm.descent) / 2f, watermark
        )
        canvas.drawRoundRect(rect, r, r, edge)
        paint.textSize = h * 0.30f
        canvas.drawText(card.rank.display, left + w * 0.12f, top + h * 0.36f, paint)
        paint.textSize = h * 0.26f
        canvas.drawText(card.suit.symbol, left + w * 0.12f, top + h * 0.74f, paint)
    }

    fun drawBack(canvas: Canvas, left: Float, top: Float, w: Float, h: Float) {
        val r = w * 0.14f
        val rect = RectF(left, top, left + w, top + h)
        canvas.drawRoundRect(rect, r, r, back)
        // 内边框 + 斜向格纹（裁剪在圆角矩形内）
        val inner = RectF(left + w * 0.10f, top + h * 0.08f, left + w * 0.90f, top + h * 0.92f)
        canvas.save()
        val clip = Path()
        clip.addRoundRect(inner, r * 0.5f, r * 0.5f, Path.Direction.CW)
        canvas.clipPath(clip)
        var x = left - h
        while (x < left + w + h) {
            canvas.drawLine(x, top, x + h * 0.5f, top + h, lattice)
            canvas.drawLine(x + h * 0.5f, top, x, top + h, lattice)
            x += w * 0.22f
        }
        canvas.restore()
        canvas.drawRoundRect(inner, r * 0.5f, r * 0.5f, backInner)
        canvas.drawRoundRect(rect, r, r, edge)
    }
}
