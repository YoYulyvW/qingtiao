package com.autoskip.helper.license

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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
        val rulesVersion: Long = 0,
        /** ★ 服务端下发的候选心跳域名（null=字段缺失，保留本地缓存；空数组=清空） */
        val endpoints: List<String>? = null
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
            val endpoints = parseEndpoints(resp)
            CheckResult(ok, reason, expireAt, serverNow, features, rulesVersion, endpoints)
        }

    /** 拉取全量服务端规则 */
    suspend fun fetchRules(base: String, deviceId: String): RulesResult =
        withContext(Dispatchers.IO) {
            val body = "{\"deviceId\":\"" + esc(deviceId) + "\"}"
            val resp = post(base + "/rules/get", body)
            if (resp == null) return@withContext RulesResult(false, 0, "")
            val ok = parseBool(resp, "ok")
            val version = parseLong(resp, "version")
            RulesResult(ok, version, resp)
        }

    /**
     * 解析 endpoints 数组：{"endpoints":["https://a","https://b"]}
     * 返回 null 表示字段不存在（保留本地缓存）；返回空数组表示服务端明确清空。
     */
    private fun parseEndpoints(json: String): List<String>? {
        val idx = json.indexOf("\"endpoints\"")
        if (idx < 0) return null
        val arrStart = json.indexOf('[', idx)
        if (arrStart < 0) return null
        val arrEnd = json.indexOf(']', arrStart)
        if (arrEnd < 0) return null
        val inner = json.substring(arrStart + 1, arrEnd).trim()
        if (inner.isEmpty()) return emptyList()
        val out = ArrayList<String>()
        // 匹配 "..." （字符串项）
        Regex("\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(inner).forEach {
            val s = unesc(it.groupValues[1]).trim()
            if (s.startsWith("http://") || s.startsWith("https://")) out.add(s)
        }
        return out
    }

    /**
     * 并发探测候选域名，返回延迟最低的。
     * - 用 GET / 检测 200，超时 3 秒
     * - 全部失败 → 返回第一个
     */
    suspend fun probeBest(candidates: List<String>): String = withContext(Dispatchers.IO) {
        val list = candidates.map { it.trim().trimEnd('/') }.filter { it.isNotBlank() }.distinct()
        if (list.isEmpty()) return@withContext ""
        val results = list.map { url ->
            async(Dispatchers.IO) {
                val t0 = System.currentTimeMillis()
                val ok = try {
                    val conn = (URL("$url/").openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 3000
                        readTimeout = 3000
                    }
                    val code = conn.responseCode
                    try { conn.disconnect() } catch (_: Exception) {}
                    code == 200
                } catch (e: Exception) { false }
                url to if (ok) (System.currentTimeMillis() - t0) else -1L
            }
        }.map { it.await() }
        val okList = results.filter { it.second >= 0 }
        Log.i(TAG, "probe: " + results.joinToString { it.first + "=" + it.second + "ms" })
        okList.minByOrNull { it.second }?.first ?: list.first()
    }

    private fun post(url: String, body: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
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
            Log.i(TAG, "POST " + url + " code=" + code)
            if (text.isBlank()) null else text
        } catch (e: Exception) {
            Log.e(TAG, "post fail: " + e.message)
            null
        } finally {
            // ★ 无论成功失败都断开连接，防泄漏
            try { conn?.disconnect() } catch (_: Exception) {}
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

    private fun unesc(s: String): String =
        s.replace("\\\"", "\"").replace("\\\\", "\\")
}
