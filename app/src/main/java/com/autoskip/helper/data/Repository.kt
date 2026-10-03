package com.autoskip.helper.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class Repository(
    private val db: AppDatabase,
    private val ruleDao: RuleDao,
    private val logDao: LogDao,
    private val condRuleDao: CondRuleDao,
    private val widgetRuleDao: WidgetRuleDao,
    private val deletedServerRuleDao: DeletedServerRuleDao,
    val prefs: Prefs
) {
    /** 供 RuleSync 做事务 */
    fun database(): AppDatabase = db

    // ===== 去重用查询（RuleSync 用）=====
    suspend fun allRulesForDedup(): List<RuleEntity> = ruleDao.all()
    suspend fun allCondsForDedup(): List<CondRuleEntity> = condRuleDao.all()
    suspend fun allWidgetsForDedup(): List<WidgetRuleEntity> = widgetRuleDao.all()

    /**
     * 规则同步（事务）：全量替换服务端规则。
     * 由 RuleSync 计算好"要保留的 serverId 集合 + 要插入/更新的规则"后调用。
     * 事务内：删旧 server 规则 + 写新规则 + 写墓碑（若有）一起提交，保证原子性。
     */
    suspend fun syncServerRules(
        newServerIds: Set<Long>,
        insertRules: List<RuleEntity>,
        insertConds: List<CondRuleEntity>,
        insertWidgets: List<WidgetRuleEntity>,
        newTombstones: List<Long>
    ) {
        db.withTransaction {
            // 1) 删除本地已有、但服务端已移除的 server 规则
            ruleDao.allServerRules().forEach {
                if (it.serverId != null && it.serverId !in newServerIds) ruleDao.deleteByServerId(it.serverId)
            }
            condRuleDao.allServerRules().forEach {
                if (it.serverId != null && it.serverId !in newServerIds) condRuleDao.deleteByServerId(it.serverId)
            }
            widgetRuleDao.allServerRules().forEach {
                if (it.serverId != null && it.serverId !in newServerIds) widgetRuleDao.deleteByServerId(it.serverId)
            }
            // 2) 写入/更新规则
            insertRules.forEach { ruleDao.insert(it) }
            insertConds.forEach { condRuleDao.insert(it) }
            insertWidgets.forEach { widgetRuleDao.insert(it) }
            // 3) 写墓碑
            newTombstones.forEach { deletedServerRuleDao.insert(DeletedServerRule(it)) }
        }
    }
    val rules: Flow<List<RuleEntity>> = ruleDao.observeAll()
    val condRules: Flow<List<CondRuleEntity>> = condRuleDao.observeAll()
    val widgetRules: Flow<List<WidgetRuleEntity>> = widgetRuleDao.observeAll()
    val recentLogs: Flow<List<LogEntity>> = logDao.observeRecent()
    val totalCount: Flow<Int> = logDao.observeTotal()

    /** 今日跳过次数（今天 00:00 起） */
    val todayCount: Flow<Int> = logDao.observeSince(startOfToday())

    val enabled: Flow<Boolean> = prefs.enabled
    val clickDelayMs: Flow<Long> = prefs.clickDelayMs
    val whitelistEnabled: Flow<Boolean> = prefs.whitelistEnabled
    val whitelistPkgs: Flow<Set<String>> = prefs.whitelistPkgs
    // 短剧自动3倍速
    val dramaEnabled: Flow<Boolean> = prefs.dramaEnabled
    val dramaAutoMount: Flow<Boolean> = prefs.dramaAutoMount
    val dramaIntervalMs: Flow<Long> = prefs.dramaIntervalMs
    val dramaTargetSpeed: Flow<String> = prefs.dramaTargetSpeed
    val dramaBaseW: Flow<Int> = prefs.dramaBaseW
    val dramaBaseH: Flow<Int> = prefs.dramaBaseH
    val dramaTapX: Flow<Int> = prefs.dramaTapX
    val dramaTapY: Flow<Int> = prefs.dramaTapY
    val dramaClicks: Flow<Int> = prefs.dramaClicks
    val dramaDebug: Flow<Boolean> = prefs.dramaDebug
    val dramaImgInterval: Flow<Int> = prefs.dramaImgInterval
    val dramaNormalInterval: Flow<Int> = prefs.dramaNormalInterval
    val condDramaOnly: Flow<Boolean> = prefs.condDramaOnly
    val dramaMountById: Flow<Boolean> = prefs.dramaMountById
    val dramaResumePause: Flow<Boolean> = prefs.dramaResumePause
    val pushUrl: Flow<String> = prefs.pushUrl
    val pushName: Flow<String> = prefs.pushName
    val pushUser: Flow<String> = prefs.pushUser
    val pushMsg: Flow<String> = prefs.pushMsg
    val pushToken: Flow<String> = prefs.pushToken
    val pushIncludeClone: Flow<Boolean> = prefs.pushIncludeClone
    val dramaLongPress: Flow<Boolean> = prefs.dramaLongPress
    val dramaClickSpeed: Flow<Boolean> = prefs.dramaClickSpeed
    val learnMode: Flow<Boolean> = prefs.learnMode
    val overlayToastEnabled: Flow<Boolean> = prefs.overlayToastEnabled

    suspend fun addRule(rule: RuleEntity): Long = ruleDao.insert(rule)

    // 条件规则
    suspend fun addWidgetRule(rule: WidgetRuleEntity): Long = widgetRuleDao.insert(rule)
    suspend fun updateWidgetRule(rule: WidgetRuleEntity) = widgetRuleDao.update(rule)
    suspend fun deleteWidgetRule(rule: WidgetRuleEntity) = widgetRuleDao.delete(rule)
    suspend fun enabledWidgetRules(): List<WidgetRuleEntity> = widgetRuleDao.enabledRules()
    suspend fun bumpWidgetHit(id: Long) = widgetRuleDao.bumpHit(id)

    suspend fun addCondRule(rule: CondRuleEntity): Long = condRuleDao.insert(rule)
    suspend fun updateCondRule(rule: CondRuleEntity) = condRuleDao.update(rule)
    suspend fun deleteCondRule(rule: CondRuleEntity) = condRuleDao.delete(rule)
    suspend fun bumpCondHit(id: Long) = condRuleDao.bumpHit(id)

    // ===== 服务端规则同步（墓碑 + 查询）=====
    suspend fun allDeletedServerIds(): Set<Long> = deletedServerRuleDao.allServerIds().toSet()
    suspend fun addDeletedServerId(serverId: Long) = deletedServerRuleDao.insert(DeletedServerRule(serverId))
    suspend fun allServerRuleIds(): Set<Long> {
        val a = ruleDao.allServerRules().mapNotNull { it.serverId }
        val b = condRuleDao.allServerRules().mapNotNull { it.serverId }
        val c = widgetRuleDao.allServerRules().mapNotNull { it.serverId }
        return (a + b + c).toSet()
    }
    suspend fun deleteServerRuleByServerId(type: String, serverId: Long) {
        when (type) {
            "rule" -> ruleDao.deleteByServerId(serverId)
            "cond" -> condRuleDao.deleteByServerId(serverId)
            "widget" -> widgetRuleDao.deleteByServerId(serverId)
        }
    }
    suspend fun getServerRuleByServerId(type: String, serverId: Long): Any? = when (type) {
        "rule" -> ruleDao.getByServerId(serverId)
        "cond" -> condRuleDao.getByServerId(serverId)
        "widget" -> widgetRuleDao.getByServerId(serverId)
        else -> null
    }

    /** 内置规则是否已 seed 过 */
    suspend fun markRuleSeeded() = prefs.markRuleSeeded()

    /** 学习模式：若同文本+同包名的规则不存在，则新增一条学习规则 */
    suspend fun addLearnedRuleIfAbsent(text: String, viewId: String?, pkg: String) {
        val exists = ruleDao.all().any { it.text == text && it.packageName == pkg }
        if (!exists) {
            ruleDao.insert(
                RuleEntity(
                    name = text,
                    text = text,
                    viewId = viewId,
                    packageName = pkg,
                    learned = true
                )
            )
        }
    }
    suspend fun updateRule(rule: RuleEntity) = ruleDao.update(rule)
    suspend fun deleteRule(rule: RuleEntity) = ruleDao.delete(rule)
    suspend fun bumpHit(id: Long) = ruleDao.bumpHit(id)
    suspend fun enabledRules(): List<RuleEntity> = ruleDao.enabledRules()

    /**
     * 首次启动预置内置条件规则（固定 serverId 2001-2005）。
     * ★ 只做一次（标记位 ruleSeeded），避免云端删除后被重新 seed。
     * ★ 使用固定 serverId，便于云端同 ID 覆盖。
     */
    suspend fun seedCondRulesIfNeeded() {
        if (prefs.ruleSeeded.first()) return
        // 清理历史错误命名的内置规则（"分享到日常" → "转发到日常"）
        condRuleDao.all().forEach { r ->
            if (r.name == "推荐+分享到日常/举报→返回") condRuleDao.delete(r)
        }
        val existingByName = condRuleDao.all().map { it.name }.toSet()
        val existingIds = condRuleDao.all().mapNotNull { it.serverId }.toSet()
        val defaults = listOf(
            CondRuleEntity(
                serverId = 2001L,
                name = "登录+自动注册/+86→返回",
                hasText = "登录",
                andText = "自动注册|+86",
                actionType = CondAction.BACK,
                delaySec = 2,
                source = "server"
            ),
            CondRuleEntity(
                serverId = 2002L,
                name = "登录+自动注册/帮助→返回",
                hasText = "登录",
                andText = "自动注册|帮助",
                actionType = CondAction.BACK,
                delaySec = 3,
                source = "server"
            ),
            CondRuleEntity(
                serverId = 2003L,
                name = "推荐+转发到日常/举报→返回",
                hasText = "推荐",
                andText = "转发到日常|举报",
                actionType = CondAction.BACK,
                delaySec = 3,
                source = "server"
            ),
            CondRuleEntity(
                serverId = 2004L,
                name = "关注+作品/粉丝→返回",
                hasText = "关注",
                andText = "作品|粉丝",
                actionType = CondAction.BACK,
                delaySec = 2,
                source = "server"
            ),
            CondRuleEntity(
                serverId = 2005L,
                name = "关注+刚刚看过→返回",
                hasText = "关注",
                andText = "刚刚看过",
                actionType = CondAction.BACK,
                delaySec = 3,
                source = "server"
            )
        )
        defaults.forEach { r ->
            if (r.name !in existingByName && r.serverId !in existingIds) {
                condRuleDao.insert(r)
            }
        }
    }

    /**
     * 条件规则 delaySec 覆盖迁移 v1：
     * 用最新默认延时覆盖同名内置规则的 delaySec（用户手动改过的名字不同则不动）。
     * 目标：把 3 条内置规则的延时从旧值更新到新值。
     */
    suspend fun migrateCondDelayV1IfNeeded() {
        if (prefs.migratedCondDelayV1.first()) return
        val overrides = mapOf(
            "登录+自动注册/+86→返回" to 2,
            "关注+作品/粉丝→返回" to 2,
            "关注+刚刚看过→返回" to 3
        )
        condRuleDao.all().forEach { r ->
            val newDelay = overrides[r.name] ?: return@forEach
            if (r.delaySec != newDelay) {
                condRuleDao.update(r.copy(delaySec = newDelay))
            }
        }
        prefs.markMigratedCondDelayV1()
    }

    /**
     * 条件规则 v1 迁移：删除旧版内置条件规则（7 条），保留用户自建。
     */
    suspend fun migrateCondRulesV1() {
        if (prefs.migratedCondV1.first()) return
        val oldNames = setOf(
            "登录框→返回", "关注页→返回",
            "登录→点X", "验证码→点X", "注册→点X",
            "登录上下文→点关闭", "登录上下文→点close"
        )
        condRuleDao.all().forEach { r ->
            if (r.name in oldNames) condRuleDao.delete(r)
        }
        prefs.markMigratedCondV1()
    }

    suspend fun addLog(log: LogEntity) = logDao.insert(log)
    suspend fun clearLogs() = logDao.clearAll()
    suspend fun setEnabled(v: Boolean) = prefs.setEnabled(v)
    suspend fun setClickDelay(ms: Long) = prefs.setClickDelay(ms)
    suspend fun setWhitelistEnabled(v: Boolean) = prefs.setWhitelistEnabled(v)
    suspend fun setWhitelist(pkgs: Set<String>) = prefs.setWhitelist(pkgs)
    /** 白名单原子翻转（true=加入，false=移除） */
    suspend fun toggleWhitelistPkgAtomically(pkg: String): Boolean {
        var added = false
        prefs.toggleWhitelistPkg(pkg) { added = it }
        return added
    }
    /** 白名单原子移除 */
    suspend fun removeWhitelistPkgAtomically(pkg: String) {
        prefs.removeWhitelistPkg(pkg)
    }
    suspend fun setDramaEnabled(v: Boolean) = prefs.setDramaEnabled(v)
    suspend fun setDramaAutoMount(v: Boolean) = prefs.setDramaAutoMount(v)
    suspend fun setDramaInterval(ms: Long) = prefs.setDramaInterval(ms)
    suspend fun setDramaTargetSpeed(s: String) = prefs.setDramaTargetSpeed(s)
    suspend fun setDramaBaseW(v: Int) = prefs.setDramaBaseW(v)
    suspend fun setDramaBaseH(v: Int) = prefs.setDramaBaseH(v)
    suspend fun setDramaTapX(v: Int) = prefs.setDramaTapX(v)
    suspend fun setDramaTapY(v: Int) = prefs.setDramaTapY(v)
    suspend fun setDramaClicks(v: Int) = prefs.setDramaClicks(v)
    suspend fun setDramaDebug(v: Boolean) = prefs.setDramaDebug(v)
    suspend fun setDramaImgInterval(v: Int) = prefs.setDramaImgInterval(v)
    suspend fun setDramaNormalInterval(v: Int) = prefs.setDramaNormalInterval(v)
    val dramaStuckSec: Flow<Int> = prefs.dramaStuckSec
    suspend fun setDramaStuckSec(v: Int) = prefs.setDramaStuckSec(v)
    suspend fun setDramaLongPress(v: Boolean) = prefs.setDramaLongPress(v)
    suspend fun setDramaMountById(v: Boolean) = prefs.setDramaMountById(v)
    suspend fun setDramaResumePause(v: Boolean) = prefs.setDramaResumePause(v)
    suspend fun setPushUrl(v: String) = prefs.setPushUrl(v)
    suspend fun setPushName(v: String) = prefs.setPushName(v)
    suspend fun setPushUser(v: String) = prefs.setPushUser(v)
    suspend fun setPushMsg(v: String) = prefs.setPushMsg(v)
    suspend fun setPushToken(v: String) = prefs.setPushToken(v)
    suspend fun setPushIncludeClone(v: Boolean) = prefs.setPushIncludeClone(v)
    suspend fun setCondDramaOnly(v: Boolean) = prefs.setCondDramaOnly(v)

    /**
     * 导出条件规则为文本，每行一条：
     * 规则名|有|且|动作|动作文字|延时
     * 动作：返回 / 点图标X / 点文字
     */
    suspend fun exportCondRules(): String {
        val actLabel: (String) -> String = {
            when (it) {
                CondAction.BACK -> "返回"
                CondAction.CLICK_ICON -> "点图标X"
                CondAction.CLICK_TEXT -> "点文字"
                else -> it
            }
        }
        return condRuleDao.all().joinToString("\n") { r ->
            listOf(
                r.name,
                r.hasText,
                r.andText ?: "",
                actLabel(r.actionType),
                r.actionText ?: "",
                r.delaySec.toString()
            ).joinToString("|")
        }
    }

    /**
     * 导入条件规则。
     * @param text 每行一条：规则名|有|且|动作|动作文字|延时
     * @param clearFirst 是否先清空原有规则
     * @return 结果描述
     */
    suspend fun importCondRules(text: String, clearFirst: Boolean): String {
        if (clearFirst) {
            condRuleDao.all().forEach { condRuleDao.delete(it) }
        }
        val existing = condRuleDao.all().map { it.name }.toHashSet()
        var added = 0
        var skipped = 0
        var failed = 0
        text.split("\n").forEach { raw ->
            val line = raw.trim()
            if (line.isBlank() || line.startsWith("#")) return@forEach
            val parts = line.split("|")
            if (parts.size < 2) { failed++; return@forEach }
            val name = parts.getOrNull(0)?.trim().orEmpty()
            val hasText = parts.getOrNull(1)?.trim().orEmpty()
            if (name.isBlank() || hasText.isBlank()) { failed++; return@forEach }
            if (!clearFirst && name in existing) { skipped++; return@forEach }
            val andText = parts.getOrNull(2)?.trim().orEmpty().ifBlank { null }
            val actionLabel = parts.getOrNull(3)?.trim().orEmpty()
            val actionType = when {
                actionLabel.contains("图标") -> CondAction.CLICK_ICON
                actionLabel.contains("文字") -> CondAction.CLICK_TEXT
                actionLabel.contains("返回") -> CondAction.BACK
                actionLabel.isBlank() -> CondAction.CLICK_ICON
                else -> actionLabel
            }
            val actionText = parts.getOrNull(4)?.trim().orEmpty().ifBlank { null }
            val delay = parts.getOrNull(5)?.trim()?.toIntOrNull()?.coerceIn(0, 60) ?: 0
            runCatching {
                condRuleDao.insert(
                    CondRuleEntity(
                        name = name, hasText = hasText, andText = andText,
                        actionType = actionType, actionText = actionText, delaySec = delay
                    )
                )
            }.onSuccess { added++ }.onFailure { failed++ }
            existing.add(name)
        }
        return "导入完成：新增 $added，跳过 $skipped，失败 $failed"
    }
    suspend fun setDramaClickSpeed(v: Boolean) = prefs.setDramaClickSpeed(v)
    suspend fun setLearnMode(v: Boolean) = prefs.setLearnMode(v)
    suspend fun setOverlayToastEnabled(v: Boolean) = prefs.setOverlayToastEnabled(v)

    /**
     * v4 迁移：删除"倍速"类规则（1x/1.25x/1.5x/2x/3x 等），
     * 避免与"短剧自动倍速"功能冲突。只删非学习规则。
     */
    suspend fun migrateV4IfNeeded() {
        if (prefs.migratedV4.first()) return
        val speedRegex = Regex("^[0-9]+(\\.[0-9]+)?[xX]$")
        ruleDao.all().forEach { r ->
            if (!r.learned && speedRegex.matches(r.text.trim())) {
                ruleDao.delete(r)
            }
        }
        prefs.markMigratedV4()
    }

    /**
     * v3 迁移：把旧版内置默认规则替换成新规则列表（全部精确匹配）。
     * 只删除"旧内置"的规则，保留用户自定义规则与学习规则。
     */
    suspend fun migrateV3IfNeeded() {
        if (prefs.migratedV3.first()) return
        val oldDefaults = setOf(
            "跳过", "下次再说", "不再提醒", "以后再说", "暂不",
            "我知道了", "知道了", "残忍拒绝", "稍后再说", "取消",
            "跳过广告", "点击跳过", "skip"
        )
        val newDefaults = listOf(
            "点击免费看全集", "刷新", "跳过", "不再提醒", "清理缓存",
            "同意", "以后再说", "下次再说"
        )
        val existing = ruleDao.all()
        // 删除旧的、非学习的内置规则
        existing.forEach { r ->
            if (!r.learned && r.text in oldDefaults && r.text !in newDefaults) {
                ruleDao.delete(r)
            }
        }
        // 补齐新规则（精确匹配，避免重复）
        val nowTexts = ruleDao.all().map { it.text }.toSet()
        newDefaults.forEach { t ->
            if (t !in nowTexts) {
                ruleDao.insert(RuleEntity(name = t, text = t, exact = true))
            }
        }
        // 把仍存在的旧规则改为精确匹配
        ruleDao.all().forEach { r ->
            if (!r.learned && r.text in newDefaults && !r.exact) {
                ruleDao.update(r.copy(exact = true))
            }
        }
        prefs.markMigratedV3()
    }

    /**
     * v2 迁移：一次性移除历史版本内置的"关闭"/"close"/"x"等默认规则，
     * 避免它们与新的严格模式重复或误触。用户自定义的规则不受影响。
     */
    suspend fun migrateV2IfNeeded() {
        if (prefs.migratedV2.first()) return
        val legacy = setOf("关闭", "close", "×", "✕", "✖", "x")
        ruleDao.all().forEach { r ->
            if (r.learned) return@forEach
            if (r.text.trim().lowercase() in legacy.map { it.lowercase() }) {
                ruleDao.delete(r)
            }
        }
        prefs.markMigratedV2()
    }

    /** 将某个包名加入白名单（原子操作，避免读-改-写竞态） */
    suspend fun addToWhitelist(pkg: String) {
        prefs.addWhitelistPkg(pkg)
    }

    /**
     * 首次启动预置默认白名单：抖音及其所有分身。
     * 抖音分身包名形如 com.qihoo.magic.xxxx_110 / _2 / _3 ...
     * 用前缀通配 * 一次覆盖全部分身。
     */
    suspend fun seedWhitelistIfNeeded() {
        if (prefs.whitelistSeeded.first()) return
        val defaults = setOf(
            "com.ss.android.ugc.aweme",   // 抖音主包名（官方）
            "com.ss.android.ugc.aweme.lite", // 抖音极速版
            "com.qihoo.magic.*",          // 360分身大师：抖音的所有分身
            "com.douyin.*"                // 其他分身工具生成的抖音
        )
        val cur = prefs.whitelistPkgs.first()
        prefs.setWhitelist(cur + defaults)
        // 默认开启"仅白名单生效"，避免误点系统 UI
        prefs.setWhitelistEnabled(true)
        prefs.markWhitelistSeeded()
    }

    private fun startOfToday(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
