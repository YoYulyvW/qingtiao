package com.autoskip.helper.fenshen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.autoskip.helper.R

/**
 * 悬浮控制台：实时状态 + 可滚动日志 + 复制。
 */
class FloatConsole(
    private val context: Context,
    private val logHeightDp: Int = 90,
    private val onPauseToggle: (Boolean) -> Unit,
    private val onStop: () -> Unit,
    private val onClose: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var wm: WindowManager? = null
    private var rootView: View? = null
    private var statusText: TextView? = null
    private var logText: TextView? = null
    private var logScroll: ScrollView? = null
    private var pauseBtn: Button? = null
    private var paused = false

    /** 完整日志（用于复制） */
    private val allLogs = ArrayList<String>()

    /** 显示层：只保留最近 N 行，避免 TextView 越来越长导致重绘卡顿 */
    private val shownLogs = ArrayDeque<String>()
    private val MAX_SHOWN = 200

    /** 待刷新标记 + 节流，避免每条日志都全量 setText */
    private var pendingRefresh = false
    private val refreshRunnable = Runnable { flushLogs() }

    private var showing = false

    fun show() {
        handler.post {
            if (showing) return@post
            try {
                val wmLocal = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm = wmLocal
                val view = LayoutInflater.from(context).inflate(R.layout.float_console, null)
                rootView = view
                statusText = view.findViewById(R.id.fc_status)
                logText = view.findViewById(R.id.fc_log)
                logScroll = view.findViewById(R.id.fc_scroll)
                pauseBtn = view.findViewById(R.id.fc_pause)

                // 应用用户配置的日志区高度（dp 转 px）
                val hPx = (logHeightDp * context.resources.displayMetrics.density).toInt()
                logScroll?.layoutParams = logScroll?.layoutParams?.apply { height = hPx }

                val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

                val lp = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.TRANSLUCENT
                )
                lp.gravity = Gravity.TOP or Gravity.START
                lp.x = 16
                lp.y = 160

                view.findViewById<Button>(R.id.fc_pause).setOnClickListener {
                    paused = !paused
                    pauseBtn?.text = if (paused) "继续" else "暂停"
                    onPauseToggle(paused)
                }
                view.findViewById<Button>(R.id.fc_stop).setOnClickListener { onStop() }
                view.findViewById<Button>(R.id.fc_copy).setOnClickListener { copyLogs() }
                view.findViewById<Button>(R.id.fc_close).setOnClickListener {
                    dismiss()
                    onClose()
                }

                // 拖动：挂在状态栏上
                val dragHandle = view.findViewById<TextView>(R.id.fc_status)
                var downX = 0f; var downY = 0f
                var startX = 0; var startY = 0
                var dragging = false
                val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop
                dragHandle.setOnTouchListener { _, e ->
                    when (e.action) {
                        MotionEvent.ACTION_DOWN -> {
                            downX = e.rawX; downY = e.rawY
                            startX = lp.x; startY = lp.y
                            dragging = false
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = e.rawX - downX
                            val dy = e.rawY - downY
                            if (!dragging && (kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop)) {
                                dragging = true
                            }
                            if (dragging) {
                                lp.x = startX + dx.toInt()
                                lp.y = startY + dy.toInt()
                                wm?.updateViewLayout(view, lp)
                            }
                            true
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            dragging = false
                            true
                        }
                        else -> false
                    }
                }

                wmLocal.addView(view, lp)
                showing = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun copyLogs() {
        val text = allLogs.joinToString("\n")
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("autoskip_log", text))
        Toast.makeText(context, "日志已复制（${allLogs.size} 行）", Toast.LENGTH_SHORT).show()
    }

    fun setVisible(visible: Boolean) {
        handler.post {
            rootView?.visibility = if (visible) View.VISIBLE else View.GONE
        }
    }

    private var lastStatus = ""
    fun updateStatus(s: String) {
        if (s == lastStatus) return
        lastStatus = s
        handler.post { statusText?.text = s }
    }

    fun appendLog(msg: String) {
        handler.post {
            allLogs.add(msg)
            shownLogs.addLast(msg)
            while (shownLogs.size > MAX_SHOWN) shownLogs.removeFirst()

            // 节流：250ms 内多次调用只刷新一次
            if (!pendingRefresh) {
                pendingRefresh = true
                handler.postDelayed(refreshRunnable, 250)
            }
        }
    }

    /** 实际刷新文本（节流后执行） */
    private fun flushLogs() {
        pendingRefresh = false
        logText?.text = shownLogs.joinToString("\n")
        // 自动滚动到底部
        logScroll?.post { logScroll?.fullScroll(View.FOCUS_DOWN) }
    }

    fun dismiss() {
        handler.post {
            handler.removeCallbacks(refreshRunnable)
            pendingRefresh = false
            try {
                rootView?.let { wm?.removeView(it) }
            } catch (_: Exception) {}
            rootView = null
            showing = false
        }
    }
}
