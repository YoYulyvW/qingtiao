package com.autoskip.helper.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * HyperOS 色彩体系（小米 HyperOS 设计规范）。
 * 浅色模式为主，深色模式对应提供。
 */
object HyperColor {
    // ===== 品牌色（浅色模式）=====
    val BrandOrange = Color(0xFFFF6900)   // 小米橙（主操作色）
    val BrandBlue = Color(0xFF4A90D9)     // 活力蓝（辅助）
    val DeepBlue = Color(0xFF1A73E8)      // 深蓝（次级操作）
    val Success = Color(0xFF00B853)       // 成功绿
    val Error = Color(0xFFD32F2F)         // 错误红
    val Warning = Color(0xFFFF9800)       // 警告橙
    val InfoYellow = Color(0xFFFFC107)    // 提示黄
    val Purple = Color(0xFF7B1FA2)        // 辅助紫
    val Cyan = Color(0xFF00ACC1)          // 辅助青

    // ===== 灰阶（浅色模式）=====
    val GrayBG = Color(0xFFF5F5F5)        // 主背景
    val Gray1 = Color(0xFFEEEEEE)         // 次级背景
    val Gray2 = Color(0xFFE0E0E0)         // 分隔线
    val Gray3 = Color(0xFFBDBDBD)         // 禁用态
    val Gray4 = Color(0xFF9E9E9E)         // 占位文字
    val Gray5 = Color(0xFF757575)         // 辅助文字
    val Gray6 = Color(0xFF616161)         // 次级文字
    val Gray7 = Color(0xFF424242)         // 正文文字
    val TextPrimary = Color(0xFF212121)   // 主文字
    val White = Color(0xFFFFFFFF)

    // ===== 品牌色（深色模式，提亮）=====
    val BrandOrangeDark = Color(0xFFFF8A33)
    val SuccessDark = Color(0xFF4CAF50)
    val ErrorDark = Color(0xFFEF5350)

    // ===== 灰阶（深色模式）=====
    val DarkBG = Color(0xFF0D0D0D)        // 纯黑主背景
    val DarkCard = Color(0xFF1A1A1A)      // 卡片背景
    val DarkTertiary = Color(0xFF242424)  // 三级背景
    val DarkDivider = Color(0xFF333333)   // 分隔线
    val DarkFill = Color(0xFF3D3D3D)      // 输入框背景
    val DarkTextPrimary = Color(0xFFF0F0F0)
    val DarkTextSecondary = Color(0xFFB0B0B0)
    val DarkTextTertiary = Color(0xFF808080)

    // ===== 语义色（浅色）=====
    val StatusOKBg = Color(0xFFE8F5E9)     // 成功状态卡背景
    val StatusWarnBg = Color(0xFFFFF3E0)   // 警告状态卡背景
    val StatusErrBg = Color(0xFFFDECEC)    // 错误状态卡背景
    val IconOrangeBg = Color(0xFFFFF3E0)   // 入口图标底色-橙
    val IconBlueBg = Color(0xFFE3F2FD)     // 入口图标底色-蓝
    val IconPurpleBg = Color(0xFFF3E5F5)   // 入口图标底色-紫
}
