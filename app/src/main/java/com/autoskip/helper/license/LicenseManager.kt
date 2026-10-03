package com.autoskip.helper.license

import android.content.Context
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    /** 探测缓存有效期（24 小时） */
    private const val PROBE_CACHE_MS = 24 * 3600_000L
    /** 连续网络失败次数（触发重探） */
    @Volatile private var hbFailCount = 0

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false
    private var prefs: LicensePrefs? = null
    /** 心跳互斥：避免多个心跳并发导致状态抖动 */
    private val heartBeatLock = java.util.concurrent.atomic.AtomicBoolean(false)

    /** ★ 更新信息（UI 观察；非空则需弹窗） */
    data class UpdateInfo(
        val latestVersion: String,
        val latestVersionCode: Long,
        val downloadUrl: String,
        val force: Boolean,
        val note: String
    )
    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo

    /** 用户已忽略的非强制更新版本（不再提示） */
    private var dismissedVersionCode: Long = 0

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
            val savedCode = p.getCode()
            Log.i(TAG, "启动：激活码=" + (if (savedCode.isBlank()) "(空)" else savedCode) + " 缓存=" + cachedJson.length + "字节")
            if (cachedJson.isNotBlank()) {
                val map = parseFeatures(cachedJson)
                FeatureGate.loadCache(map, p.getExpireAt(), p.getOffset())
            } else {
                // 无缓存 = 从未激活 → 明确进入未激活态，显示激活页
                FeatureGate.lockImmediately()
            }
            // ★ ② 探测最优域名（24 小时缓存有效则跳过）
            runCatching { ensureBestBase(p) }
            // ③ 起心跳循环（首次加随机抖动 0-60s，避免 80 台设备同时打）
            delay((0..60_000L).random())
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

    /** 用户忽略本次（仅非强制更新可调用） */
    fun dismissUpdate() {
        val cur = _updateInfo.value ?: return
        if (cur.force) return  // 强制更新不允许忽略
        dismissedVersionCode = cur.latestVersionCode
        _updateInfo.value = null
    }

    /**
     * 探测最优域名：
     * - 24 小时内已探测过且缓存有效 → 跳过
     * - 候选 = [内置兜底] ∪ [本地缓存的 endpoints]
     * - 选出延迟最低的写入 bestBase
     */
    private suspend fun ensureBestBase(p: LicensePrefs) {
        val cached = p.getBestBase()
        val probedAt = p.getBestProbedAt()
        val fresh = cached.isNotBlank() &&
            (System.currentTimeMillis() - probedAt < PROBE_CACHE_MS)
        if (fresh) {
            Log.i(TAG, "沿用缓存的 bestBase=" + cached)
            return
        }
        val candidates = buildCandidates(p)
        val best = LicenseClient.probeBest(candidates)
        if (best.isNotBlank()) {
            p.setBestBase(best)
            p.setBestProbedAt(System.currentTimeMillis())
            Log.i(TAG, "探测完成，bestBase=" + best)
        }
    }

    /** 构造候选域名列表：内置兜底 + 服务端下发的 endpoints（去重） */
    private suspend fun buildCandidates(p: LicensePrefs): List<String> {
        val out = ArrayList<String>()
        out.add(LicensePrefs.BUILTIN_BASE)
        val json = p.getEndpointsJson()
        if (json.isNotBlank()) {
            // 简单解析：["https://a","https://b"]
            Regex("\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(json).forEach {
                val s = it.groupValues[1].trim()
                if ((s.startsWith("http://") || s.startsWith("https://")) && s !in out) out.add(s)
            }
        }
        return out
    }

    private suspend fun heartBeat(ctx: Context, p: LicensePrefs) {
        // 互斥：上一次心跳没跑完就跳过本次
        if (!heartBeatLock.compareAndSet(false, true)) return
        try {
            val base = p.baseUrl.first()
            val deviceId = deviceId(ctx)
            if (deviceId.isBlank()) return
            // ★ 取当前 App 版本
            val pkgInfo = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            @Suppress("DEPRECATION")
            val versionName = pkgInfo.versionName ?: "0"
            @Suppress("DEPRECATION")
            val versionCode = if (android.os.Build.VERSION.SDK_INT >= 28)
                pkgInfo.longVersionCode
            else
                @Suppress("DEPRECATION") pkgInfo.versionCode.toLong()
            val resp = LicenseClient.check(base, deviceId, versionName, versionCode)
            // ★ 网络失败计数：连续 3 次 → 重探
            if (resp.reason == "network") {
                hbFailCount++
                if (hbFailCount >= 3) {
                    hbFailCount = 0
                    Log.w(TAG, "连续 3 次网络失败，触发重探")
                    runCatching { ensureBestBase(p) }
                }
                FeatureGate.onNetworkError()
                return
            } else {
                hbFailCount = 0
            }
            // ★ 处理服务端下发的 endpoints（null=保留旧缓存，[]=清空）
            resp.endpoints?.let { list ->
                val json = list.joinToString(",", "[", "]") { "\"" + it + "\"" }
                p.setEndpointsJson(json)
                Log.i(TAG, "更新 endpoints: " + list.size + " 个")
                // 若当前 bestBase 不在新列表中且不是内置 → 立即重探
                val cur = p.getBestBase()
                if (cur.isNotBlank() && cur != LicensePrefs.BUILTIN_BASE && cur !in list) {
                    runCatching { ensureBestBase(p) }
                }
            }
            if (resp.ok) {
                FeatureGate.updateFeatures(resp.features, resp.expireAt, resp.serverNow)
                p.setFeaturesJson(toJson(resp.features))
                p.setExpireAt(resp.expireAt)
                p.setOffset(resp.serverNow - System.currentTimeMillis())
                p.setLastHeartbeat(System.currentTimeMillis())
                Log.i(TAG, "心跳成功")

                // ★ 版本检查：仅当激活码开启"检测更新"才处理
                if (resp.autoUpdate && resp.latestVersionCode > 0 && resp.latestVersionCode > versionCode) {
                    Log.i(TAG, "发现新版本：${resp.latestVersion} (${resp.latestVersionCode}) 当前：$versionName ($versionCode)")
                    if (resp.forceUpdate || resp.latestVersionCode > dismissedVersionCode) {
                        _updateInfo.value = UpdateInfo(
                            latestVersion = resp.latestVersion,
                            latestVersionCode = resp.latestVersionCode,
                            downloadUrl = resp.downloadUrl,
                            force = resp.forceUpdate,
                            note = resp.updateNote
                        )
                    }
                    // ★ 后台预下载：仅非强制更新时（强制更新走"联系管理员"，无需下载）
                    if (!resp.forceUpdate && resp.downloadUrl.isNotBlank()) {
                        try {
                            com.autoskip.helper.update.AppUpdater.preDownload(
                                ctx, resp.downloadUrl, resp.latestVersion, resp.latestVersionCode
                            )
                        } catch (_: Exception) {}
                    }
                    // ★ 按"设备绑定激活码"的 forceUpdate 决定锁/解锁
                    //    注：不在此处弹 OverlayToast（App 内已有弹窗，不必打断）
                    if (resp.forceUpdate) {
                        if (!FeatureGate.updateBlocked) {
                            FeatureGate.setUpdateBlocked(true)
                            Log.w(TAG, "强制更新：已暂停所有跳过功能")
                        }
                    } else {
                        // ★ 非强制更新：若之前被锁，现在解锁（管理员把"强制"改回"非强制"）
                        if (FeatureGate.updateBlocked) {
                            FeatureGate.setUpdateBlocked(false)
                            Log.i(TAG, "非强制更新：已恢复跳过功能")
                        }
                    }
                } else {
                    // ★ 版本已是最新 / 未开启检测更新 / 未下发版本 → 解除阻塞
                    if (FeatureGate.updateBlocked) {
                        FeatureGate.setUpdateBlocked(false)
                    }
                }
                // ★ 规则版本检查：服务端版本更新 → 触发同步
                if (resp.rulesVersion > p.getRulesVersion()) {
                    try {
                        RuleSync.sync(ctx)
                    } catch (e: Exception) {
                        Log.e(TAG, "规则同步异常", e)
                    }
                }
            } else {
                when (resp.reason) {
                    "not_activated" -> {
                        // 服务端无此设备记录（可能 DB 抖动/重部署）→ 锁 UI 但保留本地缓存
                        FeatureGate.lockImmediately()
                        Log.w(TAG, "服务端无设备记录，锁定（保留本地缓存）")
                    }
                    "blocked", "expired", "code_disabled" -> {
                        FeatureGate.lockImmediately()
                        p.clearCache()
                        Log.w(TAG, "服务端拒绝：" + resp.reason)
                    }
                    else -> {
                        FeatureGate.onNetworkError()
                        Log.w(TAG, "心跳异常(不锁)：" + resp.reason)
                    }
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
