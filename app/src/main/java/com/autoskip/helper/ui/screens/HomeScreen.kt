package com.autoskip.helper.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.autoskip.helper.service.AutoClickAccessibilityService
import com.autoskip.helper.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: MainViewModel, onOpenFenShen: () -> Unit = {}) {
    val context = LocalContext.current
    val enabled by vm.enabled.collectAsState()
    val today by vm.todayCount.collectAsState()
    val total by vm.totalCount.collectAsState()
    val delay by vm.clickDelay.collectAsState()
    val learnMode by vm.learnMode.collectAsState()
    val strictClose by vm.strictClose.collectAsState()

    var serviceOn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // 简单轮询，判断服务是否运行
        while (true) {
            serviceOn = isAccessibilityEnabled(context)
            kotlinx.coroutines.delay(1000)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("自动跳过", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        // 服务状态卡
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (serviceOn) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    if (serviceOn) "无障碍服务：已开启 ✓" else "无障碍服务：未开启 ✗",
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (serviceOn) "自动跳过已就绪，可返回桌面正常使用其他 App。"
                    else "需要开启无障碍服务才能自动点击弹窗按钮。",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = { openAccessibilitySettings(context) }) {
                    Text(if (serviceOn) "管理无障碍服务" else "去开启无障碍服务")
                }
            }
        }

        // 总开关
        Card(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("自动点击总开关", fontWeight = FontWeight.Bold)
                    Text(
                        if (enabled) "当前：开启" else "当前：已暂停",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(checked = enabled, onCheckedChange = { vm.setEnabled(it) })
            }
        }

        // 学习模式
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("学习模式", fontWeight = FontWeight.Bold)
                        Text(
                            "开启后，请到目标 App 手动点一次要跳过的按钮，会自动生成规则。",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(checked = learnMode, onCheckedChange = { vm.setLearnMode(it) })
                }
                if (learnMode) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "学习进行中…… 请切换到目标 App，点击需要自动跳过的按钮。",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // 点击延迟
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("点击延迟：${delay} 毫秒", fontWeight = FontWeight.Bold)
                Text(
                    "弹窗出现后等待该时间再点击，避免误触。网络慢的弹窗可调大。",
                    style = MaterialTheme.typography.bodySmall
                )
                Slider(
                    value = delay.toFloat(),
                    onValueChange = { vm.setClickDelay(it.toLong()) },
                    valueRange = 0f..2000f,
                    steps = 19
                )
            }
        }

        // 自动分身入口
        Card(
            Modifier.fillMaxWidth(),
            onClick = onOpenFenShen,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("自动分身（分身大师）", fontWeight = FontWeight.Bold)
                    Text(
                        "批量创建抖音分身，自动改名、安装、跳过弹窗。点击进入配置。",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text("›", style = MaterialTheme.typography.headlineSmall)
            }
        }

        // 严格模式
        Card(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("严格模式（推荐开启）", fontWeight = FontWeight.Bold)
                    Text(
                        "「关闭 / X」类按钮，只有在登录、广告等弹窗中才点；普通页面不点，避免误触。",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(checked = strictClose, onCheckedChange = { vm.setStrictClose(it) })
            }
        }

        // 统计
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.SpaceEvenly) {
                StatItem("今日跳过", today.toString())
                StatItem("累计跳过", total.toString())
            }
        }

        OutlinedButton(
            onClick = { vm.clearLogs() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("清空统计与记录")
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "小提示：为确保长期后台存活，请在系统「电池 / 应用启动管理」中将本应用设为允许自启动、不受限制。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

/** 判断无障碍服务是否开启 */
fun isAccessibilityEnabled(context: Context): Boolean {
    if (AutoClickAccessibilityService.isRunning()) return true
    val expected = context.packageName + "/" + AutoClickAccessibilityService::class.java.name
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabledServices.split(':').any { it.equals(expected, ignoreCase = true) }
}

fun openAccessibilitySettings(context: Context) {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
