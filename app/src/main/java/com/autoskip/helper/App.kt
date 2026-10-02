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
        // ★ 所有迁移/种子串行化（同一协程按顺序执行），避免并发读写旧快照导致结果不确定
        appScope.launch {
            runCatching { repo.migrateV2IfNeeded() }
            runCatching { repo.migrateV3IfNeeded() }
            runCatching { repo.migrateV4IfNeeded() }
            runCatching { repo.seedWhitelistIfNeeded() }
            runCatching { repo.migrateCondRulesV1() }
            runCatching { repo.seedCondRulesIfNeeded() }
            runCatching { seedDefaultRulesIfEmptyInternal() }
        }
        // ★ 启动授权（先加载缓存，再起心跳）
        runCatching { LicenseManager.start(this) }
    }

    /**
     * 首次启动写入默认规则（挂起版，由迁移协程串行调用）。
     * 注意：逐条判重，避免与迁移串行执行时重复插入。
     */
    private suspend fun seedDefaultRulesIfEmptyInternal() {
        val dao = database.ruleDao()
        val existing = dao.all().map { it.text }.toSet()
        Matcher.DEFAULT_TEXTS.forEach { t ->
            if (t !in existing) {
                // 内置规则：精确匹配
                dao.insert(RuleEntity(name = t, text = t, exact = true))
            }
        }
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
