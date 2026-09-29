package com.autoskip.helper.license

import android.content.Context
import android.util.Log
import com.autoskip.helper.App
import com.autoskip.helper.data.CondRuleEntity
import com.autoskip.helper.data.RuleEntity
import com.autoskip.helper.data.WidgetRuleEntity
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/**
 * 服务端规则同步。
 *
 * 流程：
 * ① 拉取 /rules/get（全量）
 * ② 内存合并（按 serverId 匹配 + 墓碑跳过 + 本地优先）
 * ③ 事务写入 Room（规则 + 墓碑一起）
 * ④ 成功才更新本地 rulesVersion
 */
object RuleSync {
    private const val TAG = "RuleSync"

    /**
     * 全量同步。
     * @return 成功与否
     */
    suspend fun sync(ctx: Context): Boolean {
        val app = ctx.applicationContext as App
        val repo = app.repo
        val p = LicensePrefs(ctx.applicationContext)
        val base = p.baseUrl.first()
        val deviceId = LicenseManager.deviceId(ctx)
        if (deviceId.isBlank()) return false

        // ① 拉取
        val res = LicenseClient.fetchRules(base, deviceId)
        if (!res.ok || res.rawJson.isBlank()) {
            Log.w(TAG, "拉取规则失败")
            return false
        }

        // ② 内存合并
        val tombstones = repo.allDeletedServerIds()
        val remoteIds = HashSet<Long>()
        val toInsertRules = ArrayList<RuleEntity>()
        val toInsertConds = ArrayList<CondRuleEntity>()
        val toInsertWidgets = ArrayList<WidgetRuleEntity>()

        val arr = try {
            JSONObject(res.rawJson).optJSONArray("rules") ?: JSONArray()
        } catch (e: Exception) {
            Log.e(TAG, "解析规则失败", e)
            return false
        }

        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val serverId = o.optLong("serverId", 0)
            if (serverId <= 0) continue
            remoteIds.add(serverId)
            if (serverId in tombstones) continue   // 墓碑命中 → 跳过

            val type = o.optString("type", "")
            val enabled = o.optBoolean("enabled", true)
            val content = o.optJSONObject("content") ?: continue

            when (type) {
                "rule" -> {
                    val existing = repo.getServerRuleByServerId("rule", serverId) as? RuleEntity
                    if (existing != null && existing.source == "user") continue  // 本地改过，优先
                    toInsertRules.add(
                        RuleEntity(
                            id = existing?.id ?: 0,
                            name = content.optString("name", content.optString("text", "")),
                            text = content.optString("text", ""),
                            viewId = content.optString("viewId", "").ifBlank { null },
                            packageName = content.optString("packageName", "").ifBlank { null },
                            exact = content.optBoolean("exact", false),
                            enabled = enabled,
                            learned = false,
                            serverId = serverId,
                            source = "server"
                        )
                    )
                }
                "cond" -> {
                    val existing = repo.getServerRuleByServerId("cond", serverId) as? CondRuleEntity
                    if (existing != null && existing.source == "user") continue
                    toInsertConds.add(
                        CondRuleEntity(
                            id = existing?.id ?: 0,
                            name = content.optString("name", ""),
                            hasText = content.optString("hasText", ""),
                            andText = content.optString("andText", "").ifBlank { null },
                            actionType = content.optString("actionType", "CLICK_ICON"),
                            actionText = content.optString("actionText", "").ifBlank { null },
                            packageName = content.optString("packageName", "").ifBlank { null },
                            enabled = enabled,
                            delaySec = content.optInt("delaySec", 3),
                            serverId = serverId,
                            source = "server"
                        )
                    )
                }
                "widget" -> {
                    val existing = repo.getServerRuleByServerId("widget", serverId) as? WidgetRuleEntity
                    if (existing != null && existing.source == "user") continue
                    toInsertWidgets.add(
                        WidgetRuleEntity(
                            id = existing?.id ?: 0,
                            remark = content.optString("remark", ""),
                            widgetId = content.optString("widgetId", ""),
                            matchText = content.optString("matchText", "").ifBlank { null },
                            actionType = content.optString("actionType", "CLICK"),
                            coordX = content.optInt("coordX", 0),
                            coordY = content.optInt("coordY", 0),
                            packageName = content.optString("packageName", "").ifBlank { null },
                            enabled = enabled,
                            serverId = serverId,
                            source = "server"
                        )
                    )
                }
            }
        }

        // ③ 事务写入
        try {
            repo.syncServerRules(remoteIds, toInsertRules, toInsertConds, toInsertWidgets, emptyList())
        } catch (e: Exception) {
            Log.e(TAG, "同步事务失败", e)
            return false
        }

        // ④ 成功才更新版本
        p.setRulesVersion(res.version)
        Log.i(TAG, "规则同步完成 version=" + res.version + " 共 " + remoteIds.size + " 条")
        return true
    }
}
