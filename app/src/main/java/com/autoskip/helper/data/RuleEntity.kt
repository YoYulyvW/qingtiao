package com.autoskip.helper.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 一条自动点击规则。
 * 匹配逻辑：在弹窗中查找文本满足 [text] 的控件，若其 [viewId] 非空则优先按 id 匹配。
 */
@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 规则名，便于用户识别 */
    val name: String,
    /** 目标文本，如“跳过”“关闭” */
    val text: String,
    /** 可选：控件 resource-id（学习模式抓到的），为空时仅按文本匹配 */
    val viewId: String? = null,
    /** 可选：限定包名，为空表示所有应用生效 */
    val packageName: String? = null,
    /** 是否精确匹配（false = 包含即匹配） */
    val exact: Boolean = false,
    /** 是否启用 */
    val enabled: Boolean = true,
    /** 是否由学习模式自动生成 */
    val learned: Boolean = false,
    /** 创建时间 */
    val createdAt: Long = System.currentTimeMillis(),
    /** 命中次数 */
    val hitCount: Int = 0
)
