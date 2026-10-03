package com.autoskip.helper.remote

import android.util.Log
import java.io.DataInputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * 连接 app_process 的捕获输出（TCP），持续读取 H.264 帧。
 *
 * ★ 协议（与 CaptureServer 一致）：
 *   [4B total_len BE]  整个记录长度（不含本字段）
 *   [8B pts_us BE]     时间戳（微秒）
 *   [4B flags BE]      bit0=关键帧，bit1=codec config
 *   [N  B data]        H.264 NAL 数据
 */
class CaptureClient(private val port: Int) {
    companion object {
        private const val TAG = "CaptureClient"
        const val FLAG_KEYFRAME = 1
        const val FLAG_CODEC_CONFIG = 2
    }

    /** 一帧视频数据 */
    data class Frame(
        val ptsUs: Long,
        val flags: Int,
        val data: ByteArray,
    ) {
        val isKeyFrame: Boolean get() = (flags and FLAG_KEYFRAME) != 0
        val isCodecConfig: Boolean get() = (flags and FLAG_CODEC_CONFIG) != 0
    }

    var onFrame: ((Frame) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    @Volatile private var running = false
    private var thread: Thread? = null
    private var socket: Socket? = null

    fun connect() {
        running = true
        thread = Thread {
            try {
                val s = Socket()
                s.connect(InetSocketAddress("127.0.0.1", port), 5000)
                s.tcpNoDelay = true
                socket = s
                Log.i(TAG, "已连接捕获端口 $port")
                val input = DataInputStream(s.getInputStream().buffered())
                var buf = ByteArray(128 * 1024)
                while (running) {
                    // 1) 读 total_len
                    val totalLen = input.readInt()
                    if (totalLen <= 12 || totalLen > 8 * 1024 * 1024) {
                        Log.w(TAG, "非法记录长：$totalLen")
                        break
                    }
                    // 2) 读 pts_us + flags
                    val ptsUs = input.readLong()
                    val flags = input.readInt()
                    // 3) 读数据体
                    val dataLen = totalLen - 12
                    if (dataLen > buf.size) {
                        buf = ByteArray(dataLen)
                    }
                    input.readFully(buf, 0, dataLen)
                    val frame = Frame(ptsUs, flags, buf.copyOf(dataLen))
                    onFrame?.invoke(frame)
                }
            } catch (e: Exception) {
                if (running) {
                    Log.e(TAG, "读取异常", e)
                    onError?.invoke(e.message ?: "未知")
                }
            } finally {
                close()
            }
        }.apply { isDaemon = true; start() }
    }

    fun close() {
        running = false
        try { socket?.close() } catch (_: Exception) {}
        socket = null
    }
}
