package com.autoskip.helper.remote

import android.util.Log
import java.io.DataInputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * 连接 app_process 的捕获输出（TCP），持续读取 H.264 帧。
 *
 * 协议（与 CaptureServer 一致）：
 *   [4B len BE][N B H.264 数据][4B len][...]
 */
class CaptureClient(private val port: Int) {
    companion object { private const val TAG = "CaptureClient" }

    var onFrame: ((ByteArray) -> Unit)? = null
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
                var buf = ByteArray(64 * 1024)
                while (running) {
                    val len = input.readInt()
                    if (len <= 0 || len > 4 * 1024 * 1024) {
                        Log.w(TAG, "非法帧长：$len")
                        break
                    }
                    // ★ 确保缓冲区足够大（H.264 I 帧可能超过 64KB）
                    if (len > buf.size) {
                        buf = ByteArray(len)
                    }
                    input.readFully(buf, 0, len)
                    val frame = buf.copyOf(len)
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
