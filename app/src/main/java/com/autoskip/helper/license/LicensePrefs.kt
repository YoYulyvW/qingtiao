package com.autoskip.helper.license

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.licenseStore by preferencesDataStore(name = "autoskip_license")

/**
 * 授权相关的持久化。
 * - 激活码、features 缓存、到期时间、时间偏移、最后心跳
 */
class LicensePrefs(private val context: Context) {

    private val KEY_CODE = stringPreferencesKey("lic_code")
    private val KEY_FEATURES = stringPreferencesKey("lic_features")   // JSON
    private val KEY_EXPIRE_AT = stringPreferencesKey("lic_expire_at") // Long as String
    private val KEY_OFFSET = stringPreferencesKey("lic_offset")       // serverNow - localNow
    private val KEY_LAST_HB = stringPreferencesKey("lic_last_hb")
    private val KEY_BASE = stringPreferencesKey("lic_base")           // 服务端地址
    private val KEY_DEVICE_NAME = stringPreferencesKey("lic_device_name")
    private val KEY_RULES_VERSION = stringPreferencesKey("lic_rules_version")
    private val KEY_ENDPOINTS = stringPreferencesKey("lic_endpoints")       // 服务端下发的域名 JSON 数组
    private val KEY_BEST_BASE = stringPreferencesKey("lic_best_base")       // 探测出的最优域名
    private val KEY_BEST_PROBED_AT = stringPreferencesKey("lic_best_probed_at") // 上次探测时间戳

    /** 服务端地址（兼容旧代码：返回 bestBase 或 内置兜底） */
    val baseUrl = context.licenseStore.data.map { prefs ->
        prefs[KEY_BEST_BASE]?.takeIf { it.isNotBlank() } ?: BUILTIN_BASE
    }

    /** 服务端下发的候选域名列表（JSON 数组字符串） */
    suspend fun getEndpointsJson(): String = context.licenseStore.data.map { it[KEY_ENDPOINTS] ?: "" }.first()
    suspend fun setEndpointsJson(v: String) { context.licenseStore.edit { it[KEY_ENDPOINTS] = v } }

    /** 探测出的最优域名（首次为空，用内置兜底） */
    suspend fun getBestBase(): String = context.licenseStore.data.map { it[KEY_BEST_BASE] ?: "" }.first()
    suspend fun setBestBase(v: String) { context.licenseStore.edit { it[KEY_BEST_BASE] = v } }

    /** 上次探测时间戳 */
    suspend fun getBestProbedAt(): Long = context.licenseStore.data.map { (it[KEY_BEST_PROBED_AT] ?: "0").toLongOrNull() ?: 0L }.first()
    suspend fun setBestProbedAt(v: Long) { context.licenseStore.edit { it[KEY_BEST_PROBED_AT] = v.toString() } }

    /** 激活码 */
    suspend fun getCode(): String = context.licenseStore.data.map { it[KEY_CODE] ?: "" }.first()
    suspend fun setCode(v: String) { context.licenseStore.edit { it[KEY_CODE] = v } }

    /** 设备备注名 */
    suspend fun getDeviceName(): String = context.licenseStore.data.map { it[KEY_DEVICE_NAME] ?: "" }.first()
    suspend fun setDeviceName(v: String) { context.licenseStore.edit { it[KEY_DEVICE_NAME] = v } }

    /** features JSON 缓存 */
    suspend fun getFeaturesJson(): String = context.licenseStore.data.map { it[KEY_FEATURES] ?: "" }.first()
    suspend fun setFeaturesJson(v: String) { context.licenseStore.edit { it[KEY_FEATURES] = v } }

    /** 到期时间戳(ms) */
    suspend fun getExpireAt(): Long = context.licenseStore.data.map { (it[KEY_EXPIRE_AT] ?: "0").toLongOrNull() ?: 0L }.first()
    suspend fun setExpireAt(v: Long) { context.licenseStore.edit { it[KEY_EXPIRE_AT] = v.toString() } }

    /** 时间偏移 serverNow - localNow */
    suspend fun getOffset(): Long = context.licenseStore.data.map { (it[KEY_OFFSET] ?: "0").toLongOrNull() ?: 0L }.first()
    suspend fun setOffset(v: Long) { context.licenseStore.edit { it[KEY_OFFSET] = v.toString() } }

    /** 最后心跳成功时间 */
    suspend fun getLastHeartbeat(): Long = context.licenseStore.data.map { (it[KEY_LAST_HB] ?: "0").toLongOrNull() ?: 0L }.first()
    suspend fun setLastHeartbeat(v: Long) { context.licenseStore.edit { it[KEY_LAST_HB] = v.toString() } }

    /** 规则版本号（本地已同步到的服务端版本） */
    suspend fun getRulesVersion(): Long = context.licenseStore.data.map { (it[KEY_RULES_VERSION] ?: "0").toLongOrNull() ?: 0L }.first()
    suspend fun setRulesVersion(v: Long) { context.licenseStore.edit { it[KEY_RULES_VERSION] = v.toString() } }

    /** 清空授权缓存（被服务端封禁时调用） */
    suspend fun clearCache() {
        context.licenseStore.edit {
            it.remove(KEY_FEATURES)
            it.remove(KEY_EXPIRE_AT)
            it.remove(KEY_OFFSET)
        }
    }

    companion object {
        /** ★ 内置兜底域名（CF Tunnel，永不过期） */
        const val BUILTIN_BASE = "https://pybot.eu.org"
        /** 兼容旧代码的别名 */
        const val DEFAULT_BASE = BUILTIN_BASE
    }
}
