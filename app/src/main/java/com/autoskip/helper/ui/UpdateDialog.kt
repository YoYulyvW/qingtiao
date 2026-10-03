package com.autoskip.helper.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.autoskip.helper.license.LicenseManager
import com.autoskip.helper.update.AppUpdater
import kotlinx.coroutines.delay

/**
 * 强制/可选更新弹窗。
 * - force = true → 无取消按钮，无法跳过（点外部也不关）
 * - force = false → 有"稍后"按钮
 * - 点"立即下载"后显示进度条
 */
@Composable
fun UpdateDialog(info: LicenseManager.UpdateInfo) {
    val context = LocalContext.current
    val prog by AppUpdater.progress.collectAsState()

    // 进度由 AppUpdater 的 StateFlow 直接推送，无需轮询
    // （保留 LaunchedEffect 便于后续扩展）
    LaunchedEffect(Unit) {}

    Dialog(
        onDismissRequest = {
            // 强制更新：点外部不关闭（下载中也别关）
            if (!info.force && !prog.downloading) {
                LicenseManager.dismissUpdate()
                AppUpdater.reset()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !info.force && !prog.downloading,
            dismissOnClickOutside = !info.force && !prog.downloading
        )
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = com.autoskip.helper.ui.theme.HarmonyColor.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("🎉", fontSize = 42.sp)
                Text(
                    if (info.force) "必须更新" else "发现新版本",
                    fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    color = com.autoskip.helper.ui.theme.HarmonyColor.TextPrimary
                )
                Text(
                    "最新版本：${info.latestVersion}",
                    fontSize = 14.sp,
                    color = com.autoskip.helper.ui.theme.HarmonyColor.Gray4
                )
                if (info.note.isNotBlank() && !prog.downloading) {
                    Text(
                        info.note,
                        fontSize = 12.sp,
                        color = com.autoskip.helper.ui.theme.HarmonyColor.Gray6
                    )
                }
                if (info.force && !prog.downloading) {
                    Text(
                        "不更新将无法继续使用",
                        fontSize = 12.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error
                    )
                }

                Spacer(Modifier.height(4.dp))

                // ★ 状态分支：已就绪 > 下载中 > 错误 > 未开始
                if (prog.readyToInstall) {
                    // 下载完成 → 引导到文件管理器手动安装
                    Text("✅ 已下载到系统下载目录", fontSize = 13.sp,
                        color = com.autoskip.helper.ui.theme.HarmonyColor.BrandOrange,
                        fontWeight = FontWeight.Medium)
                    Text("在文件管理器点 APK 即可安装", fontSize = 11.sp,
                        color = com.autoskip.helper.ui.theme.HarmonyColor.Gray6)
                    Button(
                        onClick = { AppUpdater.openDownloadFolder(context) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = com.autoskip.helper.ui.theme.HarmonyColor.BrandOrange,
                            contentColor = com.autoskip.helper.ui.theme.HarmonyColor.White
                        )
                    ) { Text("打开文件管理器", fontSize = 15.sp, fontWeight = FontWeight.Medium) }
                } else if (prog.downloading) {
                    LinearProgressIndicator(
                        progress = { prog.percent / 100f },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = com.autoskip.helper.ui.theme.HarmonyColor.BrandOrange,
                        trackColor = com.autoskip.helper.ui.theme.HarmonyColor.IconOrangeBg
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("正在下载…", fontSize = 12.sp, color = com.autoskip.helper.ui.theme.HarmonyColor.Gray4)
                        Text(
                            if (prog.totalBytes > 0)
                                "${prog.percent}%  (${fmtSize(prog.downloadedBytes)}/${fmtSize(prog.totalBytes)})"
                            else "${prog.percent}%",
                            fontSize = 12.sp,
                            color = com.autoskip.helper.ui.theme.HarmonyColor.Gray4
                        )
                    }
                    Text("下载完会自动弹出安装", fontSize = 11.sp,
                        color = com.autoskip.helper.ui.theme.HarmonyColor.Gray6)
                } else if (prog.error != null) {
                    Text("❌ ${prog.error}", fontSize = 12.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                    Button(
                        onClick = {
                            AppUpdater.reset()
                            AppUpdater.downloadAndInstall(context, info.downloadUrl, info.latestVersion, info.latestVersionCode)
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = com.autoskip.helper.ui.theme.HarmonyColor.BrandOrange,
                            contentColor = com.autoskip.helper.ui.theme.HarmonyColor.White
                        )
                    ) { Text("重试", fontSize = 15.sp, fontWeight = FontWeight.Medium) }
                } else {
                    Button(
                        onClick = {
                            AppUpdater.downloadAndInstall(context, info.downloadUrl, info.latestVersion, info.latestVersionCode)
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = com.autoskip.helper.ui.theme.HarmonyColor.BrandOrange,
                            contentColor = com.autoskip.helper.ui.theme.HarmonyColor.White
                        )
                    ) { Text("立即下载", fontSize = 15.sp, fontWeight = FontWeight.Medium) }
                }

                if (!info.force && !prog.downloading && !prog.readyToInstall) {
                    TextButton(
                        onClick = {
                            LicenseManager.dismissUpdate()
                            AppUpdater.reset()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("稍后", fontSize = 13.sp,
                            color = com.autoskip.helper.ui.theme.HarmonyColor.Gray4)
                    }
                }
            }
        }
    }
}

/** 格式化字节数 */
private fun fmtSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        else -> String.format("%.1fMB", bytes / 1024.0 / 1024.0)
    }
}
