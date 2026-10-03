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
    private val prefs = FenShenPrefs(app)
    val state = engine.state

    /** 当前悬浮窗（保留引用，避免重复开始叠加多个） */
    private var currentConsole: FloatConsole? = null

    /** 已保存的配置（UI 读取用于回填） */
    val savedConfig = prefs.config

    fun loadConfig(onLoaded: (FenShenConfig) -> Unit) {
        viewModelScope.launch {
            onLoaded(prefs.load())
        }
    }

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
        // ★ 门控：分身功能未授权 → 提示并拒绝
        if (com.autoskip.helper.license.FeatureGate.updateBlocked) {
            android.widget.Toast.makeText(ctx, "请更新 App 后继续使用", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        if (!com.autoskip.helper.license.FeatureGate.isEnabled(com.autoskip.helper.license.FeatureGate.Feat.FENSHEN)) {
            android.widget.Toast.makeText(ctx, "分身功能未授权", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        // 启动时持久化配置
        viewModelScope.launch { prefs.save(config) }
        // 注意：必须用 IO 线程执行引擎，否则节点遍历会阻塞主线程，导致悬浮窗按钮点不动
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // 关闭上一次残留的悬浮窗，避免叠加
            currentConsole?.dismiss()
            currentConsole = null

            val fc = FloatConsole(
                ctx,
                logHeightDp = config.logHeightDp,
                onPauseToggle = { engine.setPaused(it) },
                onStop = { engine.requestStop() },
                onClose = {
                    engine.requestStop()
                    currentConsole = null
                }
            )
            currentConsole = fc
            engine.attachConsole(fc)
            if (hasOverlayPermission()) fc.show() else onNeedFloat()
            try {
                engine.run(config)
            } catch (e: Exception) {
                // 出错已写入状态
            }
            // 任务结束后不关闭悬浮窗，由用户点"关闭"按钮手动关闭
        }
    }

    fun stop() = engine.requestStop()
}
