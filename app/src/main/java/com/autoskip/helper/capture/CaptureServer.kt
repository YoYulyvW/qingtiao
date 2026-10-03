package com.autoskip.helper.capture

import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.os.SystemClock
import android.view.Display
import android.view.Surface
import java.io.DataOutputStream
import java.net.ServerSocket

/**
 * 捕获服务：VirtualDisplay + MediaCodec H.264 硬编 → TCP 输出。
 *
 * ★ TCP 协议（帧封装）：
 *   [4B total_len BE]  整个记录长度（不含本字段）
 *   [8B pts_us BE]     MediaCodec 输出的时间戳（微秒）
 *   [4B flags BE]      bit0=关键帧，bit1=SPS/PPS（codec config）
 *   [N  B h264_data]   H.264 NAL 数据（不含起始码 0x00000001，交给接收方加）
 *
 * 简化说明：
 *   - MediaCodec 输出的数据通常以 0x00000001 起始码开头，接收方按原样处理
 *   - flags 里标记"关键帧"和"codec config"，接收方据此决定是否重置解码器
 *
 * 环境要求：
 *   - 以 shell 用户（UID 2000）运行 → 有 CREATE_VIRTUAL_DISPLAY 权限
 *   - app_process 环境无 Context，用 ActivityThread.systemMain() 反射拿系统服务
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

        const val FLAG_KEYFRAME = 1
        const val FLAG_CODEC_CONFIG = 2
    }

    private fun pln(s: String) = CaptureMain.pln(s)

    @Volatile private var running = true
    private var virtualDisplay: VirtualDisplay? = null
    private var codec: MediaCodec? = null

    fun runBlocking() {
        pln("=== CaptureServer 启动 ===")
        pln("参数：port=$port w=$requestedWidth h=$requestedHeight bitrate=$bitrate fps=$fps maxSize=$maxSize poc=$pocMode")
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    // 低延迟模式（0=realtime）
                    setInteger(MediaFormat.KEY_PRIORITY, 0)
                }
            }
            val c = MediaCodec.createEncoderByType(MIME)
            c.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface: Surface = c.createInputSurface()
            c.start()
            codec = c
            pln("MediaCodec 已启动")

            // 2) 创建 VirtualDisplay（公开 API，shell 用户有权限）
            pln("创建 VirtualDisplay…")
            val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR
            val vd = try {
                dm.createVirtualDisplay("AutoSkipCapture", w, h, dpi, inputSurface, flags)
            } catch (e: SecurityException) {
                pln("★ VirtualDisplay 创建失败：权限不足：" + e.message)
                throw e
            } catch (e: Exception) {
                pln("★ VirtualDisplay 创建失败：" + e.message)
                throw e
            }
            virtualDisplay = vd
            pln("✓ VirtualDisplay 已创建：name=${vd.display?.name} state=${vd.display?.state}")

            if (pocMode) {
                pln("=== POC 模式：创建成功，10 秒后退出 ===")
                // ★ POC 模式也顺便采集几帧，验证 MediaCodec 真能产出数据
                Thread {
                    Thread.sleep(2000)
                    pln("POC 帧采集：等待 3 秒采集编码输出…")
                }.start()
                val count = drainFrames(3000, null)
                pln("POC 3 秒内产出 $count 帧（预期 30-100）")
                Thread.sleep(7000)
                cleanup()
                pln("=== POC 完成 ===")
                return
            }

            // 3) 生产模式：等待 TCP 客户端，边编码边发送
            ServerSocket(port).use { ss ->
                pln("监听端口 $port，等待连接…")
                val client = ss.accept()
                pln("客户端已连接：${client.remoteSocketAddress}")
                client.tcpNoDelay = true
                client.use { sock ->
                    val output = DataOutputStream(sock.getOutputStream().buffered())
                    encodeLoop(output)
                }
            }
        } catch (e: Throwable) {
            val sw = java.io.StringWriter()
            e.printStackTrace(java.io.PrintWriter(sw))
            pln("★ CaptureServer 异常退出：" + e.javaClass.simpleName + ": " + e.message)
            cleanup()
            throw e
        }
        cleanup()
    }

    /** POC 模式用：抽取 N 毫秒内的帧（不写 output），返回帧数 */
    private fun drainFrames(durationMs: Long, output: DataOutputStream?): Int {
        val c = codec ?: return 0
        val bufInfo = MediaCodec.BufferInfo()
        val end = SystemClock.elapsedRealtime() + durationMs
        var count = 0
        while (SystemClock.elapsedRealtime() < end && running) {
            val outIdx = c.dequeueOutputBuffer(bufInfo, 100_000)
            when {
                outIdx >= 0 -> {
                    if (bufInfo.size > 0) {
                        count++
                        if (output != null) {
                            writeFrame(output, c, bufInfo)
                        }
                    }
                    c.releaseOutputBuffer(outIdx, false)
                }
            }
        }
        return count
    }

    private fun encodeLoop(output: DataOutputStream) {
        val c = codec ?: return
        val bufInfo = MediaCodec.BufferInfo()
        while (running) {
            val outIdx = try {
                c.dequeueOutputBuffer(bufInfo, 10_000)
            } catch (e: Exception) {
                pln("dequeue 异常：" + e.message)
                break
            }
            when {
                outIdx >= 0 -> {
                    if (bufInfo.size > 0) {
                        writeFrame(output, c, bufInfo)
                    }
                    c.releaseOutputBuffer(outIdx, false)
                }
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    pln("输出格式变化：" + c.outputFormat)
                }
            }
        }
    }

    /** ★ 写入一帧（含帧头） */
    private fun writeFrame(output: DataOutputStream, codec: MediaCodec, info: MediaCodec.BufferInfo) {
        val buf = codec.getOutputBuffer(info.outputBufferIndex) ?: return
        val data = ByteArray(info.size)
        buf.position(info.offset)
        buf.limit(info.offset + info.size)
        buf.get(data)

        // 计算 flags
        var flags = 0
        if (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0) {
            flags = flags or FLAG_KEYFRAME
        }
        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
            flags = flags or FLAG_CODEC_CONFIG
        }

        // 帧头：4B len + 8B pts_us + 4B flags = 16 字节
        val totalLen = 8 + 4 + data.size
        try {
            synchronized(output) {
                output.writeInt(totalLen)
                output.writeLong(info.presentationTimeUs)
                output.writeInt(flags)
                output.write(data)
                output.flush()
            }
        } catch (e: Exception) {
            pln("写入失败，停止：" + e.message)
            running = false
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
