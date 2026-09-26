package com.autoskip.helper.fenshen

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
import android.widget.LinearLayout
import android.widget.TextView
import com.autoskip.helper.R

/**
 * 悬浮控制台：实时状态 + 日志 + 暂停/终止/关闭。
 * 对应 Auto.js 的 floaty.rawWindow。
 */
class FloatConsole(
    private val context: Context,
    private val onPauseToggle: (Boolean) -> Unit,
    private val onStop: () -> Unit,
    private val onClose: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var wm: WindowManager? = null
    private var rootView: View? = null
    private var statusText: TextView? = null
    private var logText: TextView? = null
    private var pauseBtn: Button? = null
    private var paused = false
    private val logLines = ArrayDeque<String>()

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
                pauseBtn = view.findViewById(R.id.fc_pause)

                val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

                val lp = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
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
                view.findViewById<Button>(R.id.fc_close).setOnClickListener {
                    dismiss()
                    onClose()
                }

                // 拖动移动
                val panel = view.findViewById<LinearLayout>(R.id.fc_panel)
                var downX = 0f; var downY = 0f
                var startX = 0; var startY = 0
                panel.setOnTouchListener { _, e ->
                    when (e.action) {
                        MotionEvent.ACTION_DOWN -> {
                            downX = e.rawX; downY = e.rawY
                            startX = lp.x; startY = lp.y
                            false
                        }
                        MotionEvent.ACTION_MOVE -> {
                            lp.x = startX + (e.rawX - downX).toInt()
                            lp.y = startY + (e.rawY - downY).toInt()
                            wm?.updateViewLayout(view, lp)
                            false
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

    fun setVisible(visible: Boolean) {
        handler.post {
            rootView?.visibility = if (visible) View.VISIBLE else View.GONE
        }
    }

    fun updateStatus(s: String) {
        handler.post { statusText?.text = s }
    }

    fun appendLog(msg: String) {
        handler.post {
            logLines.addLast(msg)
            while (logLines.size > 3) logLines.removeFirst()
            logText?.text = logLines.joinToString("\n")
        }
    }

    fun dismiss() {
        handler.post {
            try {
                rootView?.let { wm?.removeView(it) }
            } catch (_: Exception) {}
            rootView = null
            showing = false
        }
    }
}
