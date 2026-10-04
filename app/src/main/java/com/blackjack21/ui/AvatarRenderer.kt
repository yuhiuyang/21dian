package com.blackjack21.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/**
 * 代码绘制的卡通头像（阶段5美术，无外部素材）：
 * 后发 + 脸 + 上半刘海 + 侧发 + 眼睛/腮红/嘴，四位玩家用不同发色发型区分性格。
 */
class AvatarRenderer {

    enum class Style(val hair: Int, val longHair: Boolean, val bg: Int) {
        // 真人玩家 1~4：同一卡通风格，用发色/发型/底色区分（多人需求）
        PLAYER(Color.rgb(56, 60, 72), longHair = false, bg = Color.rgb(88, 112, 152)),
        PLAYER_2(Color.rgb(122, 82, 46), longHair = true, bg = Color.rgb(120, 104, 84)),
        PLAYER_3(Color.rgb(196, 98, 52), longHair = false, bg = Color.rgb(150, 104, 74)),
        PLAYER_4(Color.rgb(214, 178, 84), longHair = true, bg = Color.rgb(96, 128, 128)),
        // CPU：发色对应性格
        CONSERVATIVE(Color.rgb(96, 170, 98), longHair = false, bg = Color.rgb(96, 124, 100)),
        BALANCED(Color.rgb(154, 106, 62), longHair = true, bg = Color.rgb(152, 112, 92)),
        AGGRESSIVE(Color.rgb(204, 88, 70), longHair = false, bg = Color.rgb(152, 92, 98)),
    }

    private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hair = Paint(Paint.ANTI_ALIAS_FLAG)
    private val face = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 227, 199) }
    private val feature = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(42, 46, 58) }
    private val blush = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(115, 255, 148, 138) }
    private val mouth = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.rgb(158, 82, 72); strokeCap = Paint.Cap.ROUND
    }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.WHITE }

    fun draw(canvas: Canvas, cx: Float, cy: Float, radius: Float, style: Style) {
        bg.color = style.bg
        canvas.drawCircle(cx, cy, radius, bg)

        hair.color = style.hair
        canvas.drawCircle(cx, cy - radius * 0.04f, radius * 0.98f, hair)
        if (style.longHair) {
            canvas.drawRoundRect(
                RectF(cx - radius * 0.98f, cy - radius * 0.15f, cx - radius * 0.56f, cy + radius * 0.98f),
                radius * 0.2f, radius * 0.2f, hair
            )
            canvas.drawRoundRect(
                RectF(cx + radius * 0.56f, cy - radius * 0.15f, cx + radius * 0.98f, cy + radius * 0.98f),
                radius * 0.2f, radius * 0.2f, hair
            )
        }

        val fr = radius * 0.78f
        val fcy = cy + radius * 0.12f
        canvas.drawCircle(cx, fcy, fr, face)

        // 刘海：脸圆上半部分
        canvas.drawArc(RectF(cx - fr, fcy - fr, cx + fr, fcy + fr), 180f, 180f, true, hair)
        // 鬓角
        canvas.drawCircle(cx - radius * 0.72f, fcy + radius * 0.16f, radius * 0.16f, hair)
        canvas.drawCircle(cx + radius * 0.72f, fcy + radius * 0.16f, radius * 0.16f, hair)

        // 眼睛
        canvas.drawCircle(cx - radius * 0.26f, fcy + fr * 0.42f, radius * 0.085f, feature)
        canvas.drawCircle(cx + radius * 0.26f, fcy + fr * 0.42f, radius * 0.085f, feature)
        // 腮红
        canvas.drawCircle(cx - radius * 0.44f, fcy + fr * 0.62f, radius * 0.10f, blush)
        canvas.drawCircle(cx + radius * 0.44f, fcy + fr * 0.62f, radius * 0.10f, blush)
        // 微笑
        mouth.strokeWidth = radius * 0.07f
        canvas.drawArc(
            RectF(cx - radius * 0.15f, fcy + fr * 0.42f, cx + radius * 0.15f, fcy + fr * 0.80f),
            20f, 140f, false, mouth
        )

        border.strokeWidth = radius * 0.12f
        canvas.drawCircle(cx, cy, radius, border)
    }
}
