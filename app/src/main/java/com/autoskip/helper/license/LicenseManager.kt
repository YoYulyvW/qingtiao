package com.autoskip.helper.license

import android.content.Context
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 授权管理器：心跳协程 + 状态机。
 * - 启动时先加载缓存（离线可用）
 * - 之后每 1 分钟心跳（在线刷新 / 离线不影响）
 */
object LicenseManager {
    private const val TAG = "LicenseManager"
    private const val CHECK_INTERVAL = 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false
    private var prefs: LicensePrefs? = null
    /** 心跳互斥：避免多个心跳并发导致状态抖动 */
    private val heartBeatLock = java.util.concurrent.atomic.AtomicBoolean(false)

    /** 设备唯一标识 */
    fun deviceId(ctx: Context): String =
        try { Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: "" }
        catch (e: Exception) { "" }

    /** 启动：加载缓存 + 起心跳 */
    fun start(ctx: Context) {
        if (started) return
        started = true
        val app = ctx.applicationContext
        val p = LicensePrefs(app)
        prefs = p
        scope.launch {
            // ① 加载本地缓存
            val cachedJson = p.getFeaturesJson()
            if (cachedJson.isNotBlank()) {
                val map = parseFeatures(cachedJson)
                FeatureGate.loadCache(map, p.getExpireAt(), p.getOffset())
            } else {
                // 无缓存 = 从未激活 → 明确进入未激活态，显示激活页
                FeatureGate.lockImmediately()
            }
            // ② 起心跳循环
            while (true) {
                heartBeat(app, p)
                delay(CHECK_INTERVAL)
            }
        }
    }

    /** 立即心跳一次（激活成功后 / 开机后） */
    fun checkNow(ctx: Context) {
        val app = ctx.applicationContext
        scope.launch { heartBeat(app, LicensePrefs(app)) }
    }

    private suspend fun heartBeat(ctx: Context, p: LicensePrefs) {
        // 互斥：上一次心跳没跑完就跳过本次
        if (!heartBeatLock.compareAndSet(false, true)) return
        try {
            val base = p.baseUrl.first()
            val deviceId = deviceId(ctx)
            if (deviceId.isBlank()) return
            val resp = LicenseClient.check(base, deviceId)
            if (resp.ok) {
                FeatureGate.updateFeatures(resp.features, resp.expireAt, resp.serverNow)
                p.setFeaturesJson(toJson(resp.features))
                p.setExpireAt(resp.expireAt)
                p.setOffset(resp.serverNow - System.currentTimeMillis())
                p.setLastHeartbeat(System.currentTimeMillis())
                Log.i(TAG, "心跳成功")
            } else {
                // ★ 只有【明确拒绝】才锁；其余（server_error/空/未知/网络）一律不锁，避免抖动误锁
                val definitiveReject = resp.reason == "blocked" ||
                    resp.reason == "expired" ||
                    resp.reason == "not_activated" ||
                    resp.reason == "code_disabled"
                if (definitiveReject) {
                    FeatureGate.lockImmediately()
                    p.clearCache()
                    Log.w(TAG, "服务端拒绝：" + resp.reason)
                } else {
                    // server_error / 空 / 未知 reason / 网络 → 视为异常，沿用缓存
                    FeatureGate.onNetworkError()
                    Log.w(TAG, "心跳异常(不锁)：" + resp.reason)
                }
            }
        } catch (e: Exception) {
            FeatureGate.onNetworkError()
            Log.e(TAG, "心跳异常", e)
        } finally {
            heartBeatLock.set(false)
        }
    }

    /** 激活（供 UI 调用），成功返回 message */
    suspend fun activate(ctx: Context, code: String): String {
        val p = LicensePrefs(ctx.applicationContext)
        val base = p.baseUrl.first()
        val deviceId = deviceId(ctx)
        val deviceName = p.getDeviceName()
        val resp = LicenseClient.activate(base, code, deviceId, deviceName)
        if (resp.ok) {
            p.setCode(code)
            p.setFeaturesJson(toJson(resp.features))
            p.setExpireAt(resp.expireAt)
            p.setOffset(resp.serverNow - System.currentTimeMillis())
            FeatureGate.updateFeatures(resp.features, resp.expireAt, resp.serverNow)
        }
        return resp.message.ifBlank { if (resp.ok) "激活成功" else "激活失败" }
    }

    // ===== 极简 JSON =====

    private fun parseFeatures(json: String): Map<String, Boolean> {
        val out = HashMap<String, Boolean>()
        Regex("\"([a-z_]+)\"\\s*:\\s*(true|false)").findAll(json).forEach {
            out[it.groupValues[1]] = it.groupValues[2] == "true"
        }
        return out
    }

    private fun toJson(m: Map<String, Boolean>): String {
        val sb = StringBuilder("{")
        var first = true
        for ((k, v) in m) {
            if (!first) sb.append(",")
            first = false
            sb.append("\"").append(k).append("\":").append(if (v) "true" else "false")
        }
        sb.append("}")
        return sb.toString()
    }
}
