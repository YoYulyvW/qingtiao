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
    private val KEY_WL_SEEDED = booleanPreferencesKey("whitelist_seeded")
    private val KEY_MIGRATED_V3 = booleanPreferencesKey("migrated_v3")
    private val KEY_MIGRATED_V4 = booleanPreferencesKey("migrated_v4")
    // 短剧自动3倍速
    private val KEY_DRAMA_ENABLED = booleanPreferencesKey("drama_enabled")
    private val KEY_DRAMA_AUTO_MOUNT = booleanPreferencesKey("drama_auto_mount")
    private val KEY_DRAMA_INTERVAL = stringPreferencesKey("drama_interval_ms")
    private val KEY_DRAMA_TARGET = stringPreferencesKey("drama_target_speed")
    private val KEY_DRAMA_BASEW = stringPreferencesKey("drama_basew")
    private val KEY_DRAMA_BASEH = stringPreferencesKey("drama_baseh")
    private val KEY_DRAMA_TAPX = stringPreferencesKey("drama_tapx")
    private val KEY_DRAMA_TAPY = stringPreferencesKey("drama_tapy")
    private val KEY_DRAMA_CLICKS = stringPreferencesKey("drama_clicks")
    private val KEY_DRAMA_DEBUG = booleanPreferencesKey("drama_debug")
    private val KEY_DRAMA_IMG_INT = stringPreferencesKey("drama_img_int")
    private val KEY_DRAMA_NORMAL_INT = stringPreferencesKey("drama_normal_int")

    private val KEY_DRAMA_LONGPRESS = booleanPreferencesKey("drama_longpress")
    private val KEY_DRAMA_CLICKSPEED = booleanPreferencesKey("drama_clickspeed")

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

    val migratedV3: Flow<Boolean> = context.dataStore.data.map { it[KEY_MIGRATED_V3] ?: false }
    suspend fun markMigratedV3() {
        context.dataStore.edit { it[KEY_MIGRATED_V3] = true }
    }

    val migratedV4: Flow<Boolean> = context.dataStore.data.map { it[KEY_MIGRATED_V4] ?: false }
    suspend fun markMigratedV4() {
        context.dataStore.edit { it[KEY_MIGRATED_V4] = true }
    }

    /** 短剧自动3倍速：总开关 */
    val dramaEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_DRAMA_ENABLED] ?: false }
    /** 自动点击挂载按钮（关闭则仅手动进入短剧后加速） */
    val dramaAutoMount: Flow<Boolean> = context.dataStore.data.map { it[KEY_DRAMA_AUTO_MOUNT] ?: true }
    /** 检测间隔（毫秒） */
    val dramaIntervalMs: Flow<Long> = context.dataStore.data.map { (it[KEY_DRAMA_INTERVAL] ?: "1000").toLongOrNull() ?: 1000L }
    /** 目标倍速文字，如 "3x" */
    val dramaTargetSpeed: Flow<String> = context.dataStore.data.map { it[KEY_DRAMA_TARGET] ?: "3x" }

    /** 基准分辨率（坐标按此填写，运行时按真机等比换算） */
    val dramaBaseW: Flow<Int> = context.dataStore.data.map { it[KEY_DRAMA_BASEW]?.toIntOrNull() ?: 1080 }
    val dramaBaseH: Flow<Int> = context.dataStore.data.map { it[KEY_DRAMA_BASEH]?.toIntOrNull() ?: 2340 }
    /** 倍速按钮中心坐标（基于基准分辨率），默认 652,1137 */
    val dramaTapX: Flow<Int> = context.dataStore.data.map { it[KEY_DRAMA_TAPX]?.toIntOrNull() ?: 652 }
    val dramaTapY: Flow<Int> = context.dataStore.data.map { it[KEY_DRAMA_TAPY]?.toIntOrNull() ?: 1137 }
    /** 每次进入短剧连点次数（1x→3x 需 4 次） */
    val dramaClicks: Flow<Int> = context.dataStore.data.map { it[KEY_DRAMA_CLICKS]?.toIntOrNull() ?: 4 }

    suspend fun setDramaBaseW(v: Int) { context.dataStore.edit { it[KEY_DRAMA_BASEW] = v.toString() } }
    suspend fun setDramaBaseH(v: Int) { context.dataStore.edit { it[KEY_DRAMA_BASEH] = v.toString() } }
    suspend fun setDramaTapX(v: Int) { context.dataStore.edit { it[KEY_DRAMA_TAPX] = v.toString() } }
    suspend fun setDramaTapY(v: Int) { context.dataStore.edit { it[KEY_DRAMA_TAPY] = v.toString() } }
    suspend fun setDramaClicks(v: Int) { context.dataStore.edit { it[KEY_DRAMA_CLICKS] = v.toString() } }

    /** 短剧调试日志开关（默认关） */
    val dramaDebug: Flow<Boolean> = context.dataStore.data.map { it[KEY_DRAMA_DEBUG] ?: false }
    suspend fun setDramaDebug(v: Boolean) { context.dataStore.edit { it[KEY_DRAMA_DEBUG] = v } }

    /** 「识别图片」版菜单的呼出间隔（集数），默认 1（每集） */
    val dramaImgInterval: Flow<Int> = context.dataStore.data.map { it[KEY_DRAMA_IMG_INT]?.toIntOrNull() ?: 1 }
    suspend fun setDramaImgInterval(v: Int) { context.dataStore.edit { it[KEY_DRAMA_IMG_INT] = v.toString() } }

    /** 其他版菜单的呼出间隔（集数），默认 5 */
    val dramaNormalInterval: Flow<Int> = context.dataStore.data.map { it[KEY_DRAMA_NORMAL_INT]?.toIntOrNull() ?: 5 }
    suspend fun setDramaNormalInterval(v: Int) { context.dataStore.edit { it[KEY_DRAMA_NORMAL_INT] = v.toString() } }

    /** 自动长按开关，默认开 */
    val dramaLongPress: Flow<Boolean> = context.dataStore.data.map { it[KEY_DRAMA_LONGPRESS] ?: true }
    suspend fun setDramaLongPress(v: Boolean) { context.dataStore.edit { it[KEY_DRAMA_LONGPRESS] = v } }
    /** 自动点击倍数开关，默认开 */
    val dramaClickSpeed: Flow<Boolean> = context.dataStore.data.map { it[KEY_DRAMA_CLICKSPEED] ?: true }
    suspend fun setDramaClickSpeed(v: Boolean) { context.dataStore.edit { it[KEY_DRAMA_CLICKSPEED] = v } }

    suspend fun setDramaEnabled(v: Boolean) { context.dataStore.edit { it[KEY_DRAMA_ENABLED] = v } }
    suspend fun setDramaAutoMount(v: Boolean) { context.dataStore.edit { it[KEY_DRAMA_AUTO_MOUNT] = v } }
    suspend fun setDramaInterval(ms: Long) { context.dataStore.edit { it[KEY_DRAMA_INTERVAL] = ms.toString() } }
    suspend fun setDramaTargetSpeed(s: String) { context.dataStore.edit { it[KEY_DRAMA_TARGET] = s } }

    /** 是否已预置过默认白名单（抖音等） */
    val whitelistSeeded: Flow<Boolean> = context.dataStore.data.map { it[KEY_WL_SEEDED] ?: false }
    suspend fun markWhitelistSeeded() {
        context.dataStore.edit { it[KEY_WL_SEEDED] = true }
    }
    suspend fun setWhitelist(pkgs: Set<String>) {
        context.dataStore.edit { it[KEY_WL_PKGS] = pkgs.joinToString("|") }
    }

    companion object {
        const val DEFAULT_DELAY = 600L
    }
}
