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
        seedDefaultRulesIfEmpty()
    }

    /** 首次启动写入一批默认规则 */
    private fun seedDefaultRulesIfEmpty() {
        appScope.launch {
            runCatching {
                val dao = database.ruleDao()
                if (dao.all().isEmpty()) {
                    Matcher.DEFAULT_TEXTS.forEach { t ->
                        dao.insert(RuleEntity(name = t, text = t))
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
