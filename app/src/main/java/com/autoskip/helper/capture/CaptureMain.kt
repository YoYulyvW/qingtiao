package com.autoskip.helper.capture

import android.os.Looper
import android.os.Process
import android.util.Log

/**
 * app_process 启动入口。
 *
 * 用法（由 App 通过 su 启动）：
 *   su -c "CLASSPATH=<apk路径> app_process /system/bin com.autoskip.helper.capture.CaptureMain <args>"
 *
 * 关键点：
 * - 必须以 shell 用户（UID 2000）运行，才能反射调用 DisplayManagerGlobal.createVirtualDisplay
 * - 本类会被 R8 混淆破坏反射调用 → proguard 规则必须 keep
 * - 参数用 key=value 形式传递
 *
 * 日志：
 * - Log.i → logcat
 * - println → stdout（su 捕获）
 * 两者都输出，便于诊断。
 */
object CaptureMain {
    private const val TAG = "CaptureMain"

    /** 同时输出 logcat 和 stdout（stdout 会被 su -c 捕获） */
    @JvmStatic
    fun pln(s: String) {
        try { Log.i(TAG, s) } catch (_: Exception) {}
        try {
            println(s)
            System.out.flush()
        } catch (_: Exception) {}
    }

    /**
     * app_process 的 main 入口（会被反射调用，方法名必须保持 "main"）
     */
    @JvmStatic
    fun main(args: Array<String>) {
        pln("=== CaptureMain 启动 ===")
        pln("uid=" + Process.myUid() + " pid=" + Process.myPid())
        pln("args=" + args.joinToString(","))

        // app_process 环境没有 Android Runtime 上下文，需手动启 Looper
        try {
            if (Looper.myLooper() == null) {
                Looper.prepareMainLooper()
            }
            pln("Looper 已就绪")
        } catch (e: Throwable) {
            pln("Looper 初始化失败：" + e.message)
        }

        try {
            // 解析参数（key=value 形式）
            val params = mutableMapOf<String, String>()
            for (arg in args) {
                val idx = arg.indexOf('=')
                if (idx > 0) {
                    params[arg.substring(0, idx)] = arg.substring(idx + 1)
                }
            }

            val port = params["port"]?.toIntOrNull() ?: 27183
            val width = params["width"]?.toIntOrNull() ?: 0
            val height = params["height"]?.toIntOrNull() ?: 0
            val bitrate = params["bitrate"]?.toIntOrNull() ?: 2_000_000
            val fps = params["fps"]?.toIntOrNull() ?: 30
            val maxSize = params["maxSize"]?.toIntOrNull() ?: 1280
            val poc = params["poc"] == "1"

            pln("参数：port=$port width=$width height=$height bitrate=$bitrate fps=$fps maxSize=$maxSize poc=$poc")

            // 启动捕获服务（阻塞式运行）
            val server = CaptureServer(port, width, height, bitrate, fps, maxSize, poc)
            server.runBlocking()
            pln("CaptureServer 已退出")
        } catch (e: Throwable) {
            pln("★ CaptureMain 异常退出：" + e.javaClass.simpleName + ": " + e.message)
            val sw = java.io.StringWriter()
            e.printStackTrace(java.io.PrintWriter(sw))
            pln(sw.toString())
            System.exit(1)
        }
    }
}
