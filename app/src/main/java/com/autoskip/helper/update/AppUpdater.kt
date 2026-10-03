package com.autoskip.helper.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * APK 下载 + 安装（下载与安装分离）。
 * - preDownload：后台静默预下载，完成后标记 readyToInstall（不自动安装）
 * - downloadAndInstall：手动下载，完成后自动调系统安装
 * - installNow：对已下载的 APK 触发系统安装
 * - 暴露 progress（下载进度 + 就绪状态）
 */
object AppUpdater {
    private const val TAG = "AppUpdater"

    /** 下载/就绪状态（供 UI 观察） */
    data class Progress(
        val downloading: Boolean = false,
        val percent: Int = 0,
        val downloadedBytes: Long = 0,
        val totalBytes: Long = 0,
        val readyToInstall: Boolean = false,   // 已下载完成，待用户点击安装
        val version: String = "",
        val error: String? = null
    )

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress

    private var currentDownloadId: Long = -1L
    private var currentFileName: String = ""
    private var currentVersion: String = ""
    /** 正在下载/已就绪的版本码，避免重复下载 */
    @Volatile private var busyVersionCode: Long = 0

    /**
     * 开始下载。
     * @param autoInstall true=下载完自动调系统安装；false=仅下载（就绪后等用户点击）
     */
    fun startDownload(ctx: Context, url: String, version: String, versionCode: Long, autoInstall: Boolean): Long? {
        // 已就绪同一版本 → 直接返回
        val cur = _progress.value
        if (cur.readyToInstall && cur.version == version) return currentDownloadId
        // 正在下载同一版本 → 跳过
        if (cur.downloading && cur.version == version) return currentDownloadId
        // 其他版本下载中 → 不干扰
        if (cur.downloading) return null

        return try {
            val fileName = "AutoSkip-${version}.apk"
            currentFileName = fileName
            currentVersion = version
            busyVersionCode = versionCode
            _progress.value = Progress(downloading = true, percent = 0, version = version)

            val req = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle("开饭小工具更新")
                setDescription("正在下载 ${version}…")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }
            val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val id = dm.enqueue(req)
            currentDownloadId = id

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    val downloadId = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
                    if (downloadId != id) return
                    val ctx2 = c ?: return
                    ctx2.unregisterReceiver(this)
                    val ok = checkSuccess(dm, id)
                    if (ok) {
                        if (autoInstall) {
                            _progress.value = Progress(downloading = false, percent = 100, version = version)
                            installApk(ctx2, fileName)
                        } else {
                            // 后台预下载：标记就绪，不自动安装
                            _progress.value = Progress(
                                downloading = false, percent = 100,
                                readyToInstall = true, version = version
                            )
                            Log.i(TAG, "预下载完成，等待用户安装：${version}")
                        }
                    } else {
                        _progress.value = Progress(downloading = false, error = "下载失败，请重试", version = version)
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= 33) {
                ctx.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
            } else {
                ctx.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
            }
            id
        } catch (e: Exception) {
            Log.e(TAG, "下载失败", e)
            _progress.value = Progress(downloading = false, error = (e.message ?: "下载失败"), version = version)
            Toast.makeText(ctx, "下载失败：" + (e.message ?: ""), Toast.LENGTH_LONG).show()
            null
        }
    }

    /** 后台预下载（不自动安装） */
    fun preDownload(ctx: Context, url: String, version: String, versionCode: Long): Long? =
        startDownload(ctx, url, version, versionCode, autoInstall = false)

    /** 手动下载并安装（下载完自动装） */
    fun downloadAndInstall(ctx: Context, url: String, version: String, versionCode: Long = 0): Long? =
        startDownload(ctx, url, version, versionCode, autoInstall = true)

    /** 对已下载的 APK 触发系统安装 */
    fun installNow(ctx: Context) {
        if (currentFileName.isBlank()) {
            Toast.makeText(ctx, "安装包不存在", Toast.LENGTH_LONG).show()
            return
        }
        installApk(ctx, currentFileName)
    }

    private fun checkSuccess(dm: DownloadManager, id: Long): Boolean {
        val cursor = dm.query(DownloadManager.Query().setFilterById(id)) ?: return false
        return if (cursor.moveToFirst()) {
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            cursor.close()
            status == DownloadManager.STATUS_SUCCESSFUL
        } else {
            cursor.close()
            false
        }
    }

    /** 查询当前下载进度（由 UI 定时调用） */
    fun refreshProgress(ctx: Context) {
        if (currentDownloadId < 0) return
        if (_progress.value.readyToInstall) return
        try {
            val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val cursor = dm.query(DownloadManager.Query().setFilterById(currentDownloadId)) ?: return
            if (cursor.moveToFirst()) {
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val bytesDown = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val bytesTotal = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                cursor.close()
                when (status) {
                    DownloadManager.STATUS_RUNNING -> {
                        val pct = if (bytesTotal > 0) ((bytesDown * 100) / bytesTotal).toInt() else 0
                        _progress.value = _progress.value.copy(
                            downloading = true, percent = pct,
                            downloadedBytes = bytesDown, totalBytes = bytesTotal, error = null
                        )
                    }
                    DownloadManager.STATUS_FAILED -> {
                        _progress.value = _progress.value.copy(downloading = false, error = "下载失败，请重试")
                    }
                }
            } else {
                cursor.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "查询进度失败", e)
        }
    }

    /** 重置状态 */
    fun reset() {
        currentDownloadId = -1L
        currentFileName = ""
        currentVersion = ""
        busyVersionCode = 0
        _progress.value = Progress()
    }

    /** 触发 APK 安装 */
    private fun installApk(ctx: Context, fileName: String) {
        try {
            val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
            if (!file.exists()) {
                Toast.makeText(ctx, "APK 文件不存在", Toast.LENGTH_LONG).show()
                return
            }
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
}
