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
 * - 请求体：{"text":"<名称>，出现了广告窗口，请留意"}
 * - 单次超时 3 秒，失败重试，最多 3 次
 * - 运行在 IO 线程，失败静默（仅记日志），不阻塞主流程/节点处理
 */
object PushNotifier {
    private const val TAG = "PushNotifier"
    private const val SUFFIX = "，出现了广告窗口，请留意"
    private const val TIMEOUT_MS = 3000
    private const val MAX_RETRY = 3
    private const val RETRY_GAP_MS = 500L

    suspend fun send(url: String, name: String) {
        if (url.isBlank()) return
        val text = if (name.isBlank()) "出现了广告窗口，请留意" else name + SUFFIX
        withContext(Dispatchers.IO) {
            for (attempt in 1..MAX_RETRY) {
                try {
                    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = TIMEOUT_MS
                        readTimeout = TIMEOUT_MS
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    }
                    val body = "{\"text\":\"" + esc(text) + "\"}"
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

    private fun esc(s: String): String {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
    }
}
