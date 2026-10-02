package com.autoskip.helper.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * HarmonyOS 色彩体系（华为鸿蒙设计规范）。
 * 浅色模式为主，深色模式对应提供。
 */
object HarmonyColor {
    // ===== 品牌色（浅色模式）=====
    val BrandOrange = Color(0xFF007DFF)   // 主操作色（鸿蒙蓝）
    val BrandBlue = Color(0xFF3A86FF)     // 辅助蓝（浅）
    val DeepBlue = Color(0xFF0050D8)      // 深蓝
    val HuaweiRed = Color(0xFFCF0A2C)     // 华为红
    val Success = Color(0xFF64BB5C)       // 成功绿
    val Error = Color(0xFFE84026)         // 错误红
    val Warning = Color(0xFFE8A739)       // 警告橙
    val InfoYellow = Color(0xFFF2C312)    // 提示黄
    val Purple = Color(0xFF9B59B6)        // 辅助紫

    // ===== 灰阶（浅色模式）=====
    val GrayBG = Color(0xFFF1F3F5)        // 主背景
    val Gray1 = Color(0xFFE5E8EB)         // 次级背景
    val Gray2 = Color(0xFFCDD1D6)         // 分隔线
    val Gray3 = Color(0xFFB2B8C1)         // 禁用态
    val Gray4 = Color(0xFF9AA0A9)         // 占位文字
    val Gray5 = Color(0xFF8C929A)         // 辅助文字
    val Gray6 = Color(0xFF6C7280)         // 次级文字
    val Gray7 = Color(0xFF4A505C)         // 正文文字
    val Gray8 = Color(0xFF333B47)         // 主文字
    val TextPrimary = Color(0xFF191919)   // 极深文字
    val White = Color(0xFFFFFFFF)

    // ===== 品牌色（深色模式，提亮）=====
    val BrandOrangeDark = Color(0xFF4DA0FF)
    val SuccessDark = Color(0xFF7ED680)
    val ErrorDark = Color(0xFFFF6B5E)

    // ===== 灰阶（深色模式）=====
    val DarkBG = Color(0xFF1A1A2E)        // 深蓝黑主背景
    val DarkCard = Color(0xFF22223B)      // 卡片背景
    val DarkTertiary = Color(0xFF2D2D44)  // 三级背景
    val DarkDivider = Color(0xFF3A3A52)   // 分隔线
    val DarkFill = Color(0xFF45455E)      // 输入框背景
    val DarkTextPrimary = Color(0xFFE8E8F0)
    val DarkTextSecondary = Color(0xFFA0A0B8)
    val DarkTextTertiary = Color(0xFF707088)

    // ===== 语义色（浅色）=====
    val StatusOKBg = Color(0xFFE8F5E9)     // 成功状态卡背景
    val StatusWarnBg = Color(0xFFFFF3E0)   // 警告状态卡背景
    val StatusErrBg = Color(0xFFFDECEC)    // 错误状态卡背景
    val IconOrangeBg = Color(0xFFE3F0FF)   // 入口图标底色-蓝
    val IconBlueBg = Color(0xFFE3F0FF)     // 入口图标底色-蓝
    val IconPurpleBg = Color(0xFFF3E5F5)   // 入口图标底色-紫
}
