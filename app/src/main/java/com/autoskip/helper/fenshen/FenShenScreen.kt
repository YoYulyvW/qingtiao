package com.autoskip.helper.fenshen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FenShenScreen(onBack: () -> Unit, vm: FenShenViewModel = viewModel()) {
    val context = LocalContext.current
    val state by vm.state.collectAsState()

    var totalCount by remember { mutableStateOf("20") }
    var suffixFmt by remember { mutableStateOf("-{date}号") }
    var installTimeout by remember { mutableStateOf("60") }
    var retryTimes by remember { mutableStateOf("1") }
    var maxFail by remember { mutableStateOf("3") }
    var saveLog by remember { mutableStateOf(true) }
    var testW by remember { mutableStateOf("1080") }
    var testH by remember { mutableStateOf("1920") }

    var serviceOn by remember { mutableStateOf(false) }
    var overlayOn by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            serviceOn = vm.isServiceEnabled()
            overlayOn = vm.hasOverlayPermission()
            kotlinx.coroutines.delay(1000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("自动分身（分身大师）") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 权限状态
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (serviceOn && overlayOn) MaterialTheme.colorScheme.surfaceVariant
                    else MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        when {
                            !serviceOn && !overlayOn -> "需要开启：无障碍服务 + 悬浮窗权限"
                            !serviceOn -> "需要开启：分身无障碍服务"
                            !overlayOn -> "需要开启：悬浮窗权限"
                            else -> "权限已就绪 ✓"
                        },
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!serviceOn) {
                            Button(onClick = {
                                val i = android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(i)
                            }) { Text("开启无障碍") }
                        }
                        if (!overlayOn) {
                            Button(onClick = { vm.requestOverlayPermission() }) { Text("开启悬浮窗") }
                        }
                    }
                }
            }

            // 运行状态
            if (state.running || state.currentIndex > 0) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(state.statusText, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("成功 ${state.successCount} / 失败 ${state.failCount}", style = MaterialTheme.typography.bodySmall)
                        if (state.lastLog.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(state.lastLog, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline, maxLines = 2)
                        }
                    }
                }
            }

            Text("基础配置", fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("分身总数", Modifier.padding(end = 8.dp))
                OutlinedTextField(
                    value = totalCount, onValueChange = { totalCount = it },
                    singleLine = true, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("后缀格式", Modifier.padding(end = 8.dp))
                OutlinedTextField(
                    value = suffixFmt, onValueChange = { suffixFmt = it },
                    singleLine = true, modifier = Modifier.weight(1f)
                )
            }
            Text("支持 {date} 日期、{date2} 补零日期、{time} 时间", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline)

            Text("高级配置", fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("安装超时(秒)", Modifier.padding(end = 8.dp))
                OutlinedTextField(
                    value = installTimeout, onValueChange = { installTimeout = it },
                    singleLine = true, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("失败重试次数", Modifier.padding(end = 8.dp))
                OutlinedTextField(
                    value = retryTimes, onValueChange = { retryTimes = it },
                    singleLine = true, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("连续失败停止", Modifier.padding(end = 8.dp))
                OutlinedTextField(
                    value = maxFail, onValueChange = { maxFail = it },
                    singleLine = true, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("保存详细日志", Modifier.weight(1f))
                Switch(checked = saveLog, onCheckedChange = { saveLog = it })
            }

            Text("测试机分辨率（一般不用改）", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = testW, onValueChange = { testW = it },
                    singleLine = true, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Text("  ×  ", Modifier.padding(horizontal = 4.dp))
                OutlinedTextField(
                    value = testH, onValueChange = { testH = it },
                    singleLine = true, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            Spacer(Modifier.height(8.dp))

            if (state.running) {
                Button(
                    onClick = { vm.stop() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("终止运行") }
            } else {
                Button(
                    onClick = {
                        val cfg = FenShenConfig(
                            totalCount = totalCount.toIntOrNull() ?: 20,
                            suffixFmt = suffixFmt.ifBlank { "-{date}号" },
                            installTimeoutSec = installTimeout.toIntOrNull() ?: 60,
                            retryTimes = retryTimes.toIntOrNull() ?: 0,
                            maxFail = maxFail.toIntOrNull() ?: 3,
                            saveLog = saveLog,
                            testW = testW.toIntOrNull() ?: 1080,
                            testH = testH.toIntOrNull() ?: 1920
                        )
                        vm.start(cfg) { vm.requestOverlayPermission() }
                    },
                    enabled = serviceOn,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("开始运行") }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "提示：运行时会弹出悬浮窗显示进度，可暂停/终止。请保持分身大师和抖音的分身权限正常。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
