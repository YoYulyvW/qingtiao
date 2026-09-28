package com.autoskip.helper.service

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * 广告出现时，向用户配置的 URL 发送 POST(JSON) 推送。
 * - URL 为空则不推送
 * - 请求体：{"text":"<名称>，<消息>","user":"<user>"}
 * - 名称/消息为空时自动省略（消息默认"出现了广告窗口，请注意查看"）
 * - 单次超时 3 秒，失败重试 3 次
 * - 运行在 IO 线程，失败静默（仅记日志），不阻塞主流程
 */
object PushNotifier {
    private const val TAG = "PushNotifier"
    private const val TIMEOUT_MS = 3000
    private const val MAX_RETRY = 3
    private const val RETRY_GAP_MS = 500L

    /** 构造推送用的 text 内容 */
    private fun buildText(name: String, msg: String): String {
        val m = msg.ifBlank { "出现了广告窗口，请注意查看" }
        return if (name.isBlank()) m else name + "，" + m
    }

    suspend fun send(url: String, name: String, user: String, msg: String) {
        if (url.isBlank()) return
        val text = buildText(name, msg)
        withContext(Dispatchers.IO) {
            val body = buildJson(text, user)
            for (attempt in 1..MAX_RETRY) {
                try {
                    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = TIMEOUT_MS
                        readTimeout = TIMEOUT_MS
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    }
                    OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(body) }
                    val code = conn.responseCode
                    conn.disconnect()
                    Log.i(TAG, "push code=" + code + " attempt=" + attempt)
                    if (code in 200..299) return@withContext
                } catch (e: Exception) {
                    Log.e(TAG, "push fail attempt=" + attempt + " : " + e.message)
                }
                if (attempt < MAX_RETRY) delay(RETRY_GAP_MS)
            }
            Log.e(TAG, "push give up after " + MAX_RETRY + " attempts")
        }
    }

    /** 测试推送：返回 "成功(code)" 或 "失败: 原因" */
    suspend fun testSend(url: String, name: String, user: String, msg: String): String {
        if (url.isBlank()) return "推送地址为空"
        val text = buildText(name, msg)
        return withContext(Dispatchers.IO) {
            try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(buildJson(text, user)) }
                val code = conn.responseCode
                conn.disconnect()
                if (code in 200..299) "测试成功 (HTTP " + code + ")" else "服务端返回 HTTP " + code
            } catch (e: Exception) {
                "失败: " + (e.message ?: "未知错误")
            }
        }
    }

    /** 手动拼接 JSON，避免依赖序列化库 */
    private fun buildJson(text: String, user: String): String {
        val sb = StringBuilder()
        sb.append("{")
        sb.append("\"text\":\"").append(esc(text)).append("\"")
        sb.append(",\"user\":\"").append(esc(user)).append("\"")
        sb.append("}")
        return sb.toString()
    }

    private fun esc(s: String): String {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
    }
}
