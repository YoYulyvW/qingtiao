package com.autoskip.helper.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 一次跳过的记录 */
@Entity(tableName = "logs")
data class LogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 被跳过的应用包名 */
    val packageName: String,
    /** 应用名（尽力获取，可能为空） */
    val appLabel: String? = null,
    /** 命中的规则描述，如“跳过”“关闭(id=xxx)” */
    val rule: String,
    /** 命中的按钮文本 */
    val matchedText: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
