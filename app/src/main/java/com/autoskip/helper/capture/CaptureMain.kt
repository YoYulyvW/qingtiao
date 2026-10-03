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
     * POC=2：测试 app_process 环境能否加载 WebRTC 类（决定 P3 架构方向）
     */
    private fun testWebRTCLoad() {
        pln("=== POC=2：测试 WebRTC 加载 ===")

        // 1) 检查 .so 库路径（app_process 不会自动加载 APK 的 native 库）
        val nativeLibDir = try {
            val atCls = Class.forName("android.app.ActivityThread")
            val systemMain = atCls.getMethod("systemMain").invoke(null)
            val appInfo = systemMain.javaClass.getMethod("getApplicationInfo").invoke(systemMain)
            val srcDir = appInfo.javaClass.getField("nativeLibraryDir").get(appInfo) as String
            pln("nativeLibraryDir=$srcDir")
            srcDir
        } catch (e: Throwable) {
            pln("获取 nativeLibraryDir 失败：" + e.message)
            ""
        }

        // 2) 尝试设置 java.library.path
        try {
            System.setProperty("java.library.path", nativeLibDir + ":" + System.getProperty("java.library.path"))
            pln("已设置 java.library.path")
        } catch (e: Throwable) {
            pln("设置 java.library.path 失败：" + e.message)
        }

        // 3) 检查 APK 里有哪些 .so
        try {
            val apkPath = System.getProperty("java.class.path") ?: ""
            pln("CLASSPATH=$apkPath")
            // 简单检查 APK 内 lib 目录
            val zf = java.util.zip.ZipFile(apkPath.split(":").firstOrNull { it.endsWith(".apk") } ?: apkPath)
            val libs = zf.entries().toList()
                .filter { it.name.startsWith("lib/") && it.name.endsWith(".so") }
                .map { it.name }
            pln("APK 内 .so 数量：${libs.size}")
            libs.take(20).forEach { pln("  $it") }
            if (libs.size > 20) pln("  ... 还有 ${libs.size - 20} 个")
            zf.close()
        } catch (e: Throwable) {
            pln("列 .so 失败：" + e.message)
        }

        // 4) 尝试加载 WebRTC 核心类
        val classesToTest = listOf(
            "org.webrtc.PeerConnectionFactory",
            "org.webrtc.SurfaceTextureHelper",
            "org.webrtc.VideoSource",
            "org.webrtc.VideoTrack",
            "org.webrtc.DataChannel",
            "org.webrtc.SessionDescription"
        )
        for (cn in classesToTest) {
            try {
                val cls = Class.forName(cn)
                pln("✓ $cn 加载成功")
            } catch (e: Throwable) {
                pln("✗ $cn 加载失败：" + e.javaClass.simpleName + ": " + e.message)
            }
        }

        // 5) 尝试真正初始化 WebRTC（会触发 JNI .so 加载）
        try {
            org.webrtc.PeerConnectionFactory.initialize(
                org.webrtc.PeerConnectionFactory.InitializationOptions.builder(
                    getSystemContext()
                ).createInitializationOptions()
            )
            pln("✓ WebRTC PeerConnectionFactory.initialize 成功")
        } catch (e: Throwable) {
            pln("✗ WebRTC 初始化失败：" + e.javaClass.simpleName + ": " + e.message)
            val sw = java.io.StringWriter()
            e.printStackTrace(java.io.PrintWriter(sw))
            pln(sw.toString().take(500))
        }

        pln("=== POC=2 完成 ===")
    }

    /** 获取系统 Context（app_process 环境） */
    private fun getSystemContext(): android.content.Context {
        val atCls = Class.forName("android.app.ActivityThread")
        val systemMain = atCls.getMethod("systemMain").invoke(null)
        return atCls.getMethod("getSystemContext").invoke(systemMain) as android.content.Context
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
            val poc2 = params["poc"] == "2"

            pln("参数：port=$port width=$width height=$height bitrate=$bitrate fps=$fps maxSize=$maxSize poc=$poc poc2=$poc2")

            // POC=2：测试 app_process 里能否加载 WebRTC
            if (poc2) {
                testWebRTCLoad()
                return
            }

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
