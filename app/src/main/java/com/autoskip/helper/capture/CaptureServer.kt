package com.autoskip.helper.capture

import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.Surface
import java.io.DataOutputStream
import java.net.ServerSocket
import java.nio.ByteBuffer

/**
 * 捕获服务（POC 版 + 生产逻辑混合）。
 *
 * 工作流程（app_process 启动）：
 *   1. 以 shell 用户身份运行（有更高权限）
 *   2. 尝试用 DisplayManager.createVirtualDisplay 创建虚拟屏幕
 *      —— 需要 android.permission.CREATE_VIRTUAL_DISPLAY 权限
 *      —— 该权限 protectionLevel 含 development，userdebug 系统可直接用；user 版可能失败
 *   3. 成功后用 MediaCodec 硬编 H.264
 *   4. 编码输出写 TCP（长度前缀帧）
 *
 * 若 VirtualDisplay 创建失败 → 日志明确报错，便于诊断权限问题。
 */
class CaptureServer(
    private val port: Int,
    private val requestedWidth: Int,
    private val requestedHeight: Int,
    private val bitrate: Int,
    private val fps: Int,
    private val maxSize: Int,
    private val pocMode: Boolean = false,  // true=只创建 VirtualDisplay 后退出（诊断模式）
) {
    companion object {
        private const val TAG = "CaptureServer"
        private const val MIME = "video/avc"
        private const val IFRAME_INTERVAL = 1
    }

    /** 统一走 CaptureMain.pln（logcat + stdout） */
    private fun pln(s: String) = CaptureMain.pln(s)

    @Volatile private var running = true
    private var virtualDisplay: VirtualDisplay? = null
    private var codec: MediaCodec? = null

    fun runBlocking() {
        pln("=== CaptureServer 启动 ===")
        pln("参数：port=$port w=$requestedWidth h=$requestedHeight bitrate=$bitrate fps=$fps maxSize=$maxSize poc=$pocMode")

        // ★ 诊断：打印当前进程信息
        pln("PID=${android.os.Process.myPid()} UID=${android.os.Process.myUid()}")

        try {
            val dm = getDisplayManager()
            val defaultDisplay = dm.getDisplay(Display.DEFAULT_DISPLAY)
            val realW = defaultDisplay.width
            val realH = defaultDisplay.height
            val metrics = android.util.DisplayMetrics()
            defaultDisplay.getMetrics(metrics)
            val dpi = metrics.densityDpi
            pln("屏幕：${realW}x${realH} dpi=$dpi")

            val (w, h) = computeSize(
                if (requestedWidth > 0) requestedWidth else realW,
                if (requestedHeight > 0) requestedHeight else realH,
                maxSize
            )
            pln("捕获尺寸：${w}x${h}")

            // 1) 创建 MediaCodec（Surface 输入）
            pln("创建 MediaCodec（H.264 硬编）…")
            val format = MediaFormat.createVideoFormat(MIME, w, h).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL)
            }
            val c = MediaCodec.createEncoderByType(MIME)
            c.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface: Surface = c.createInputSurface()
            c.start()
            codec = c
            pln("MediaCodec 已启动，Surface=$inputSurface")

            // 2) 创建 VirtualDisplay（公开 API，shell 用户可能有权）
            pln("创建 VirtualDisplay…")
            val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR
            val vd = try {
                dm.createVirtualDisplay(
                    "AutoSkipCapture",
                    w, h, dpi,
                    inputSurface,
                    flags
                )
            } catch (e: SecurityException) {
                pln("★ VirtualDisplay 创建失败：权限不足（CREATE_VIRTUAL_DISPLAY）：" + e.message)
                throw e
            } catch (e: Exception) {
                pln("★ VirtualDisplay 创建失败：" + e.message)
                throw e
            }
            virtualDisplay = vd
            pln("✓ VirtualDisplay 已创建：name=${vd.display?.name} state=${vd.display?.state}")

            if (pocMode) {
                pln("=== POC 模式：创建成功，10 秒后退出 ===")
                Thread.sleep(10_000)
                cleanup()
                pln("=== POC 完成 ===")
                return
            }

            // 3) 生产模式：等待 TCP 客户端，边编码边发送
            ServerSocket(port).use { ss ->
                pln("监听端口 $port，等待连接…")
                val client = ss.accept()
                pln("客户端已连接：${client.remoteSocketAddress}")
                client.use { sock ->
                    val output = DataOutputStream(sock.getOutputStream().buffered())
                    encodeLoop(output)
                }
            }
        } catch (e: Throwable) {
            val sw = java.io.StringWriter()
            e.printStackTrace(java.io.PrintWriter(sw))
            pln("★ CaptureServer 异常退出：" + e.javaClass.simpleName + ": " + e.message + "\n" + sw.toString())
            cleanup()
            throw e
        }
        cleanup()
    }

    private fun encodeLoop(output: DataOutputStream) {
        val c = codec ?: return
        val bufInfo = MediaCodec.BufferInfo()
        while (running) {
            val outIdx = c.dequeueOutputBuffer(bufInfo, 10_000)
            when {
                outIdx >= 0 -> {
                    val buf = c.getOutputBuffer(outIdx)
                    if (buf != null && bufInfo.size > 0) {
                        val data = ByteArray(bufInfo.size)
                        buf.position(bufInfo.offset)
                        buf.limit(bufInfo.offset + bufInfo.size)
                        buf.get(data)
                        synchronized(output) {
                            try {
                                output.writeInt(data.size)
                                output.write(data)
                                output.flush()
                            } catch (e: Exception) {
                                pln("写入失败，停止：" + e.message)
                                running = false
                            }
                        }
                    }
                    c.releaseOutputBuffer(outIdx, false)
                }
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    pln("输出格式变化：${c.outputFormat}")
                }
            }
        }
    }

    private fun cleanup() {
        running = false
        try { codec?.stop() } catch (_: Exception) {}
        try { codec?.release() } catch (_: Exception) {}
        try { virtualDisplay?.release() } catch (_: Exception) {}
        codec = null
        virtualDisplay = null
    }

    private fun computeSize(reqW: Int, reqH: Int, maxSize: Int): Pair<Int, Int> {
        val longest = maxOf(reqW, reqH)
        // 0xFFFFFFFE 转 Int（等价 -2，即最低位清零取偶数）
        val evenMask = 0xFFFFFFFE.toInt()
        if (longest <= maxSize) {
            return (reqW and evenMask) to (reqH and evenMask)
        }
        val scale = maxSize.toFloat() / longest
        val w = (reqW * scale).toInt() and evenMask
        val h = (reqH * scale).toInt() and evenMask
        return w to h
    }

    /** app_process 环境无 Context，反射拿系统服务 */
    private fun getDisplayManager(): DisplayManager {
        val atCls = Class.forName("android.app.ActivityThread")
        val systemMain = atCls.getMethod("systemMain").invoke(null)
        val ctx = atCls.getMethod("getSystemContext").invoke(systemMain)
        val getService = ctx.javaClass.getMethod("getSystemService", String::class.java)
        return getService.invoke(ctx, "display") as DisplayManager
    }
}

