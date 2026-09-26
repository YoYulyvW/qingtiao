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

    val enabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_ENABLED] ?: true }
    val clickDelayMs: Flow<Long> = context.dataStore.data.map { (it[KEY_DELAY] ?: "600").toLongOrNull() ?: 600L }
    val lastPackage: Flow<String?> = context.dataStore.data.map { it[KEY_LAST_PKG] }

    suspend fun setEnabled(value: Boolean) {
        context.dataStore.edit { it[KEY_ENABLED] = value }
    }
    suspend fun setClickDelay(ms: Long) {
        context.dataStore.edit { it[KEY_DELAY] = ms.toString() }
    }
    suspend fun setLastPackage(pkg: String?) {
        context.dataStore.edit { if (pkg == null) it.remove(KEY_LAST_PKG) else it[KEY_LAST_PKG] = pkg }
    }

    companion object {
        const val DEFAULT_DELAY = 600L
    }
}
