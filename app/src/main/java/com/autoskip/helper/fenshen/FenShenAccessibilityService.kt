package com.autoskip.helper.fenshen

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 分身专用无障碍服务：不监听事件，只提供主动操作能力（点击/滑动/全局动作）。
 * 与 AutoSkip 的监听式服务分开，避免互相干扰。
 */
class FenShenAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "分身服务已连接")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* 不监听 */ }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    /** 当前活动窗口根节点 */
    fun root(): AccessibilityNodeInfo? = rootInActiveWindow

    /** 全局动作：返回 */
    fun globalBack() = performGlobalAction(GLOBAL_ACTION_BACK)

    /** 全局动作：Home */
    fun globalHome() = performGlobalAction(GLOBAL_ACTION_HOME)

    /** 全局动作：最近任务 */
    fun globalRecents() = performGlobalAction(GLOBAL_ACTION_RECENTS)

    /**
     * 按坐标点击（异步，挂起直到手势完成或超时）。
     * 免 root，用 dispatchGesture。
     */
    suspend fun tap(x: Float, y: Float): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 40)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatch(gesture)
    }

    /** 滑动（异步） */
    suspend fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 400): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatch(gesture)
    }

    private suspend fun dispatch(gesture: GestureDescription): Boolean =
        suspendCancellableCoroutine { cont ->
            val ok = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(true)
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(false)
                }
            }, null)
            if (!ok && cont.isActive) cont.resume(false)
        }

    companion object {
        private const val TAG = "FenShen"
        @Volatile var instance: FenShenAccessibilityService? = null
            private set
        fun isRunning(): Boolean = instance != null
    }
}
