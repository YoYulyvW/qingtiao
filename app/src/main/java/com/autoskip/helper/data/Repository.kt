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

    /** 将某个包名加入白名单（已存在则忽略） */
    suspend fun addToWhitelist(pkg: String) {
        val cur = prefs.whitelistPkgs.first()
        if (pkg !in cur) prefs.setWhitelist(cur + pkg)
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
