package com.autoskip.helper.root

import android.content.Context
import android.util.Log
import com.autoskip.helper.service.DramaDebug
import java.io.File

/**
 * 远程控制 POC 测试器。
 *
 * 用于验证 app_process 通路：
 *   1. 定位 APK 路径
 *   2. su 执行 app_process 启动 CaptureMain
 *   3. 读取日志判断是否成功
 *
 * 用法（App 内按钮）：RemoteControlTester.testCaptureProcess(context)
 */
object RemoteControlTester {
    private const val TAG = "RemoteControlTester"

    /**
     * 测试 app_process 通路。
     * 返回日志字符串（供 UI 展示）。
     */
    fun testAppProcess(ctx: Context): String {
        val sb = StringBuilder()
        fun line(s: String) {
            Log.i(TAG, s)
            sb.append(s).append('\n')
            DramaDebug.add("[远控] " + s)
        }

        // 1) Root 检测
        line("① Root 检测…")
        val rooted = RootChecker.isRooted(force = true, checkByExec = true)
        line("   Root 可用：$rooted")
        if (!rooted) {
            line("   ✗ 设备未 Root 或用户拒绝授权")
            return sb.toString()
        }

        // 2) 获取 su 信息
        val suVer = RootShell.getSuVersion()
        line("   su 版本：$suVer")

        // 3) 定位 APK 路径
        val apkPath = try {
            ctx.packageManager.getApplicationInfo(ctx.packageName, 0).sourceDir
        } catch (e: Exception) {
            ""
        }
        line("② APK 路径：$apkPath")
        if (apkPath.isEmpty() || !File(apkPath).exists()) {
            line("   ✗ APK 路径获取失败")
            return sb.toString()
        }

        // 4) 执行 app_process
        line("③ 启动 app_process…")
        val cmd = "CLASSPATH=$apkPath app_process /system/bin com.autoskip.helper.capture.CaptureMain poc=1 port=27183"
        line("   命令：$cmd")
        val r = RootShell.exec(cmd, timeoutMs = 15_000)
        line("   退出码：${r.exitCode} 超时：${r.timedOut}")
        line("   输出：")
        r.stdout.split('\n').forEach { line("     | $it") }
        if (r.error != null) line("   错误：${r.error}")

        return sb.toString()
    }
}
