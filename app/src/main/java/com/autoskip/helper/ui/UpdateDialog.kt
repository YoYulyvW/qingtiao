package com.autoskip.helper.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

/**
 * 强制/可选更新弹窗。
 * - force = true → 无取消按钮，无法跳过（点外部也不关）
 * - force = false → 有"稍后"按钮
 */
@Composable
fun UpdateDialog(info: LicenseManager.UpdateInfo) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = {
            // 强制更新：点外部不关闭
            if (!info.force) LicenseManager.dismissUpdate()
        },
        properties = DialogProperties(
            dismissOnBackPress = !info.force,
            dismissOnClickOutside = !info.force
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
                if (info.note.isNotBlank()) {
                    Text(
                        info.note,
                        fontSize = 12.sp,
                        color = com.autoskip.helper.ui.theme.HarmonyColor.Gray6
                    )
                }
                if (info.force) {
                    Text(
                        "不更新将无法继续使用",
                        fontSize = 12.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error
                    )
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = {
                        AppUpdater.downloadAndInstall(context, info.downloadUrl, info.latestVersion)
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = com.autoskip.helper.ui.theme.HarmonyColor.BrandOrange,
                        contentColor = com.autoskip.helper.ui.theme.HarmonyColor.White
                    )
                ) {
                    Text("立即下载", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }

                if (!info.force) {
                    androidx.compose.material3.TextButton(
                        onClick = { LicenseManager.dismissUpdate() },
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
