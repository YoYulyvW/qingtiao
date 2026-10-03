package com.autoskip.helper.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * APK 下载 + 安装（自研下载器，不用 DownloadManager）。
 * - 后台预下载（不自动装）/ 手动下载（下完自动装）
 * - HttpURLConnection 流式下载 + 进度上报（自动跟 302）
 * - 文件写到 App 私有外部目录，安装时经 FileProvider
 */
object AppUpdater {
    private const val TAG = "AppUpdater"

    data class Progress(
        val downloading: Boolean = false,
        val percent: Int = 0,
        val downloadedBytes: Long = 0,
        val totalBytes: Long = 0,
        val readyToInstall: Boolean = false,
        val version: String = "",
        val error: String? = null
    )

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var downloadedFile: File? = null
    private var downloadVersion: String = ""

    /** 后台预下载（不自动安装） */
    fun preDownload(ctx: Context, url: String, version: String, versionCode: Long) {
        start(ctx, url, version, autoInstall = false)
    }

    /** 手动下载并安装（下完自动装） */
    fun downloadAndInstall(ctx: Context, url: String, version: String, versionCode: Long = 0) {
        start(ctx, url, version, autoInstall = true)
    }

    private fun start(ctx: Context, url: String, version: String, autoInstall: Boolean) {
        // 已就绪同一版本 → 直接装
        if (_progress.value.readyToInstall && _progress.value.version == version) {
            if (autoInstall) installNow(ctx)
            return
        }
        // 正在下载同一版本 → 忽略
        if (_progress.value.downloading && _progress.value.version == version) return
        // 有别的下载在跑 → 取消旧的
        job?.cancel()

        val appCtx = ctx.applicationContext
        downloadVersion = version
        _progress.value = Progress(downloading = true, percent = 0, version = version)

        job = scope.launch {
            val file = download(appCtx, url, version)
            if (file != null) {
                downloadedFile = file
                if (autoInstall) {
                    _progress.value = Progress(downloading = false, percent = 100,
                        readyToInstall = true, version = version)
                    withContext(Dispatchers.Main) { installNow(appCtx) }
                } else {
                    _progress.value = Progress(downloading = false, percent = 100,
                        readyToInstall = true, version = version)
                    Log.i(TAG, "预下载完成：${version}")
                }
            } else {
                _progress.value = Progress(downloading = false,
                    error = "下载失败，请重试", version = version)
            }
        }
    }

    /** 核心：流式下载（自动跟 302，更新进度） */
    private suspend fun download(ctx: Context, url: String, version: String): File? =
        withContext(Dispatchers.IO) {
            try {
                val fileName = "AutoSkip-${version}.apk"
                val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: File(ctx.filesDir, "downloads")
                dir.mkdirs()
                val file = File(dir, fileName)
                if (file.exists()) file.delete()

                // 手动处理 302（最多 5 跳）
                var currentUrl = url
                var redirects = 0
                var conn: HttpURLConnection
                while (true) {
                    conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 15000
                        readTimeout = 30000
                        instanceFollowRedirects = false   // 手动处理
                        setRequestProperty("User-Agent", "AutoSkip/Android")
                    }
                    val code = conn.responseCode
                    Log.i(TAG, "HTTP $code  $currentUrl")
                    if (code in 300..399) {
                        val loc = conn.getHeaderField("Location")
                        conn.disconnect()
                        if (loc.isNullOrBlank() || ++redirects > 5) return@withContext null
                        currentUrl = if (loc.startsWith("http")) loc
                                     else URL(URL(currentUrl), loc).toString()
                        continue
                    }
                    if (code !in 200..299) {
                        conn.disconnect()
                        return@withContext null
                    }
                    break
                }

                val total = conn.contentLengthLong
                var downloaded = 0L
                conn.inputStream.use { input ->
                    file.outputStream().use { output ->
                        val buf = ByteArray(8192)
                        while (true) {
                            if (!isActive) {
                                conn.disconnect()
                                file.delete()
                                return@withContext null
                            }
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            downloaded += n
                            val pct = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                            _progress.value = _progress.value.copy(
                                downloading = true, percent = pct,
                                downloadedBytes = downloaded,
                                totalBytes = if (total > 0) total else 0,
                                error = null
                            )
                        }
                    }
                }
                conn.disconnect()
                if (file.exists() && file.length() > 0) {
                    Log.i(TAG, "下载完成：${file.absolutePath} (${file.length()} B)")
                    file
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "下载异常", e)
                null
            }
        }

    /** 安装已下载的 APK */
    fun installNow(ctx: Context) {
        val file = downloadedFile
        if (file == null || !file.exists()) {
            Toast.makeText(ctx, "安装包不存在，请重新下载", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val uri: Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "安装失败", e)
            Toast.makeText(ctx, "安装失败：" + (e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    fun reset() {
        job?.cancel()
        _progress.value = Progress()
    }
}
