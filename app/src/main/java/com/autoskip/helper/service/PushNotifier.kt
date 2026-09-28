package com.autoskip.helper.service

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * 广告出现时，向用户配置的 URL 发送 POST(JSON) 推送。
 * - URL 为空则不推送
 * - 请求体：{"text":"<名称>，出现了广告窗口，请留意"}
 * - 失败静默（仅记日志），不影响主流程
 */
object PushNotifier {
    private const val TAG = "PushNotifier"
    private const val SUFFIX = "，出现了广告窗口，请留意"

    suspend fun send(url: String, name: String) {
        if (url.isBlank()) return
        val text = if (name.isBlank()) "出现了广告窗口，请留意" else name + SUFFIX
        try {
            withContext(Dispatchers.IO) {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 8000
                    readTimeout = 8000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                val body = "{\"text\":\"" + esc(text) + "\"}"
                OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(body) }
                val code = conn.responseCode
                Log.i(TAG, "push code=" + code)
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "push fail: " + e.message)
        }
    }

    private fun esc(s: String): String {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
    }
}
