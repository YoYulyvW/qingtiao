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
            runCatching { repo.migrateCondDelayV1IfNeeded() }
            runCatching { seedDefaultRulesIfNeeded() }
            runCatching { repo.markRuleSeeded() }
        }
        // ★ 启动授权（先加载缓存，再起心跳）
        runCatching { LicenseManager.start(this) }
    }

    /**
     * 首次启动写入默认普通规则（固定 serverId 1001-1008）。
     * ★ 只做一次（由 Repository 的 ruleSeeded 标记控制）。
     */
    private suspend fun seedDefaultRulesIfNeeded() {
        val dao = database.ruleDao()
        val existingTexts = dao.all().map { it.text }.toSet()
        val existingIds = dao.all().mapNotNull { it.serverId }.toSet()
        val defaults = listOf(
            1001L to "点击免费看全集",
            1002L to "刷新",
            1003L to "跳过",
            1004L to "不再提醒",
            1005L to "清理缓存",
            1006L to "同意",
            1007L to "以后再说",
            1008L to "下次再说"
        )
        defaults.forEach { (sid, t) ->
            if (t !in existingTexts && sid !in existingIds) {
                dao.insert(RuleEntity(
                    name = t, text = t, exact = true,
                    serverId = sid, source = "server"
                ))
            }
        }
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
