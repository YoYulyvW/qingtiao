package com.autoskip.helper.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 控件动作类型 */
object WidgetAction {
    const val CLICK = "CLICK"            // 点击
    const val LONG_PRESS = "LONG_PRESS"  // 长按
    const val BACK = "BACK"              // 按返回键
    const val COORD = "COORD"            // 点击指定坐标
}

/**
 * 控件规则：按控件 ID（可加文本匹配）定位控件，执行指定动作。
 * 例：widgetId="com.ss.android.ugc.aweme:id/vfd" → 点击
 */
@Entity(tableName = "widget_rules")
data class WidgetRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remark: String,                 // 备注（这个控件是什么）
    val widgetId: String,               // 控件 ID（完整或后缀，如 vfd）
    val matchText: String? = null,      // 可选：控件 ID + 文本 双条件
    val actionType: String = WidgetAction.CLICK,
    val coordX: Int = 0,                // COORD 动作时的 X
    val coordY: Int = 0,                // COORD 动作时的 Y
    val packageName: String? = null,    // 限定包名（可选）
    val enabled: Boolean = true,
    val hitCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    /** 服务端分配 ID（null=用户自建） */
    val serverId: Long? = null,
    /** 来源：user / server */
    val source: String = "user"
)
