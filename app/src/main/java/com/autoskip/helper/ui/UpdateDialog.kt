package com.autoskip.helper.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.autoskip.helper.license.LicenseManager

/**
 * 更新提示弹窗（统一"联系管理员"，App 内不下载）。
 * - force = true  → 必须更新，无"稍后"（不可关）
 * - force = false → 可"稍后"，不更新可继续使用功能
 */
@Composable
fun UpdateDialog(info: LicenseManager.UpdateInfo) {
    Dialog(
        onDismissRequest = {
            // 非强制：点外部/返回可关闭
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
                Text(if (info.force) "🔒" else "🎉", fontSize = 42.sp)
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

                Spacer(Modifier.height(4.dp))

                Text(
                    "请联系管理员获取最新安装包",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (info.force) androidx.compose.material3.MaterialTheme.colorScheme.error
                            else com.autoskip.helper.ui.theme.HarmonyColor.TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                if (info.force) {
                    Text(
                        "未更新前，跳过功能已暂停",
                        fontSize = 12.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        "不更新可继续使用",
                        fontSize = 12.sp,
                        color = com.autoskip.helper.ui.theme.HarmonyColor.Gray6,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (!info.force) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(
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
