package com.autoskip.helper.update

import android.app.DownloadManager
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
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
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * APK 下载（自研 HttpURLConnection）。
 * - 下载到"系统下载目录"（公共 Download）
 * - 只保留最新 APK（下载成功后删旧的 AutoSkip-*.apk）
 * - 不用 REQUEST_INSTALL_PACKAGES：用户到文件管理器手动点 APK 安装
 */
object AppUpdater {
    private const val TAG = "AppUpdater"
    private const val PREFIX = "AutoSkip-"

    data class Progress(
        val downloading: Boolean = false,
        val percent: Int = 0,
        val downloadedBytes: Long = 0,
        val totalBytes: Long = 0,
        val readyToInstall: Boolean = false,
        val version: String = "",
        val fileName: String = "",   // 下载后的文件名（用于定位）
        val error: String? = null
    )

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    /** 后台预下载（不自动安装） */
    fun preDownload(ctx: Context, url: String, version: String, versionCode: Long) {
        start(ctx, url, version)
    }

    /** 手动下载（不自动安装；下载完后由用户到文件管理器点 APK） */
    fun downloadAndInstall(ctx: Context, url: String, version: String, versionCode: Long = 0) {
        start(ctx, url, version)
    }

    private fun start(ctx: Context, url: String, version: String) {
        if (_progress.value.readyToInstall && _progress.value.version == version) return
        if (_progress.value.downloading && _progress.value.version == version) return
        job?.cancel()

        val appCtx = ctx.applicationContext
        _progress.value = Progress(downloading = true, percent = 0, version = version)

        job = scope.launch {
            val fileName = PREFIX + version + ".apk"
            val ok = download(appCtx, url, fileName)
            if (ok) {
                // 下载成功 → 清理其他旧包（只留最新的）
                cleanOldApks(appCtx, keep = fileName)
                _progress.value = Progress(
                    downloading = false, percent = 100,
                    readyToInstall = true, version = version, fileName = fileName
                )
                Log.i(TAG, "下载完成，已保存到系统下载目录：$fileName")
            } else {
                _progress.value = Progress(downloading = false, error = "下载失败，请重试", version = version)
            }
        }
    }

    /** 流式下载到系统 Download 目录（返回是否成功） */
    private suspend fun download(ctx: Context, url: String, fileName: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                var output: OutputStream? = null
                var legacyFile: File? = null

                if (Build.VERSION.SDK_INT >= 29) {
                    // Android 10+：MediaStore 写公共 Download
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    // 先删同名（避免 MediaStore 自动改名 xxx(1).apk）
                    deleteByName(ctx, fileName)
                    val uri = ctx.contentResolver.insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                    ) ?: return@withContext false
                    output = ctx.contentResolver.openOutputStream(uri)
                } else {
                    // Android 9-：直接写公共 Download 目录
                    val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    dir.mkdirs()
                    val f = File(dir, fileName)
                    if (f.exists()) f.delete()
                    legacyFile = f
                    output = f.outputStream()
                }

                if (output == null) return@withContext false

                // HTTP 下载（手动跟 302，最多 5 跳）
                var currentUrl = url
                var redirects = 0
                var conn: HttpURLConnection
                while (true) {
                    conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 15000
                        readTimeout = 30000
                        instanceFollowRedirects = false
                        setRequestProperty("User-Agent", "AutoSkip/Android")
                    }
                    val code = conn.responseCode
                    Log.i(TAG, "HTTP $code  $currentUrl")
                    if (code in 300..399) {
                        val loc = conn.getHeaderField("Location")
                        conn.disconnect()
                        if (loc.isNullOrBlank() || ++redirects > 5) {
                            output.close()
                            legacyFile?.delete()
                            return@withContext false
                        }
                        currentUrl = if (loc.startsWith("http")) loc
                                     else URL(URL(currentUrl), loc).toString()
                        continue
                    }
                    if (code !in 200..299) {
                        conn.disconnect()
                        output.close()
                        legacyFile?.delete()
                        return@withContext false
                    }
                    break
                }

                val total = conn.contentLengthLong
                var downloaded = 0L
                var success = false
                try {
                    conn.inputStream.use { input ->
                        output.use { out ->
                            val buf = ByteArray(8192)
                            while (true) {
                                if (!isActive) return@withContext false
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
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
                    success = downloaded > 0
                } catch (e: Exception) {
                    Log.e(TAG, "写文件异常", e)
                } finally {
                    conn.disconnect()
                }
                if (!success) legacyFile?.delete()
                success
            } catch (e: Exception) {
                Log.e(TAG, "下载异常", e)
                false
            }
        }

    /** 删除指定文件名的下载（MediaStore，Android 10+） */
    private fun deleteByName(ctx: Context, fileName: String) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                val sel = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
                ctx.contentResolver.delete(collection, sel, arrayOf(fileName))
            }
        } catch (_: Exception) {}
    }

    /** 清理旧的 AutoSkip-*.apk（保留 keep 指定的那个） */
    private fun cleanOldApks(ctx: Context, keep: String) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME)
                val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
                val args = arrayOf("$PREFIX%.apk")
                ctx.contentResolver.query(collection, projection, selection, args, null)?.use { cur ->
                    while (cur.moveToNext()) {
                        val id = cur.getLong(0)
                        val name = cur.getString(1) ?: continue
                        if (name == keep) continue
                        val uri = ContentUris.withAppendedId(collection, id)
                        ctx.contentResolver.delete(uri, null, null)
                        Log.i(TAG, "已删除旧包：$name")
                    }
                }
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.listFiles()?.forEach { f ->
                    if (f.name.startsWith(PREFIX) && f.name.endsWith(".apk") && f.name != keep) {
                        if (f.delete()) Log.i(TAG, "已删除旧包：${f.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "清理旧包失败", e)
        }
    }

    /** 打开系统文件管理器（定位到下载目录），用户手动点 APK 安装 */
    fun openDownloadFolder(ctx: Context) {
        try {
            val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "打开下载目录失败", e)
            Toast.makeText(ctx, "请到系统「文件管理」→「下载」目录手动安装", Toast.LENGTH_LONG).show()
        }
    }

    fun reset() {
        job?.cancel()
        _progress.value = Progress()
    }
}
