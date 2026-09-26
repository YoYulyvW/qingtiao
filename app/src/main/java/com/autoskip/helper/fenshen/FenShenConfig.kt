package com.autoskip.helper.fenshen

/** 分身任务配置（对应 Auto.js 脚本顶部表单） */
data class FenShenConfig(
    val totalCount: Int = 20,
    val suffixFmt: String = "-{date}号",
    val installTimeoutSec: Int = 60,
    val retryTimes: Int = 1,
    val maxFail: Int = 3,
    val saveLog: Boolean = true,
    // 基准分辨率（坐标按此分辨率填写，运行时会按真机等比换算）
    val testW: Int = 1080,
    val testH: Int = 1920,
    // 系统弹窗坐标（基于 testW × testH 基准）
    val installX: Int = 760,   // 安装 / 打开 / 确定 / 不允许
    val installY: Int = 1620,
    val permX: Int = 540,      // 允许
    val permY: Int = 1465,
    val locX: Int = 523,       // 定位：仅在使用该应用时允许
    val locY: Int = 1308,
    // 停止模式：true=按"分身序号"截止，false=按"数量"
    val useStopIndex: Boolean = false,
    // 截止序号：抖音序号 >= 此值就停止（如 50 → 遇到 抖音50 及以上不处理）
    val stopIndex: Int = 50,
    // 悬浮窗日志区高度（dp），默认 90
    val logHeightDp: Int = 90
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
