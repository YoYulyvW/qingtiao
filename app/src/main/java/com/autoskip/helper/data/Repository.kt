package com.autoskip.helper.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class Repository(
    private val ruleDao: RuleDao,
    private val logDao: LogDao,
    val prefs: Prefs
) {
    val rules: Flow<List<RuleEntity>> = ruleDao.observeAll()
    val recentLogs: Flow<List<LogEntity>> = logDao.observeRecent()
    val totalCount: Flow<Int> = logDao.observeTotal()

    /** 今日跳过次数（今天 00:00 起） */
    val todayCount: Flow<Int> = logDao.observeSince(startOfToday())

    val enabled: Flow<Boolean> = prefs.enabled
    val clickDelayMs: Flow<Long> = prefs.clickDelayMs
    val whitelistEnabled: Flow<Boolean> = prefs.whitelistEnabled
    val whitelistPkgs: Flow<Set<String>> = prefs.whitelistPkgs
    val strictClose: Flow<Boolean> = prefs.strictClose

    suspend fun addRule(rule: RuleEntity): Long = ruleDao.insert(rule)

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

    suspend fun addLog(log: LogEntity) = logDao.insert(log)
    suspend fun clearLogs() = logDao.clearAll()
    suspend fun setEnabled(v: Boolean) = prefs.setEnabled(v)
    suspend fun setClickDelay(ms: Long) = prefs.setClickDelay(ms)
    suspend fun setWhitelistEnabled(v: Boolean) = prefs.setWhitelistEnabled(v)
    suspend fun setWhitelist(pkgs: Set<String>) = prefs.setWhitelist(pkgs)
    suspend fun setStrictClose(v: Boolean) = prefs.setStrictClose(v)

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
