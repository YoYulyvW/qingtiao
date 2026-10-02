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

    /** 安全取字符串：JSON null 或空 → null（修复 org.json optString 把 null 变 "null" 的问题） */
    private fun JSONObject.optStr(key: String): String? {
        if (isNull(key)) return null
        val v = optString(key, "")
        return if (v.isBlank() || v == "null") null else v
    }

    /** 去重 key：普通规则 = text + packageName */
    private fun ruleKey(text: String, pkg: String) = text + "\u0001" + pkg
    /** 去重 key：条件规则 = hasText + andText + actionType + actionText */
    private fun condKey(hasText: String, andText: String, actionType: String, actionText: String) =
        hasText + "\u0001" + andText + "\u0001" + actionType + "\u0001" + actionText
    /** 去重 key：控件规则 = widgetId + matchText + actionType */
    private fun widgetKey(widgetId: String, matchText: String, actionType: String) =
        widgetId + "\u0001" + matchText + "\u0001" + actionType

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

        // 本地全部规则（用于内容去重）
        val localRules = repo.allRulesForDedup()
        val localConds = repo.allCondsForDedup()
        val localWidgets = repo.allWidgetsForDedup()
        // 等价 key 集合
        val localRuleKeys = localRules.map { ruleKey(it.text, it.packageName ?: "") }.toHashSet()
        val localCondKeys = localConds.map { condKey(it.hasText, it.andText ?: "", it.actionType, it.actionText ?: "") }.toHashSet()
        val localWidgetKeys = localWidgets.map { widgetKey(it.widgetId, it.matchText ?: "", it.actionType) }.toHashSet()

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
                    val text = content.optStr("text") ?: ""
                    val pkg = content.optStr("packageName")
                    // ★ 新增（本地无此 serverId）时做内容去重
                    if (existing == null) {
                        val k = ruleKey(text, pkg ?: "")
                        if (k in localRuleKeys) continue   // 已有等价规则 → 跳过
                        localRuleKeys.add(k)
                    }
                    toInsertRules.add(
                        RuleEntity(
                            id = existing?.id ?: 0,
                            name = content.optStr("name") ?: text,
                            text = text,
                            viewId = content.optStr("viewId"),
                            packageName = pkg,
                            exact = content.optBoolean("exact", false),
                            // ★ 保留本地 enabled（已存在时）；新增用云端
                            enabled = existing?.enabled ?: enabled,
                            learned = false,
                            serverId = serverId,
                            source = "server"
                        )
                    )
                }
                "cond" -> {
                    val existing = repo.getServerRuleByServerId("cond", serverId) as? CondRuleEntity
                    if (existing != null && existing.source == "user") continue
                    val hasText = content.optStr("hasText") ?: ""
                    val andText = content.optStr("andText")
                    val actionType = content.optStr("actionType") ?: "CLICK_ICON"
                    val actionText = content.optStr("actionText")
                    if (existing == null) {
                        val k = condKey(hasText, andText ?: "", actionType, actionText ?: "")
                        if (k in localCondKeys) continue
                        localCondKeys.add(k)
                    }
                    toInsertConds.add(
                        CondRuleEntity(
                            id = existing?.id ?: 0,
                            name = content.optStr("name") ?: "",
                            hasText = hasText,
                            andText = andText,
                            actionType = actionType,
                            actionText = actionText,
                            packageName = content.optStr("packageName"),
                            // ★ 保留本地 enabled
                            enabled = existing?.enabled ?: enabled,
                            delaySec = content.optInt("delaySec", 3),
                            serverId = serverId,
                            source = "server"
                        )
                    )
                }
                "widget" -> {
                    val existing = repo.getServerRuleByServerId("widget", serverId) as? WidgetRuleEntity
                    if (existing != null && existing.source == "user") continue
                    val widgetId = content.optStr("widgetId") ?: ""
                    val matchText = content.optStr("matchText")
                    val actionType = content.optStr("actionType") ?: "CLICK"
                    if (existing == null) {
                        val k = widgetKey(widgetId, matchText ?: "", actionType)
                        if (k in localWidgetKeys) continue
                        localWidgetKeys.add(k)
                    }
                    toInsertWidgets.add(
                        WidgetRuleEntity(
                            id = existing?.id ?: 0,
                            remark = content.optStr("remark") ?: "",
                            widgetId = widgetId,
                            matchText = matchText,
                            actionType = actionType,
                            coordX = content.optInt("coordX", 0),
                            coordY = content.optInt("coordY", 0),
                            packageName = content.optStr("packageName"),
                            // ★ 保留本地 enabled
                            enabled = existing?.enabled ?: enabled,
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
