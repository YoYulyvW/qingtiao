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
 *
 * 请求方式：
 *   POST <url>
 *   Content-Type: application/json; charset=utf-8
 *   Authorization: Bearer <token>   （仅当用户填写了 token）
 *   Body: {"text":"<名称>，<消息>","user":"<user>"}
 *
 * - URL 为空则不推送
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

    suspend fun send(url: String, name: String, user: String, msg: String, token: String) {
        if (url.isBlank()) return
        val text = buildText(name, msg)
        withContext(Dispatchers.IO) {
            val body = buildJson(text, user)
            for (attempt in 1..MAX_RETRY) {
                var conn: java.net.HttpURLConnection? = null
                var shouldRetry = true
                try {
                    conn = open(url, token)
                    OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(body) }
                    val code = conn.responseCode
                    Log.i(TAG, "push code=" + code + " attempt=" + attempt)
                    if (code in 200..299) return@withContext
                    // ★ 4xx 是客户端错误（参数/权限），重试无意义 → 直接放弃
                    if (code in 400..499) {
                        shouldRetry = false
                        Log.w(TAG, "push 客户端错误 " + code + "，不重试")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "push fail attempt=" + attempt + " : " + e.message)
                } finally {
                    try { conn?.disconnect() } catch (_: Exception) {}
                }
                if (!shouldRetry) return@withContext
                if (attempt < MAX_RETRY) delay(RETRY_GAP_MS)
            }
            Log.e(TAG, "push give up after " + MAX_RETRY + " attempts")
        }
    }

    /** 测试推送：返回 "成功(code)" 或 "失败: 原因" */
    suspend fun testSend(url: String, name: String, user: String, msg: String, token: String): String {
        if (url.isBlank()) return "推送地址为空"
        val text = buildText(name, msg)
        return withContext(Dispatchers.IO) {
            try {
                val conn = open(url, token)
                OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(buildJson(text, user)) }
                val code = conn.responseCode
                conn.disconnect()
                if (code in 200..299) "测试成功 (HTTP " + code + ")" else "服务端返回 HTTP " + code
            } catch (e: Exception) {
                "失败: " + (e.message ?: "未知错误")
            }
        }
    }

    private fun open(url: String, token: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (token.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer " + token)
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

    /** 完整 JSON 字符串转义：反斜杠、双引号、控制字符（换行/制表符等） */
    private fun esc(s: String): String = buildString {
        for (c in s) {
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                else -> if (c < ' ') append(String.format("\\u%04x", c.code)) else append(c)
            }
        }
    }
}
