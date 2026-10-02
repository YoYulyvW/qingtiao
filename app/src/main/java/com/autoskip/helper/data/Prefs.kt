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
    private val KEY_MIGRATED_V2 = booleanPreferencesKey("migrated_v2")
    private val KEY_WL_SEEDED = booleanPreferencesKey("whitelist_seeded")
    private val KEY_MIGRATED_V3 = booleanPreferencesKey("migrated_v3")
    private val KEY_MIGRATED_V4 = booleanPreferencesKey("migrated_v4")
    private val KEY_MIGRATED_COND_V1 = booleanPreferencesKey("migrated_cond_v1")
    private val KEY_MIGRATED_COND_DELAY_V1 = booleanPreferencesKey("migrated_cond_delay_v1")
    private val KEY_RULE_SEEDED = booleanPreferencesKey("rule_seeded_v1")
    private val KEY_COND_DRAMA_ONLY = booleanPreferencesKey("cond_drama_only")
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
    private val KEY_DRAMA_MOUNT_BY_ID = booleanPreferencesKey("drama_mount_by_id")
    private val KEY_DRAMA_RESUME_PAUSE = booleanPreferencesKey("drama_resume_pause")
    private val KEY_PUSH_URL = stringPreferencesKey("push_url")
    private val KEY_PUSH_NAME = stringPreferencesKey("push_name")
    private val KEY_PUSH_USER = stringPreferencesKey("push_user")
    private val KEY_PUSH_MSG = stringPreferencesKey("push_msg")
    private val KEY_PUSH_TOKEN = stringPreferencesKey("push_token")
    private val KEY_PUSH_INCLUDE_CLONE = booleanPreferencesKey("push_include_clone")
    private val KEY_LEARN_MODE = booleanPreferencesKey("learn_mode")
    private val KEY_OVERLAY_TOAST = booleanPreferencesKey("overlay_toast")

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
    val migratedCondV1: Flow<Boolean> = context.dataStore.data.map { it[KEY_MIGRATED_COND_V1] ?: false }
    suspend fun markMigratedCondV1() { context.dataStore.edit { it[KEY_MIGRATED_COND_V1] = true } }
    val migratedCondDelayV1: Flow<Boolean> = context.dataStore.data.map { it[KEY_MIGRATED_COND_DELAY_V1] ?: false }
    suspend fun markMigratedCondDelayV1() { context.dataStore.edit { it[KEY_MIGRATED_COND_DELAY_V1] = true } }

    /** 内置规则是否已 seed 过（只做一次，避免云端删除后被重新 seed） */
    val ruleSeeded: Flow<Boolean> = context.dataStore.data.map { it[KEY_RULE_SEEDED] ?: false }
    suspend fun markRuleSeeded() { context.dataStore.edit { it[KEY_RULE_SEEDED] = true } }

    /** 条件规则仅在抖音/白名单内生效（默认开，避免其他界面误触） */
    val condDramaOnly: Flow<Boolean> = context.dataStore.data.map { it[KEY_COND_DRAMA_ONLY] ?: true }
    suspend fun setCondDramaOnly(v: Boolean) { context.dataStore.edit { it[KEY_COND_DRAMA_ONLY] = v } }
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
    /** 挂载识别方式：true=控件ID识别，false=文字识别（默认 false） */
    val dramaMountById: Flow<Boolean> = context.dataStore.data.map { it[KEY_DRAMA_MOUNT_BY_ID] ?: false }
    suspend fun setDramaMountById(v: Boolean) { context.dataStore.edit { it[KEY_DRAMA_MOUNT_BY_ID] = v } }
    /** 识别到"暂停"控件时点击恢复播放，默认开 */
    val dramaResumePause: Flow<Boolean> = context.dataStore.data.map { it[KEY_DRAMA_RESUME_PAUSE] ?: true }
    suspend fun setDramaResumePause(v: Boolean) { context.dataStore.edit { it[KEY_DRAMA_RESUME_PAUSE] = v } }

    /** 广告推送地址（空则不推送） */
    val pushUrl: Flow<String> = context.dataStore.data.map { it[KEY_PUSH_URL] ?: "" }
    suspend fun setPushUrl(v: String) { context.dataStore.edit { it[KEY_PUSH_URL] = v } }
    /** 推送识别字符（设备名，如"1号板XX号机"） */
    val pushName: Flow<String> = context.dataStore.data.map { it[KEY_PUSH_NAME] ?: "" }
    suspend fun setPushName(v: String) { context.dataStore.edit { it[KEY_PUSH_NAME] = v } }
    /** 推送 user 字段（可选） */
    val pushUser: Flow<String> = context.dataStore.data.map { it[KEY_PUSH_USER] ?: "" }
    suspend fun setPushUser(v: String) { context.dataStore.edit { it[KEY_PUSH_USER] = v } }
    /** 推送消息内容（可自定义，默认"出现了广告窗口，请注意查看"） */
    val pushMsg: Flow<String> = context.dataStore.data.map { it[KEY_PUSH_MSG] ?: "出现了广告窗口，请注意查看" }
    suspend fun setPushMsg(v: String) { context.dataStore.edit { it[KEY_PUSH_MSG] = v } }
    /** 推送 token（可选，携带在请求头 Authorization） */
    val pushToken: Flow<String> = context.dataStore.data.map { it[KEY_PUSH_TOKEN] ?: "" }
    suspend fun setPushToken(v: String) { context.dataStore.edit { it[KEY_PUSH_TOKEN] = v } }
    /** 推送是否附带当前分身名（默认开） */
    val pushIncludeClone: Flow<Boolean> = context.dataStore.data.map { it[KEY_PUSH_INCLUDE_CLONE] ?: true }
    suspend fun setPushIncludeClone(v: Boolean) { context.dataStore.edit { it[KEY_PUSH_INCLUDE_CLONE] = v } }

    /** 学习模式开关（持久化，重启恢复） */
    val learnMode: Flow<Boolean> = context.dataStore.data.map { it[KEY_LEARN_MODE] ?: false }
    suspend fun setLearnMode(v: Boolean) { context.dataStore.edit { it[KEY_LEARN_MODE] = v } }

    /** 识别提示悬浮窗开关（默认关；需要悬浮窗权限） */
    val overlayToastEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_OVERLAY_TOAST] ?: false }
    suspend fun setOverlayToastEnabled(v: Boolean) { context.dataStore.edit { it[KEY_OVERLAY_TOAST] = v } }

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

    /** 原子添加白名单（读-改-写在同一 edit 事务内，避免并发丢更新） */
    suspend fun addWhitelistPkg(pkg: String) {
        context.dataStore.edit { p ->
            val cur = (p[KEY_WL_PKGS] ?: "").split("|").filter { it.isNotBlank() }.toMutableSet()
            if (cur.add(pkg)) p[KEY_WL_PKGS] = cur.joinToString("|")
        }
    }

    /** 原子切换白名单成员（added=true 表示本次是加入） */
    suspend fun toggleWhitelistPkg(pkg: String, onResult: (Boolean) -> Unit) {
        context.dataStore.edit { p ->
            val cur = (p[KEY_WL_PKGS] ?: "").split("|").filter { it.isNotBlank() }.toMutableSet()
            val added = if (cur.contains(pkg)) { cur.remove(pkg); false } else { cur.add(pkg); true }
            p[KEY_WL_PKGS] = cur.joinToString("|")
            onResult(added)
        }
    }

    /** 原子移除白名单成员 */
    suspend fun removeWhitelistPkg(pkg: String) {
        context.dataStore.edit { p ->
            val cur = (p[KEY_WL_PKGS] ?: "").split("|").filter { it.isNotBlank() }.toMutableSet()
            if (cur.remove(pkg)) p[KEY_WL_PKGS] = cur.joinToString("|")
        }
    }

    companion object {
        const val DEFAULT_DELAY = 600L
    }
}
