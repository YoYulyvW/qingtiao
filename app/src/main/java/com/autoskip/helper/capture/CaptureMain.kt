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
 * - 参数用 JSON 传递（端口、宽高、码率等）
 *
 * ⚠️ 该进程独立于 App 主进程运行。通信方式：本地 TCP socket（App 主进程作为客户端连接）。
 */
object CaptureMain {
    private const val TAG = "CaptureMain"

    /**
     * app_process 的 main 入口（会被反射调用，方法名必须保持 "main"）
     */
    @JvmStatic
    fun main(args: Array<String>) {
        Log.i(TAG, "CaptureMain started, uid=" + Process.myUid() + ", args=" + args.joinToString(","))

        // app_process 环境没有 Android Runtime 上下文，需手动启 Looper
        if (Looper.myLooper() == null) {
            Looper.prepareMainLooper()
        }

        try {
            // 解析参数（最简单的 key=value 形式）
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

            Log.i(TAG, "参数：port=$port width=$width height=$height bitrate=$bitrate fps=$fps maxSize=$maxSize poc=$poc")

            // 启动捕获服务（阻塞式运行）
            val server = CaptureServer(port, width, height, bitrate, fps, maxSize, poc)
            server.runBlocking()
        } catch (e: Throwable) {
            Log.e(TAG, "CaptureMain 异常退出", e)
            System.exit(1)
        }
    }
}
