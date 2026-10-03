package com.autoskip.helper.root

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * Root Shell 执行封装。
 *
 * 用途：远程控制功能需要以 shell 用户（UID 2000）身份启动 app_process 捕获屏幕，
 *      以及执行 su input 命令注入触控事件。本类统一封装这些操作。
 *
 * 注意：
 * - 每条命令新建一个 su 进程，用完即关（避免持有长连接被系统回收）
 * - 超时保护：默认 5 秒，超时后 kill 进程
 * - 返回：stdout 内容（trim 后）；stderr 不单独返回，合并到 stdout
 */
object RootShell {
    private const val TAG = "RootShell"
    private const val DEFAULT_TIMEOUT_MS = 5000L

    /** 单条命令的执行结果 */
    data class Result(
        val stdout: String,
        val exitCode: Int,
        val timedOut: Boolean,
        val error: String? = null
    ) {
        val ok: Boolean get() = !timedOut && error == null && exitCode == 0
    }

    /**
     * 执行一条 su 命令（会新建 su 进程）。
     *
     * @param command 命令内容（不带 "su -c"，内部自动包裹）
     * @param timeoutMs 超时毫秒数
     */
    fun exec(command: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): Result {
        var proc: Process? = null
        return try {
            proc = ProcessBuilder("su").redirectErrorStream(true).start()
            val out = OutputStreamWriter(proc.outputStream)
            out.write(command)
            out.write("\nexit\n")
            out.flush()

            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            val sb = StringBuilder()
            val done = java.util.concurrent.CountDownLatch(1)
            val thread = Thread {
                try {
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        sb.append(line).append('\n')
                    }
                } catch (_: Exception) {
                } finally {
                    done.countDown()
                }
            }
            thread.start()

            val finished = done.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (!finished) {
                proc.destroyForcibly()
                return Result(sb.toString().trim(), -1, timedOut = true, error = "命令超时")
            }
            val exit = try { proc.waitFor() } catch (_: Exception) { -1 }
            Result(sb.toString().trim(), exit, timedOut = false)
        } catch (e: Exception) {
            Log.e(TAG, "exec 失败: ${command}", e)
            Result("", -1, timedOut = false, error = e.message ?: "未知错误")
        } finally {
            try { proc?.destroy() } catch (_: Exception) {}
        }
    }

    /**
     * 执行 su 命令并返回是否成功（用于动作类命令，不关心输出）
     */
    fun execSilently(command: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): Boolean =
        exec(command, timeoutMs).ok

    /**
     * 检查 root 是否可用（执行 "id" 看是否返回 uid=0）
     */
    fun isRootAvailable(): Boolean {
        val r = exec("id", 3000)
        return r.ok && r.stdout.contains("uid=0")
    }

    /**
     * 获取 su 版本信息（Magisk/KernelSU/SuperSU 等）
     */
    fun getSuVersion(): String {
        val r = exec("su -v", 3000)
        return r.stdout.trim()
    }
}
