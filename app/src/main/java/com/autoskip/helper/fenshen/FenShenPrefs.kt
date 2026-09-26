package com.autoskip.helper.fenshen

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.fenShenStore by preferencesDataStore(name = "fenshen_prefs")

/** 分身配置持久化 */
class FenShenPrefs(private val context: Context) {

    private val K_TOTAL = stringPreferencesKey("fs_total")
    private val K_SUFFIX = stringPreferencesKey("fs_suffix")
    private val K_TIMEOUT = stringPreferencesKey("fs_timeout")
    private val K_RETRY = stringPreferencesKey("fs_retry")
    private val K_MAXFAIL = stringPreferencesKey("fs_maxfail")
    private val K_SAVELOG = stringPreferencesKey("fs_savelog")
    private val K_TESTW = stringPreferencesKey("fs_testw")
    private val K_TESTH = stringPreferencesKey("fs_testh")
    private val K_INSTX = stringPreferencesKey("fs_instx")
    private val K_INSTY = stringPreferencesKey("fs_insty")
    private val K_PERMX = stringPreferencesKey("fs_permx")
    private val K_PERMY = stringPreferencesKey("fs_permy")
    private val K_LOCX = stringPreferencesKey("fs_locx")
    private val K_LOCY = stringPreferencesKey("fs_locy")
    private val K_MODE = stringPreferencesKey("fs_mode")       // "count" or "index"
    private val K_STOPINDEX = stringPreferencesKey("fs_stopindex")
    private val K_LOGH = stringPreferencesKey("fs_logh")

    private val d: Flow<androidx.datastore.preferences.core.Preferences> = context.fenShenStore.data

    val config: Flow<FenShenConfig> = d.map { p ->
        FenShenConfig(
            totalCount = p[K_TOTAL]?.toIntOrNull() ?: 20,
            suffixFmt = p[K_SUFFIX] ?: "-{date}号",
            installTimeoutSec = p[K_TIMEOUT]?.toIntOrNull() ?: 60,
            retryTimes = p[K_RETRY]?.toIntOrNull() ?: 1,
            maxFail = p[K_MAXFAIL]?.toIntOrNull() ?: 3,
            saveLog = p[K_SAVELOG]?.toBoolean() ?: true,
            testW = p[K_TESTW]?.toIntOrNull() ?: 1080,
            testH = p[K_TESTH]?.toIntOrNull() ?: 1920,
            installX = p[K_INSTX]?.toIntOrNull() ?: 760,
            installY = p[K_INSTY]?.toIntOrNull() ?: 1620,
            permX = p[K_PERMX]?.toIntOrNull() ?: 540,
            permY = p[K_PERMY]?.toIntOrNull() ?: 1465,
            locX = p[K_LOCX]?.toIntOrNull() ?: 523,
            locY = p[K_LOCY]?.toIntOrNull() ?: 1308,
            useStopIndex = (p[K_MODE] ?: "count") == "index",
            stopIndex = p[K_STOPINDEX]?.toIntOrNull() ?: 50,
            logHeightDp = p[K_LOGH]?.toIntOrNull() ?: 90
        )
    }

    suspend fun load(): FenShenConfig = config.first()

    suspend fun save(c: FenShenConfig) {
        context.fenShenStore.edit { p ->
            p[K_TOTAL] = c.totalCount.toString()
            p[K_SUFFIX] = c.suffixFmt
            p[K_TIMEOUT] = c.installTimeoutSec.toString()
            p[K_RETRY] = c.retryTimes.toString()
            p[K_MAXFAIL] = c.maxFail.toString()
            p[K_SAVELOG] = c.saveLog.toString()
            p[K_TESTW] = c.testW.toString()
            p[K_TESTH] = c.testH.toString()
            p[K_INSTX] = c.installX.toString()
            p[K_INSTY] = c.installY.toString()
            p[K_PERMX] = c.permX.toString()
            p[K_PERMY] = c.permY.toString()
            p[K_LOCX] = c.locX.toString()
            p[K_LOCY] = c.locY.toString()
            p[K_MODE] = if (c.useStopIndex) "index" else "count"
            p[K_STOPINDEX] = c.stopIndex.toString()
            p[K_LOGH] = c.logHeightDp.toString()
        }
    }
}
