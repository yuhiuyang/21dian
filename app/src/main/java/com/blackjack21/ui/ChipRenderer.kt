package com.blackjack21.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

/** 筹码绘制：圆形 + 白色内圈 + 面额文字，颜色按面额区分（总方案第 12 节） */
class ChipRenderer {

    private val body = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.WHITE
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
    }

    fun draw(canvas: Canvas, cx: Float, cy: Float, radius: Float, amount: Int) {
        body.color = colorFor(amount)
        canvas.drawCircle(cx, cy, radius, body)
        ring.strokeWidth = radius * 0.13f
        canvas.drawCircle(cx, cy, radius * 0.82f, ring)
        text.textSize = radius * 0.92f
        val fm = text.fontMetrics
        canvas.drawText("$amount", cx, cy - (fm.ascent + fm.descent) / 2f, text)
    }

    /** 无面额纯色筹码图标（红色，参考图样式；面额由旁边文字表达） */
    fun drawPlain(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        body.color = Color.rgb(198, 60, 54)
        canvas.drawCircle(cx, cy, radius, body)
        ring.strokeWidth = radius * 0.14f
        canvas.drawCircle(cx, cy, radius * 0.80f, ring)
    }

    private fun colorFor(amount: Int): Int = when {
        amount >= 50 -> Color.rgb(45, 45, 52)
        amount >= 20 -> Color.rgb(24, 110, 190)
        amount >= 10 -> Color.rgb(196, 60, 54)
        else -> Color.rgb(46, 125, 80)
    }
}
