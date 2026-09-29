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

    /** 服务端地址（固定内置，忽略历史残留，防止旧地址导致心跳失败） */
    val baseUrl = context.licenseStore.data.map { DEFAULT_BASE }
    suspend fun setBaseUrl(v: String) { context.licenseStore.edit { it[KEY_BASE] = DEFAULT_BASE } }

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

    /** 清空授权缓存（被服务端封禁时调用） */
    suspend fun clearCache() {
        context.licenseStore.edit {
            it.remove(KEY_FEATURES)
            it.remove(KEY_EXPIRE_AT)
            it.remove(KEY_OFFSET)
        }
    }

    companion object {
        /** ★ 默认服务端地址（内置，UI 不展示） */
        const val DEFAULT_BASE = "https://pybot.eu.org"
    }
}
