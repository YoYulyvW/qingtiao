package com.autoskip.helper.fenshen

/** 分身任务配置（对应脚本顶部表单） */
data class FenShenConfig(
    val totalCount: Int = 20,
    val suffixFmt: String = "-{date}号",
    val installTimeoutSec: Int = 60,
    val retryTimes: Int = 1,
    val maxFail: Int = 3,
    val saveLog: Boolean = true,
    val testW: Int = 1080,
    val testH: Int = 1920
)

/** 运行状态（供 UI 与悬浮窗读取） */
data class FenShenState(
    val running: Boolean = false,
    val paused: Boolean = false,
    val stopped: Boolean = false,
    val currentIndex: Int = 0,
    val successCount: Int = 0,
    val failCount: Int = 0,
    val statusText: String = "准备中...",
    val lastLog: String = ""
)
