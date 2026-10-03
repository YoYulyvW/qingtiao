package com.autoskip.helper.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autoskip.helper.App
import com.autoskip.helper.data.RuleEntity
import com.autoskip.helper.service.AutoClickAccessibilityService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as App).repo

    val rules = repo.rules.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val condRules = repo.condRules.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val logs = repo.recentLogs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val totalCount = repo.totalCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val todayCount = repo.todayCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val enabled = repo.enabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val clickDelay = repo.clickDelayMs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 600L)
    val whitelistEnabled = repo.whitelistEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val whitelistPkgs = repo.whitelistPkgs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    // 短剧自动倍速
    val dramaEnabled = repo.dramaEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val dramaAutoMount = repo.dramaAutoMount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val dramaIntervalMs = repo.dramaIntervalMs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1000L)
    val dramaTargetSpeed = repo.dramaTargetSpeed.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "3x")
    val dramaDebug = repo.dramaDebug.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val dramaImgInterval = repo.dramaImgInterval.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)
    val dramaNormalInterval = repo.dramaNormalInterval.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 5)
    val dramaStuckSec = repo.dramaStuckSec.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 180)
    val dramaLongPress = repo.dramaLongPress.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val dramaMountById = repo.dramaMountById.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val dramaResumePause = repo.dramaResumePause.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val pushUrl = repo.pushUrl.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val pushName = repo.pushName.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val pushUser = repo.pushUser.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val pushMsg = repo.pushMsg.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "出现了广告窗口，请注意查看")
    val pushToken = repo.pushToken.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val pushIncludeClone = repo.pushIncludeClone.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val condDramaOnly = repo.condDramaOnly.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val dramaClickSpeed = repo.dramaClickSpeed.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // ★ 学习模式从 DataStore 读取（持久化，重启恢复）
    val learnMode: StateFlow<Boolean> = repo.learnMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // ★ 识别提示悬浮窗开关
    val overlayToastEnabled = repo.overlayToastEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    fun setOverlayToastEnabled(v: Boolean) = viewModelScope.launch { repo.setOverlayToastEnabled(v) }

    fun setEnabled(v: Boolean) = viewModelScope.launch { repo.setEnabled(v) }

    fun setClickDelay(ms: Long) = viewModelScope.launch { repo.setClickDelay(ms) }

    // ===== 普通规则（门控 RULE_NORMAL）=====
    fun addRule(rule: RuleEntity) = viewModelScope.launch {
        if (!com.autoskip.helper.license.FeatureGate.isEnabled(com.autoskip.helper.license.FeatureGate.Feat.RULE_NORMAL)) return@launch
        repo.addRule(rule)
    }
    fun updateRule(rule: RuleEntity) = viewModelScope.launch {
        if (!com.autoskip.helper.license.FeatureGate.isEnabled(com.autoskip.helper.license.FeatureGate.Feat.RULE_NORMAL)) return@launch
        // ★ 用户改服务端规则 → 转为 user（脱离管控）
        val r = if (rule.source == "server") rule.copy(source = "user") else rule
        repo.updateRule(r)
    }
    fun deleteRule(rule: RuleEntity) = viewModelScope.launch {
        if (!com.autoskip.helper.license.FeatureGate.isEnabled(com.autoskip.helper.license.FeatureGate.Feat.RULE_NORMAL)) return@launch
        // ★ 删服务端规则 → 记墓碑，防复活
        if (rule.source == "server" && rule.serverId != null) repo.addDeletedServerId(rule.serverId)
        repo.deleteRule(rule)
    }

    // ===== 条件规则（门控 RULE_COND）=====
    fun addCondRule(rule: com.autoskip.helper.data.CondRuleEntity) = viewModelScope.launch {
        if (!com.autoskip.helper.license.FeatureGate.isEnabled(com.autoskip.helper.license.FeatureGate.Feat.RULE_COND)) return@launch
        repo.addCondRule(rule)
    }
    fun updateCondRule(rule: com.autoskip.helper.data.CondRuleEntity) = viewModelScope.launch {
        if (!com.autoskip.helper.license.FeatureGate.isEnabled(com.autoskip.helper.license.FeatureGate.Feat.RULE_COND)) return@launch
        val r = if (rule.source == "server") rule.copy(source = "user") else rule
        repo.updateCondRule(r)
    }
    fun deleteCondRule(rule: com.autoskip.helper.data.CondRuleEntity) = viewModelScope.launch {
        if (!com.autoskip.helper.license.FeatureGate.isEnabled(com.autoskip.helper.license.FeatureGate.Feat.RULE_COND)) return@launch
        if (rule.source == "server" && rule.serverId != null) repo.addDeletedServerId(rule.serverId)
        repo.deleteCondRule(rule)
    }

    // ===== 控件规则（门控 RULE_WIDGET）=====
    val widgetRules = repo.widgetRules.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun addWidgetRule(rule: com.autoskip.helper.data.WidgetRuleEntity) = viewModelScope.launch {
        if (!com.autoskip.helper.license.FeatureGate.isEnabled(com.autoskip.helper.license.FeatureGate.Feat.RULE_WIDGET)) return@launch
        repo.addWidgetRule(rule)
    }
    fun updateWidgetRule(rule: com.autoskip.helper.data.WidgetRuleEntity) = viewModelScope.launch {
        if (!com.autoskip.helper.license.FeatureGate.isEnabled(com.autoskip.helper.license.FeatureGate.Feat.RULE_WIDGET)) return@launch
        val r = if (rule.source == "server") rule.copy(source = "user") else rule
        repo.updateWidgetRule(r)
    }
    fun deleteWidgetRule(rule: com.autoskip.helper.data.WidgetRuleEntity) = viewModelScope.launch {
        if (!com.autoskip.helper.license.FeatureGate.isEnabled(com.autoskip.helper.license.FeatureGate.Feat.RULE_WIDGET)) return@launch
        if (rule.source == "server" && rule.serverId != null) repo.addDeletedServerId(rule.serverId)
        repo.deleteWidgetRule(rule)
    }

    fun clearLogs() = viewModelScope.launch { repo.clearLogs() }

    fun setWhitelistEnabled(v: Boolean) = viewModelScope.launch { repo.setWhitelistEnabled(v) }


    fun setDramaEnabled(v: Boolean) = viewModelScope.launch { repo.setDramaEnabled(v) }
    fun setDramaAutoMount(v: Boolean) = viewModelScope.launch { repo.setDramaAutoMount(v) }
    fun setDramaInterval(ms: Long) = viewModelScope.launch { repo.setDramaInterval(ms) }
    fun setDramaTargetSpeed(s: String) = viewModelScope.launch { repo.setDramaTargetSpeed(s) }
    fun setDramaDebug(v: Boolean) = viewModelScope.launch { repo.setDramaDebug(v) }
    fun setDramaLongPress(v: Boolean) = viewModelScope.launch { repo.setDramaLongPress(v) }
    fun setDramaMountById(v: Boolean) = viewModelScope.launch { repo.setDramaMountById(v) }
    fun setDramaResumePause(v: Boolean) = viewModelScope.launch { repo.setDramaResumePause(v) }
    /** 一次性读取推送配置（用 first() 拿 DataStore 真实值，避开 stateIn 初始空值） */
    suspend fun loadPushConfig(): List<String> = listOf(
        repo.pushUrl.first(),
        repo.pushName.first(),
        repo.pushUser.first(),
        repo.pushMsg.first(),
        repo.pushToken.first()
    )
    suspend fun loadPushIncludeClone(): Boolean = repo.pushIncludeClone.first()

    fun setPushUrl(v: String) = viewModelScope.launch { repo.setPushUrl(v) }
    fun setPushName(v: String) = viewModelScope.launch { repo.setPushName(v) }
    fun setPushUser(v: String) = viewModelScope.launch { repo.setPushUser(v) }
    fun setPushMsg(v: String) = viewModelScope.launch { repo.setPushMsg(v) }
    fun setPushToken(v: String) = viewModelScope.launch { repo.setPushToken(v) }
    fun setPushIncludeClone(v: Boolean) = viewModelScope.launch { repo.setPushIncludeClone(v) }
    fun setCondDramaOnly(v: Boolean) = viewModelScope.launch { repo.setCondDramaOnly(v) }

    fun exportCondRules(onResult: (String) -> Unit) = viewModelScope.launch {
        onResult(repo.exportCondRules())
    }

    fun importCondRules(text: String, clearFirst: Boolean, onResult: (String) -> Unit) = viewModelScope.launch {
        onResult(repo.importCondRules(text, clearFirst))
    }
    fun setDramaClickSpeed(v: Boolean) = viewModelScope.launch { repo.setDramaClickSpeed(v) }
    fun setDramaImgInterval(v: Int) = viewModelScope.launch { repo.setDramaImgInterval(v) }
    fun setDramaNormalInterval(v: Int) = viewModelScope.launch { repo.setDramaNormalInterval(v) }
    fun setDramaStuckSec(v: Int) = viewModelScope.launch { repo.setDramaStuckSec(v) }

    // ===== 白名单（门控 WHITELIST；原子操作防并发丢更新）=====
    fun toggleWhitelistPkg(pkg: String) = viewModelScope.launch {
        if (!com.autoskip.helper.license.FeatureGate.isEnabled(com.autoskip.helper.license.FeatureGate.Feat.WHITELIST)) return@launch
        repo.toggleWhitelistPkgAtomically(pkg)
    }

    fun removeFromWhitelist(pkg: String) = viewModelScope.launch {
        if (!com.autoskip.helper.license.FeatureGate.isEnabled(com.autoskip.helper.license.FeatureGate.Feat.WHITELIST)) return@launch
        repo.removeWhitelistPkgAtomically(pkg)
    }

    /** 添加一条自定义通配/包名规则（原子） */
    fun addWhitelistPattern(pattern: String) = viewModelScope.launch {
        if (!com.autoskip.helper.license.FeatureGate.isEnabled(com.autoskip.helper.license.FeatureGate.Feat.WHITELIST)) return@launch
        val p = pattern.trim()
        if (p.isBlank()) return@launch
        repo.addToWhitelist(p)
    }

    /** 学习模式：开启后，用户在其他 App 手动点击的按钮会被自动记录为规则 */
    fun setLearnMode(on: Boolean) = viewModelScope.launch {
        // ★ 门控：学习模式
        if (on && !com.autoskip.helper.license.FeatureGate.isEnabled(com.autoskip.helper.license.FeatureGate.Feat.LEARNING)) return@launch
        // ★ 持久化开关
        repo.setLearnMode(on)
        // ★ 更新服务回调
        val svc = AutoClickAccessibilityService.instance ?: return@launch
        svc.learnCallback = if (on) {
            { node ->
                viewModelScope.launch {
                    repo.addLearnedRuleIfAbsent(node.text, node.viewId, node.packageName)
                    repo.addToWhitelist(node.packageName)
                }
            }
        } else null
    }
}
