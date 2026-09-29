package com.autoskip.helper

import android.app.Application
import com.autoskip.helper.data.AppDatabase
import com.autoskip.helper.data.Prefs
import com.autoskip.helper.data.Repository
import com.autoskip.helper.data.RuleEntity
import com.autoskip.helper.license.LicenseManager
import com.autoskip.helper.service.Matcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class App : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database by lazy { AppDatabase.get(this) }
    val prefs by lazy { Prefs(this) }
    val repo by lazy { Repository(database, database.ruleDao(), database.logDao(), database.condRuleDao(), database.widgetRuleDao(), database.deletedServerRuleDao(), prefs) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        appScope.launch { runCatching { repo.migrateV2IfNeeded() } }
        appScope.launch { runCatching { repo.migrateV3IfNeeded() } }
        appScope.launch { runCatching { repo.migrateV4IfNeeded() } }
        appScope.launch { runCatching { repo.seedWhitelistIfNeeded() } }
        seedDefaultRulesIfEmpty()
        appScope.launch {
            runCatching { repo.migrateCondRulesV1() }
            runCatching { repo.seedCondRulesIfNeeded() }
        }
        // ★ 启动授权（先加载缓存，再起心跳）
        runCatching { LicenseManager.start(this) }
    }

    /**
     * 首次启动写入默认规则。
     * 注意：逐条判重（不能只看"库是否为空"），避免与迁移协程并发执行时重复插入。
     */
    private fun seedDefaultRulesIfEmpty() {
        appScope.launch {
            runCatching {
                val dao = database.ruleDao()
                val existing = dao.all().map { it.text }.toSet()
                Matcher.DEFAULT_TEXTS.forEach { t ->
                    if (t !in existing) {
                        // 内置规则：精确匹配
                        dao.insert(RuleEntity(name = t, text = t, exact = true))
                    }
                }
            }
        }
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
