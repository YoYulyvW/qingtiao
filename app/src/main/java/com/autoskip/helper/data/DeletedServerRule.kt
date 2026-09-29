package com.autoskip.helper.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 墓碑：用户删除过的服务端规则（按 serverId 记录）。
 * 服务端下次下发时跳过这些 serverId，避免"删了又回来"。
 */
@Entity(tableName = "deleted_server_rules")
data class DeletedServerRule(
    @PrimaryKey val serverId: Long,
    val deletedAt: Long = System.currentTimeMillis()
)
