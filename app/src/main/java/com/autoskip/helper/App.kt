package com.autoskip.helper

import android.app.Application
import com.autoskip.helper.data.AppDatabase
import com.autoskip.helper.data.Prefs
import com.autoskip.helper.data.Repository
import com.autoskip.helper.data.RuleEntity
import com.autoskip.helper.service.Matcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class App : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database by lazy { AppDatabase.get(this) }
    val prefs by lazy { Prefs(this) }
    val repo by lazy { Repository(database.ruleDao(), database.logDao(), prefs) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        appScope.launch { runCatching { repo.migrateV2IfNeeded() } }
        appScope.launch { runCatching { repo.migrateV3IfNeeded() } }
        appScope.launch { runCatching { repo.migrateV4IfNeeded() } }
        appScope.launch { runCatching { repo.seedWhitelistIfNeeded() } }
        seedDefaultRulesIfEmpty()
    }

    /** 首次启动写入一批默认规则 */
    private fun seedDefaultRulesIfEmpty() {
        appScope.launch {
            runCatching {
                val dao = database.ruleDao()
                if (dao.all().isEmpty()) {
                    Matcher.DEFAULT_TEXTS.forEach { t ->
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
