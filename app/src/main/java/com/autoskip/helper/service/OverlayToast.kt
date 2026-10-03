package com.autoskip.helper.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

/**
 * 识别提示悬浮窗：屏幕上方 25% 处，2 秒自动消失。
 * - 不抢占焦点、不阻塞触摸（FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE）
 * - 相同文本 500ms 内节流（防刷屏）
 * - 单 View 复用，避免频繁 add/remove
 * - 需要 SYSTEM_ALERT_WINDOW 权限（Settings.canDrawOverlays）
 */
object OverlayToast {
    private const val SHOW_DURATION_MS = 4000L   // 默认 4 秒
    private const val THROTTLE_MS = 500L

    private val handler = Handler(Looper.getMainLooper())
    private var wm: WindowManager? = null
    private var view: TextView? = null
    private var lp: WindowManager.LayoutParams? = null

    private var lastText = ""
    private var lastTime = 0L

    private val hideRunnable = Runnable { hideInternal() }

    /** 显示提示（线程安全，内部切主线程） */
    fun show(ctx: Context, text: String, durationMs: Long = SHOW_DURATION_MS) {
        if (text.isBlank()) return
        val now = System.currentTimeMillis()
        if (text == lastText && now - lastTime < THROTTLE_MS) return
        lastText = text
        lastTime = now
        handler.post { showInternal(ctx.applicationContext, text, durationMs) }
    }

    private fun showInternal(ctx: Context, text: String, durationMs: Long) {
        try {
            if (wm == null) {
                wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            }
            if (view == null) {
                view = TextView(ctx).apply {
                    setTextColor(0xFFFFFFFF.toInt())
                    textSize = 13f
                    setPadding(28, 16, 28, 16)
                    setBackgroundColor(0xCC000000.toInt())
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                }
                val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
                lp = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    y = (ctx.resources.displayMetrics.heightPixels * 0.25f).toInt()
                }
            }
            val v = view ?: return
            v.text = text
            if (!v.isAttachedToWindow) {
                wm?.addView(v, lp)
            } else {
                wm?.updateViewLayout(v, lp)
            }
            handler.removeCallbacks(hideRunnable)
            handler.postDelayed(hideRunnable, durationMs)
        } catch (_: Exception) {
            // 权限不足 / 系统拒绝 → 静默忽略
        }
    }

    private fun hideInternal() {
        try {
            val v = view ?: return
            if (v.isAttachedToWindow) wm?.removeView(v)
        } catch (_: Exception) {}
    }
}
