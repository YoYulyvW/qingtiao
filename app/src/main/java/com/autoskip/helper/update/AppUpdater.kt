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
 * APK 下载 + 安装。
 * - 用系统 DownloadManager 下载到 Download/ 目录
 * - 下载完通过 FileProvider 触发安装
 * - 暴露 downloadProgress（0..100），供 UI 显示进度条
 */
object AppUpdater {
    private const val TAG = "AppUpdater"

    /** 下载进度状态（供 UI 观察） */
    data class Progress(
        val downloading: Boolean = false,
        val percent: Int = 0,
        val downloadedBytes: Long = 0,
        val totalBytes: Long = 0,
        val error: String? = null
    )

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress

    private var currentDownloadId: Long = -1L

    /** 下载并安装（返回 DownloadManager 的 downloadId） */
    fun downloadAndInstall(ctx: Context, url: String, version: String): Long? {
        return try {
            _progress.value = Progress(downloading = true, percent = 0)
            val fileName = "AutoSkip-${version}.apk"
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

            // 注册广播：下载完成 → 触发安装
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    val downloadId = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
                    if (downloadId != id) return
                    val ctx2 = c ?: return
                    ctx2.unregisterReceiver(this)
                    // 检查下载是否成功
                    val q = DownloadManager.Query().setFilterById(id)
                    val cursor = dm.query(q)
                    if (cursor != null && cursor.moveToFirst()) {
                        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        cursor.close()
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            _progress.value = Progress(downloading = false, percent = 100)
                            installApk(ctx2, fileName)
                            return
                        }
                    }
                    _progress.value = Progress(downloading = false, error = "下载失败，请重试")
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
            _progress.value = Progress(downloading = false, error = (e.message ?: "下载失败"))
            Toast.makeText(ctx, "下载失败：" + (e.message ?: ""), Toast.LENGTH_LONG).show()
            null
        }
    }

    /** 查询当前下载进度（由 UI 定时调用） */
    fun refreshProgress(ctx: Context) {
        if (currentDownloadId < 0) return
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
                        _progress.value = Progress(true, pct, bytesDown, bytesTotal)
                    }
                    DownloadManager.STATUS_FAILED -> {
                        _progress.value = Progress(false, error = "下载失败，请重试")
                    }
                    // SUCCESSFUL 由广播处理
                }
            } else {
                cursor.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "查询进度失败", e)
        }
    }

    /** 重置状态（用户重试 / 关闭弹窗） */
    fun reset() {
        currentDownloadId = -1L
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
