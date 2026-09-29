package com.autoskip.helper.license

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * 授权服务端 HTTP 客户端。
 * - 激活 /license/activate
 * - 心跳 /license/check
 * 手拼 JSON，避免引入序列化库。
 */
object LicenseClient {
    private const val TAG = "LicenseClient"
    private const val TIMEOUT_MS = 5000

    /** 激活结果 */
    data class ActivateResult(
        val ok: Boolean,
        val message: String,
        val expireAt: Long,
        val serverNow: Long,
        val features: Map<String, Boolean>
    )

    /** 心跳结果 */
    data class CheckResult(
        val ok: Boolean,
        val reason: String,
        val expireAt: Long,
        val serverNow: Long,
        val features: Map<String, Boolean>,
        val rulesVersion: Long = 0
    )

    /** 拉取规则结果（原始 JSON，交给 RuleSync 解析） */
    data class RulesResult(
        val ok: Boolean,
        val version: Long,
        val rawJson: String
    )

    suspend fun activate(base: String, code: String, deviceId: String, deviceName: String): ActivateResult =
        withContext(Dispatchers.IO) {
            val body = "{\"code\":\"" + esc(code) + "\",\"deviceId\":\"" + esc(deviceId) +
                "\",\"deviceName\":\"" + esc(deviceName) + "\"}"
            val resp = post(base + "/license/activate", body)
            if (resp == null) {
                return@withContext ActivateResult(false, "网络错误，请检查服务端地址", 0, 0, emptyMap())
            }
            val ok = parseBool(resp, "ok")
            val msg = parseString(resp, "message")
            val expireAt = parseLong(resp, "expireAt")
            val serverNow = parseLong(resp, "serverNow")
            val features = parseFeatures(resp)
            ActivateResult(ok, msg, expireAt, serverNow, features)
        }

    suspend fun check(base: String, deviceId: String): CheckResult =
        withContext(Dispatchers.IO) {
            val body = "{\"deviceId\":\"" + esc(deviceId) + "\"}"
            val resp = post(base + "/license/check", body)
            if (resp == null) {
                // 网络错误：用 reason=network 区分
                return@withContext CheckResult(false, "network", 0, 0, emptyMap())
            }
            val ok = parseBool(resp, "ok")
            val reason = parseString(resp, "reason")
            val expireAt = parseLong(resp, "expireAt")
            val serverNow = parseLong(resp, "serverNow")
            val features = parseFeatures(resp)
            val rulesVersion = parseLong(resp, "rulesVersion")
            CheckResult(ok, reason, expireAt, serverNow, features, rulesVersion)
        }

    /** 拉取全量服务端规则 */
    suspend fun fetchRules(base: String, deviceId: String): RulesResult =
        withContext(Dispatchers.IO) {
            val body = "{"deviceId":"" + esc(deviceId) + ""}"
            val resp = post(base + "/rules/get", body)
            if (resp == null) return@withContext RulesResult(false, 0, "")
            val ok = parseBool(resp, "ok")
            val version = parseLong(resp, "version")
            RulesResult(ok, version, resp)
        }

    private fun post(url: String, body: String): String? {
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(body) }
            val code = conn.responseCode
            val text = if (code in 200..299) {
                conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            }
            conn.disconnect()
            Log.i(TAG, "POST " + url + " code=" + code)
            if (text.isBlank()) null else text
        } catch (e: Exception) {
            Log.e(TAG, "post fail: " + e.message)
            null
        }
    }

    // ===== 极简 JSON 解析（够用，不引库） =====

    private fun parseBool(json: String, key: String): Boolean {
        val m = Regex("\"" + key + "\"\\s*:\\s*(true|false)").find(json) ?: return false
        return m.groupValues[1] == "true"
    }

    private fun parseString(json: String, key: String): String {
        val m = Regex("\"" + key + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(json) ?: return ""
        return unesc(m.groupValues[1])
    }

    private fun parseLong(json: String, key: String): Long {
        val m = Regex("\"" + key + "\"\\s*:\\s*(\\d+)").find(json) ?: return 0L
        return m.groupValues[1].toLongOrNull() ?: 0L
    }

    /** 解析 features 对象：{"autoskip":true,"drama":false,...} */
    private fun parseFeatures(json: String): Map<String, Boolean> {
        val out = HashMap<String, Boolean>()
        val start = json.indexOf("\"features\"")
        if (start < 0) return out
        val braceStart = json.indexOf('{', start)
        if (braceStart < 0) return out
        val braceEnd = json.indexOf('}', braceStart)
        if (braceEnd < 0) return out
        val inner = json.substring(braceStart + 1, braceEnd)
        Regex("\"([a-z_]+)\"\\s*:\\s*(true|false)").findAll(inner).forEach {
            out[it.groupValues[1]] = it.groupValues[2] == "true"
        }
        return out
    }

    private fun esc(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun unesc(s: String): String =
        s.replace("\\\"", "\"").replace("\\\\", "\\")
}
