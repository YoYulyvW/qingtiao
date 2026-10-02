package com.autoskip.helper.ui.theme

import androidx.compose.ui.unit.dp

/**
 * HarmonyOS 设计令牌：间距与圆角。
 * 规范来源：华为鸿蒙设计系统（8vp 基础网格）。
 */
object Dimens {
    // ===== 间距（8vp 网格，4vp 微调）=====
    /** 2dp - 极小 */
    val SpaceXXS = 2.dp
    /** 4dp - 微调单位 */
    val SpaceXS = 4.dp
    /** 8dp - 基础单位 */
    val SpaceS = 8.dp
    /** 12dp - 卡片内边距 */
    val SpaceM = 12.dp
    /** 16dp - 水平边距 */
    val SpaceL = 16.dp
    /** 20dp - 大间距 */
    val SpaceXL = 20.dp
    /** 24dp - 区块间距 */
    val SpaceXXL = 24.dp
    /** 32dp - 页面顶部 */
    val SpaceXXXL = 32.dp

    // ===== 圆角（鸿蒙体系）=====
    /** 8dp - 标签/徽章 */
    val RadiusTag = 8.dp
    /** 12dp - 输入框/次级按钮 */
    val RadiusInput = 12.dp
    /** 16dp - 小卡片 */
    val RadiusSmall = 16.dp
    /** 20dp - 中卡片 */
    val RadiusCard = 20.dp
    /** 24dp - 大卡片/胶囊按钮/弹窗 */
    val RadiusLarge = 24.dp

    // ===== 组件尺寸 =====
    /** 最小触控区域（无障碍） */
    val MinTouchTarget = 44.dp
    /** 标准列表行高 */
    val ListItemHeight = 48.dp
    /** 底部导航栏高度 */
    val TabBarHeight = 64.dp
    /** 顶栏高度 */
    val AppBarHeight = 56.dp

    // ===== 开关尺寸 =====
    val SwitchWidth = 48.dp
    val SwitchHeight = 28.dp
}
