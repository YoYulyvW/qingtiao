package com.autoskip.helper.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autoskip.helper.App
import com.autoskip.helper.data.RuleEntity
import com.autoskip.helper.service.AutoClickAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
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

    private val _learnMode = MutableStateFlow(false)
    val learnMode = _learnMode.asStateFlow()

    fun setEnabled(v: Boolean) = viewModelScope.launch { repo.setEnabled(v) }

    fun setClickDelay(ms: Long) = viewModelScope.launch { repo.setClickDelay(ms) }

    fun addRule(rule: RuleEntity) = viewModelScope.launch { repo.addRule(rule) }

    fun updateRule(rule: RuleEntity) = viewModelScope.launch { repo.updateRule(rule) }

    fun deleteRule(rule: RuleEntity) = viewModelScope.launch { repo.deleteRule(rule) }

    fun clearLogs() = viewModelScope.launch { repo.clearLogs() }

    /** 学习模式：开启后，用户在其他 App 手动点击的按钮会被自动记录为规则 */
    fun setLearnMode(on: Boolean) {
        _learnMode.value = on
        val svc = AutoClickAccessibilityService.instance ?: return
        svc.learnCallback = if (on) {
            { node ->
                viewModelScope.launch {
                    repo.addLearnedRuleIfAbsent(node.text, node.viewId, node.packageName)
                }
            }
        } else null
    }
}
