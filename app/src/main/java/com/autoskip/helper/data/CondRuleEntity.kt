package com.autoskip.helper.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 动作类型 */
object CondAction {
    const val CLICK_TEXT = "CLICK_TEXT"  // 点击某文字
    const val CLICK_ICON = "CLICK_ICON"  // 点击图标 X
    const val BACK = "BACK"              // 按返回键
}

/**
 * 条件规则：满足"有 + 且"两个条件时，执行指定动作。
 * 例：有"登录" 且 包含"帮助" → 点"X"（图标）
 */
@Entity(tableName = "cond_rules")
data class CondRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,                 // 规则名（便于识别）
    val hasText: String,              // 【有】屏幕上必须出现的文字
    val andText: String? = null,      // 【且】还必须包含的文字（可选）
    val actionType: String = CondAction.CLICK_ICON,  // 动作类型
    val actionText: String? = null,   // 动作参数（CLICK_TEXT 时的目标文字）
    val packageName: String? = null,  // 限定包名（可选）
    val enabled: Boolean = true,
    /** 延时检测（秒）：检测到条件后等 N 秒，仍存在才执行动作；0 = 立即执行 */
    val delaySec: Int = 3,
    val hitCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    /** 服务端分配 ID（null=用户自建） */
    val serverId: Long? = null,
    /** 来源：user / server */
    val source: String = "user"
)
