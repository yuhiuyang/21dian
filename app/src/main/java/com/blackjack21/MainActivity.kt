package com.blackjack21

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import com.blackjack21.ui.BlackjackGameView

/**
 * 唯一入口：全屏 BlackjackGameView，Canvas 绘制整个游戏（总方案第 2 节结构）。
 *
 * 全屏适配（全屏显示与屏幕适配优化方案第 2、3 节）：
 * - 沉浸式隐藏状态栏/导航栏：API 30+ 走 WindowInsetsController，低版本走 systemUiVisibility；
 *   onWindowFocusChanged 重新聚焦时再隐藏一次，防止弹窗/交互唤回系统栏后常驻；
 * - API 28+ 允许窗口延伸进刘海区（SHORT_EDGES）；
 * - 横屏固定在 Manifest（screenOrientation="landscape"），超宽屏防信箱黑边靠 max_aspect 声明；
 * - 刘海/导航栏/圆角屏的安全边距由 GameView 按窗口 insets 内缩，背景铺满整个 Surface。
 */
class MainActivity : Activity() {

    private lateinit var gameView: BlackjackGameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gameView = BlackjackGameView(this)
        setContentView(gameView)
        hideSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /** 沉浸式全屏：系统栏隐藏、滑动临时呼出且不常驻（优化方案第 2 节） */
    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.apply {
                hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        // 低版本主路径；API 30+ 上与 WindowInsetsController 并存无冲突（兼容层自动映射）
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    /** 生命周期直控暂停（比窗口可见性回调及时；见 GameView.setGamePaused 注释） */
    override fun onStart() {
        super.onStart()
        gameView.setGamePaused(false)
    }

    override fun onStop() {
        super.onStop()
        gameView.setGamePaused(true)
    }
}
