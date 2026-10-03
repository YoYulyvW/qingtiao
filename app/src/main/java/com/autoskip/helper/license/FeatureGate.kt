package com.autoskip.helper.license

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 全局功能开关（门控）。
 * - 在线以服务端为准
 * - 离线不锁（已激活未到期），用本地缓存
 * - 只在这三种情况锁：无缓存 / 本地到期 / 服务端明确拒绝
 */
object FeatureGate {

    enum class State { LOADING, ACTIVE, LOCKED }

    /** 一级功能 key */
    object Feat {
        const val AUTOSKIP = "autoskip"
        const val DRAMA = "drama"
        const val FENSHEN = "fenshen"
        const val PUSH = "push"
        // 二级
        const val RULE_NORMAL = "rule_normal"
        const val RULE_COND = "rule_cond"
        const val RULE_WIDGET = "rule_widget"
        const val LEARNING = "learning"
        const val WHITELIST = "whitelist"
        const val DRAMA_LONGPRESS = "drama_longpress"
        const val DRAMA_SPEED = "drama_speed"
        const val DRAMA_MOUNT = "drama_mount"
        const val DRAMA_RESUME = "drama_resume"
        const val FENSHEN_CREATE = "fenshen_create"
        const val FENSHEN_RENAME = "fenshen_rename"
        const val FENSHEN_INSTALL = "fenshen_install"
    }

    /** 二级归属 */
    private val parentOf = mapOf(
        Feat.RULE_NORMAL to Feat.AUTOSKIP,
        Feat.RULE_COND to Feat.AUTOSKIP,
        Feat.RULE_WIDGET to Feat.AUTOSKIP,
        Feat.LEARNING to Feat.AUTOSKIP,
        Feat.WHITELIST to Feat.AUTOSKIP,
        Feat.DRAMA_LONGPRESS to Feat.DRAMA,
        Feat.DRAMA_SPEED to Feat.DRAMA,
        Feat.DRAMA_MOUNT to Feat.DRAMA,
        Feat.DRAMA_RESUME to Feat.DRAMA,
        Feat.FENSHEN_CREATE to Feat.FENSHEN,
        Feat.FENSHEN_RENAME to Feat.FENSHEN,
        Feat.FENSHEN_INSTALL to Feat.FENSHEN
    )

    @Volatile private var features: Map<String, Boolean> = emptyMap()
    @Volatile private var expireAt = 0L
    @Volatile private var serverTimeOffset = 0L   // serverNow - localNow

    /** ★ 强制更新阻塞：检测到必须更新时为 true → 所有跳过功能暂停 */
    @Volatile var updateBlocked: Boolean = false
        private set

    private val _state = MutableStateFlow(State.LOADING)
    val state: StateFlow<State> = _state

    /** 更新阻塞状态（UI 观察用） */
    private val _updateBlockedFlow = MutableStateFlow(false)
    val updateBlockedFlow: StateFlow<Boolean> = _updateBlockedFlow

    /** 设置更新阻塞状态 */
    fun setUpdateBlocked(v: Boolean) {
        updateBlocked = v
        _updateBlockedFlow.value = v
    }

    /** 供 UI 观察的 features（刷新时触发重组） */
    private val _featuresFlow = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val featuresFlow: StateFlow<Map<String, Boolean>> = _featuresFlow

    /** 当前到期时间（供 UI 显示） */
    private val _expireFlow = MutableStateFlow(0L)
    val expireFlow: StateFlow<Long> = _expireFlow

    /** 功能是否可用（含锁定/到期判断） */
    fun isEnabled(key: String): Boolean {
        if (_state.value != State.ACTIVE) return false
        // 注：updateBlocked 不在 this 判，由"跳过功能"入口单独判
        //     （否则广告推送也会被挡，而推送需在更新期间保持可用）
        // 到期判断（用校准时间）
        if (expireAt > 0) {
            val effectiveNow = System.currentTimeMillis() + serverTimeOffset
            if (effectiveNow > expireAt) {
                _state.value = State.LOCKED
                return false
            }
        }
        // 一级关闭 → 二级强制关闭
        val parent = parentOf[key]
        if (parent != null && !rawEnabled(parent)) return false
        return rawEnabled(key)
    }

    private fun rawEnabled(key: String): Boolean = features[key] == true

    /** 加载本地缓存（App 启动时，避免联网前误锁）。调用方已确保"有缓存" */
    fun loadCache(cached: Map<String, Boolean>, cachedExpireAt: Long, offset: Long) {
        features = cached
        expireAt = cachedExpireAt
        serverTimeOffset = offset
        _featuresFlow.value = cached
        _expireFlow.value = cachedExpireAt
        _state.value = State.ACTIVE
    }

    /** 服务端在线 OK：刷新开关/到期/时间偏移 */
    fun updateFeatures(map: Map<String, Boolean>, newExpireAt: Long, serverNow: Long) {
        features = map
        expireAt = newExpireAt
        serverTimeOffset = serverNow - System.currentTimeMillis()
        _featuresFlow.value = map
        _expireFlow.value = newExpireAt
        _state.value = State.ACTIVE
    }

    /** 服务端明确拒绝 → 立即锁 */
    fun lockImmediately() {
        features = emptyMap()
        _featuresFlow.value = emptyMap()
        _state.value = State.LOCKED
    }

    /** 网络错误 / 服务端离线 → 不锁（沿用缓存） */
    fun onNetworkError() {
        // 什么都不做，到期判断在 isEnabled 里
    }

    fun currentState(): State = _state.value
}
