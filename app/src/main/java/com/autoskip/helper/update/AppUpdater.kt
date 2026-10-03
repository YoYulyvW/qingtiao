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
import java.io.File

/**
 * APK 下载 + 安装。
 * - 用系统 DownloadManager 下载到 Download/ 目录
 * - 下载完通过 FileProvider 触发安装
 */
object AppUpdater {
    private const val TAG = "AppUpdater"

    /** 下载并安装（返回 DownloadManager 的 downloadId） */
    fun downloadAndInstall(ctx: Context, url: String, version: String): Long? {
        return try {
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

            // 注册广播：下载完成 → 触发安装
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    val downloadId = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
                    if (downloadId != id) return
                    val ctx2 = c ?: return
                    ctx2.unregisterReceiver(this)
                    installApk(ctx2, fileName)
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
            Toast.makeText(ctx, "下载失败：" + (e.message ?: ""), Toast.LENGTH_LONG).show()
            null
        }
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
