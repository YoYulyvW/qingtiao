package com.autoskip.helper.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "autoskip_prefs")

/** 轻量配置 */
class Prefs(private val context: Context) {

    private val KEY_ENABLED = booleanPreferencesKey("auto_click_enabled")
    private val KEY_DELAY = stringPreferencesKey("click_delay_ms")
    private val KEY_LAST_PKG = stringPreferencesKey("last_pkg")
    private val KEY_WL_ENABLED = booleanPreferencesKey("whitelist_enabled")
    private val KEY_WL_PKGS = stringPreferencesKey("whitelist_pkgs")
    private val KEY_STRICT_CLOSE = booleanPreferencesKey("strict_close")
    private val KEY_MIGRATED_V2 = booleanPreferencesKey("migrated_v2")

    val enabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_ENABLED] ?: true }
    val clickDelayMs: Flow<Long> = context.dataStore.data.map { (it[KEY_DELAY] ?: "600").toLongOrNull() ?: 600L }
    val lastPackage: Flow<String?> = context.dataStore.data.map { it[KEY_LAST_PKG] }

    /** 是否只对白名单内的应用生效（默认关闭） */
    val whitelistEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_WL_ENABLED] ?: false }
    /** 白名单包名集合 */
    val whitelistPkgs: Flow<Set<String>> = context.dataStore.data.map { p ->
        (p[KEY_WL_PKGS] ?: "").split("|").filter { it.isNotBlank() }.toSet()
    }

    suspend fun setEnabled(value: Boolean) {
        context.dataStore.edit { it[KEY_ENABLED] = value }
    }
    suspend fun setClickDelay(ms: Long) {
        context.dataStore.edit { it[KEY_DELAY] = ms.toString() }
    }
    suspend fun setLastPackage(pkg: String?) {
        context.dataStore.edit { if (pkg == null) it.remove(KEY_LAST_PKG) else it[KEY_LAST_PKG] = pkg }
    }
    suspend fun setWhitelistEnabled(value: Boolean) {
        context.dataStore.edit { it[KEY_WL_ENABLED] = value }
    }

    /** 严格模式：关闭/X 类按钮仅在登录/广告上下文出现时才点击（默认开） */
    val strictClose: Flow<Boolean> = context.dataStore.data.map { it[KEY_STRICT_CLOSE] ?: true }
    suspend fun setStrictClose(value: Boolean) {
        context.dataStore.edit { it[KEY_STRICT_CLOSE] = value }
    }

    /** 是否已完成 v2 迁移（移除默认"关闭"规则） */
    val migratedV2: Flow<Boolean> = context.dataStore.data.map { it[KEY_MIGRATED_V2] ?: false }
    suspend fun markMigratedV2() {
        context.dataStore.edit { it[KEY_MIGRATED_V2] = true }
    }
    suspend fun setWhitelist(pkgs: Set<String>) {
        context.dataStore.edit { it[KEY_WL_PKGS] = pkgs.joinToString("|") }
    }

    companion object {
        const val DEFAULT_DELAY = 600L
    }
}
