package com.autoskip.helper.root

import android.util.Log
import java.io.File

/**
 * Root 权限检测。
 *
 * 检测策略（多信号综合，避免单一误判）：
 * 1. su 二进制路径检测（常见路径 /system/bin/su、/system/xbin/su、/sbin/su 等）
 * 2. 执行 su -c id 命令，看是否返回 uid=0（最可靠，但会弹 Root 授权窗）
 * 3. Magisk/KernelSU 特有路径检测（/data/adb/magisk 等）
 *
 * 注意：
 * - 检测会触发 Root 授权提示（第一次），用户拒绝后 isRooted 返回 false
 * - 结果缓存 30 秒，避免频繁弹窗
 */
object RootChecker {
    private const val TAG = "RootChecker"
    private const val CACHE_TTL_MS = 30_000L

    @Volatile private var cachedResult: Boolean = false
    @Volatile private var cachedAt: Long = 0L

    /** 常见 su 二进制路径 */
    private val SU_PATHS = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/system/sbin/su",
        "/sbin/su",
        "/vendor/bin/su",
        "/su/bin/su",
        "/magisk/.core/bin/su"
    )

    /** Magisk / KernelSU 特征路径 */
    private val ROOT_MARKERS = listOf(
        "/data/adb/magisk",
        "/data/adb/ksu",
        "/data/adb/modules",
        "/sbin/.magisk",
        "/cache/.disable_magisk",
        "/dev/.magisk.unblock",
        "/system/app/Superuser.apk",
        "/system/etc/init.d/99SuperSUDaemon"
    )

    /**
     * 判断设备是否已 Root。
     *
     * @param force 是否强制重新检测（忽略缓存）
     * @param checkByExec 是否执行 "su -c id" 检测（会弹授权窗）。
     *                    建议：首次启动用 true，之后用 false（仅路径检测）。
     */
    fun isRooted(force: Boolean = false, checkByExec: Boolean = true): Boolean {
        val now = System.currentTimeMillis()
        if (!force && now - cachedAt < CACHE_TTL_MS) {
            return cachedResult
        }
        val result = detect(checkByExec)
        cachedResult = result
        cachedAt = now
        Log.i(TAG, "Root 检测结果：$result")
        return result
    }

    /** 清除缓存（用户手动重新检测时用） */
    fun clearCache() {
        cachedAt = 0L
    }

    /** 完整检测 */
    private fun detect(checkByExec: Boolean): Boolean {
        // 1) su 二进制存在？
        val hasSuBinary = SU_PATHS.any { File(it).exists() }
        // 2) Magisk / KernelSU 特征？
        val hasRootMarker = ROOT_MARKERS.any { File(it).exists() }
        // 3) 执行 su -c id（最可靠，但会弹窗）
        val execOk = if (checkByExec) RootShell.isRootAvailable() else false

        // 任一为真 → Rooted
        return execOk || hasSuBinary || hasRootMarker
    }

    /** 返回 Root 详情（诊断用） */
    fun getRootInfo(): Map<String, Any> {
        val suBinary = SU_PATHS.firstOrNull { File(it).exists() } ?: "(未找到)"
        val marker = ROOT_MARKERS.firstOrNull { File(it).exists() } ?: "(未找到)"
        val suVersion = try { RootShell.getSuVersion() } catch (_: Exception) { "(获取失败)" }
        return mapOf(
            "rooted" to cachedResult,
            "suBinary" to suBinary,
            "rootMarker" to marker,
            "suVersion" to suVersion
        )
    }
}
