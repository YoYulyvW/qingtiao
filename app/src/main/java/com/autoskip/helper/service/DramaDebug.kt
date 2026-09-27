package com.autoskip.helper.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 短剧加速调试日志（内存缓冲，供 UI 实时查看） */
object DramaDebug {
    private const val MAX = 200
    private val buffer = ArrayDeque<String>()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    /** 日志开关：关闭时不记录（省性能） */
    @Volatile var enabled: Boolean = false

    fun add(msg: String) {
        if (!enabled) return
        val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date())
        buffer.addLast("[$ts] $msg")
        while (buffer.size > MAX) buffer.removeFirst()
        _logs.value = buffer.toList()
    }

    fun clear() {
        buffer.clear()
        _logs.value = emptyList()
    }
}
