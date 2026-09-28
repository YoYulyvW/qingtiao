package com.autoskip.helper.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class Repository(
    private val ruleDao: RuleDao,
    private val logDao: LogDao,
    private val condRuleDao: CondRuleDao,
    val prefs: Prefs
) {
    val rules: Flow<List<RuleEntity>> = ruleDao.observeAll()
    val condRules: Flow<List<CondRuleEntity>> = condRuleDao.observeAll()
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
    val dramaLongPress: Flow<Boolean> = prefs.dramaLongPress
    val dramaClickSpeed: Flow<Boolean> = prefs.dramaClickSpeed

    suspend fun addRule(rule: RuleEntity): Long = ruleDao.insert(rule)

    // 条件规则
    suspend fun addCondRule(rule: CondRuleEntity): Long = condRuleDao.insert(rule)
    suspend fun updateCondRule(rule: CondRuleEntity) = condRuleDao.update(rule)
    suspend fun deleteCondRule(rule: CondRuleEntity) = condRuleDao.delete(rule)
    suspend fun bumpCondHit(id: Long) = condRuleDao.bumpHit(id)

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
     * 首次启动预置内置条件规则（逐条判重，避免重复）。
     */
    suspend fun seedCondRulesIfNeeded() {
        val existing = condRuleDao.all().map { it.name }.toSet()
        val defaults = listOf(
            CondRuleEntity(
                name = "登录+自动注册/+86→返回",
                hasText = "登录",
                andText = "自动注册|+86",
                actionType = CondAction.BACK,
                delaySec = 0
            ),
            CondRuleEntity(
                name = "登录+自动注册/帮助→返回",
                hasText = "登录",
                andText = "自动注册|帮助",
                actionType = CondAction.BACK,
                delaySec = 3
            ),
            CondRuleEntity(
                name = "推荐+分享到日常/举报→返回",
                hasText = "推荐",
                andText = "转发到日常|举报",
                actionType = CondAction.BACK,
                delaySec = 3
            ),
            CondRuleEntity(
                name = "关注+作品/粉丝→返回",
                hasText = "关注",
                andText = "作品|粉丝",
                actionType = CondAction.BACK,
                delaySec = 0
            ),
            CondRuleEntity(
                name = "关注+刚刚看过→返回",
                hasText = "关注",
                andText = "刚刚看过",
                actionType = CondAction.BACK,
                delaySec = 0
            )
        )
        defaults.forEach { r ->
            if (r.name !in existing) condRuleDao.insert(r)
        }
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
    suspend fun setDramaLongPress(v: Boolean) = prefs.setDramaLongPress(v)
    suspend fun setCondDramaOnly(v: Boolean) = prefs.setCondDramaOnly(v)
    suspend fun setDramaClickSpeed(v: Boolean) = prefs.setDramaClickSpeed(v)

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

    /** 将某个包名加入白名单（已存在则忽略） */
    suspend fun addToWhitelist(pkg: String) {
        val cur = prefs.whitelistPkgs.first()
        if (pkg !in cur) prefs.setWhitelist(cur + pkg)
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
