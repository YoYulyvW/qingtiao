package com.autoskip.helper.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autoskip.helper.App
import com.autoskip.helper.data.RuleEntity
import com.autoskip.helper.service.AutoClickAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as App).repo

    val rules = repo.rules.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val logs = repo.recentLogs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val totalCount = repo.totalCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val todayCount = repo.todayCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val enabled = repo.enabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val clickDelay = repo.clickDelayMs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 600L)
    val whitelistEnabled = repo.whitelistEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val whitelistPkgs = repo.whitelistPkgs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    val strictClose = repo.strictClose.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    // 短剧自动倍速
    val dramaEnabled = repo.dramaEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val dramaAutoMount = repo.dramaAutoMount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val dramaIntervalMs = repo.dramaIntervalMs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1000L)
    val dramaTargetSpeed = repo.dramaTargetSpeed.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "3x")

    private val _learnMode = MutableStateFlow(false)
    val learnMode = _learnMode.asStateFlow()

    fun setEnabled(v: Boolean) = viewModelScope.launch { repo.setEnabled(v) }

    fun setClickDelay(ms: Long) = viewModelScope.launch { repo.setClickDelay(ms) }

    fun addRule(rule: RuleEntity) = viewModelScope.launch { repo.addRule(rule) }

    fun updateRule(rule: RuleEntity) = viewModelScope.launch { repo.updateRule(rule) }

    fun deleteRule(rule: RuleEntity) = viewModelScope.launch { repo.deleteRule(rule) }

    fun clearLogs() = viewModelScope.launch { repo.clearLogs() }

    fun setWhitelistEnabled(v: Boolean) = viewModelScope.launch { repo.setWhitelistEnabled(v) }

    fun setStrictClose(v: Boolean) = viewModelScope.launch { repo.setStrictClose(v) }

    fun setDramaEnabled(v: Boolean) = viewModelScope.launch { repo.setDramaEnabled(v) }
    fun setDramaAutoMount(v: Boolean) = viewModelScope.launch { repo.setDramaAutoMount(v) }
    fun setDramaInterval(ms: Long) = viewModelScope.launch { repo.setDramaInterval(ms) }
    fun setDramaTargetSpeed(s: String) = viewModelScope.launch { repo.setDramaTargetSpeed(s) }

    fun toggleWhitelistPkg(pkg: String) = viewModelScope.launch {
        val cur = repo.whitelistPkgs.first()
        if (pkg in cur) repo.setWhitelist(cur - pkg) else repo.setWhitelist(cur + pkg)
    }

    fun removeFromWhitelist(pkg: String) = viewModelScope.launch {
        repo.setWhitelist(repo.whitelistPkgs.first() - pkg)
    }

    /** 添加一条自定义通配/包名规则 */
    fun addWhitelistPattern(pattern: String) = viewModelScope.launch {
        val p = pattern.trim()
        if (p.isBlank()) return@launch
        val cur = repo.whitelistPkgs.first()
        if (p !in cur) repo.setWhitelist(cur + p)
    }

    /** 学习模式：开启后，用户在其他 App 手动点击的按钮会被自动记录为规则 */
    fun setLearnMode(on: Boolean) {
        _learnMode.value = on
        val svc = AutoClickAccessibilityService.instance ?: return
        svc.learnCallback = if (on) {
            { node ->
                viewModelScope.launch {
                    // 学习到规则时，自动把该应用加入白名单
                    repo.addLearnedRuleIfAbsent(node.text, node.viewId, node.packageName)
                    repo.addToWhitelist(node.packageName)
                }
            }
        } else null
    }
}
