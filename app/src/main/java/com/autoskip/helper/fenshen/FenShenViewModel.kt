package com.autoskip.helper.fenshen

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class FenShenViewModel(app: Application) : AndroidViewModel(app) {

    private val engine = FenShenEngine(app)
    val state = engine.state

    fun hasOverlayPermission(): Boolean {
        val ctx = getApplication<Application>()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(ctx) else true
    }

    fun requestOverlayPermission() {
        val ctx = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(ctx)) {
            val i = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        }
    }

    /** 是否开启分身无障碍服务 */
    fun isServiceEnabled(): Boolean {
        if (FenShenAccessibilityService.isRunning()) return true
        val ctx = getApplication<Application>()
        val expected = ctx.packageName + "/" + FenShenAccessibilityService::class.java.name
        val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    fun start(config: FenShenConfig, onNeedFloat: () -> Unit) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val fc = FloatConsole(
                ctx,
                onPauseToggle = { engine.setPaused(it) },
                onStop = { engine.requestStop() },
                onClose = { engine.requestStop() }
            )
            engine.attachConsole(fc)
            if (hasOverlayPermission()) fc.show() else onNeedFloat()
            try {
                engine.run(config)
            } catch (e: Exception) {
                // 出错已写入状态
            } finally {
                fc.dismiss()
            }
        }
    }

    fun stop() = engine.requestStop()
}
