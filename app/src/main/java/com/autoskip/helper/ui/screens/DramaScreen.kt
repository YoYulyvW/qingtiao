package com.autoskip.helper.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autoskip.helper.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DramaScreen(onBack: () -> Unit, vm: MainViewModel) {
    val context = LocalContext.current
    val dramaEnabled by vm.dramaEnabled.collectAsState()
    val autoMount by vm.dramaAutoMount.collectAsState()
    val intervalMs by vm.dramaIntervalMs.collectAsState()
    val targetSpeed by vm.dramaTargetSpeed.collectAsState()
    val debugLogs by com.autoskip.helper.service.DramaDebug.logs.collectAsState()

    var serviceOn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            serviceOn = isAccessibilityEnabled(context)
            kotlinx.coroutines.delay(1000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("短剧自动倍速", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 无障碍状态
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (serviceOn) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (serviceOn) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (serviceOn) "无障碍服务已开启" else "需要先开启无障碍服务",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 总开关
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("自动倍速总开关", fontWeight = FontWeight.Bold)
                        Text(
                            "开启后，在抖音短剧中自动把倍速切到你设定的值。",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(checked = dramaEnabled, onCheckedChange = { vm.setDramaEnabled(it) })
                }
            }

            // 自动挂载
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("自动进入短剧", fontWeight = FontWeight.Bold)
                        Text(
                            "在普通视频流中自动识别「短剧｜xxx」挂载并点进去。关闭则只在你手动进入短剧后加速。",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(checked = autoMount, onCheckedChange = { vm.setDramaAutoMount(it) })
                }
            }

            // 目标倍速
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("目标倍速", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "可选 0.75x / 1x / 1.25x / 1.5x / 2x / 3x。推荐 3x。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    val row1 = listOf("0.75x", "1x", "1.25x")
                    val row2 = listOf("1.5x", "2x", "3x")
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row1.forEach { s -> SpeedChip(s, targetSpeed) { vm.setDramaTargetSpeed(s) } }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row2.forEach { s -> SpeedChip(s, targetSpeed) { vm.setDramaTargetSpeed(s) } }
                        }
                    }
                }
            }

            // 检测间隔
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("检测间隔", fontWeight = FontWeight.Bold)
                    Text(
                        "越小越灵敏，推荐 500~1000 毫秒。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    var intervalText by remember { mutableStateOf(intervalMs.toString()) }
                    LaunchedEffect(intervalMs) { intervalText = intervalMs.toString() }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = intervalText,
                            onValueChange = {
                                intervalText = it
                                it.toLongOrNull()?.let { ms ->
                                    if (ms in 200..5000) vm.setDramaInterval(ms)
                                }
                            },
                            label = { Text("毫秒") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                }
            }

            // 调试日志
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("识别日志", fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.TextButton(onClick = {
                                com.autoskip.helper.service.DramaDebug.clear()
                            }) { Text("清空", fontSize = 12.sp) }
                        }
                    }
                    Text(
                        "每秒检测一次，每 3 秒输出一条诊断。用来查看识别到了什么。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(8.dp))

                    if (debugLogs.isEmpty()) {
                        Text("暂无日志。开启总开关后，在抖音里会自动输出。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline)
                    } else {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(8.dp)
                                )
                        ) {
                            androidx.compose.foundation.lazy.LazyColumn(
                                Modifier.fillMaxSize().padding(8.dp)
                            ) {
                                items(debugLogs.reversed()) { line ->
                                    Text(
                                        line,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "说明：抖音里「3x 再点会变 0.75x」，所以已到目标倍速时会自动停止点击，不会把你切回低速。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SpeedChip(text: String, target: String, onClick: () -> Unit) {
    val selected = text.equals(target, ignoreCase = true)
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) Color.White
            else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 16.dp, vertical = 6.dp
        )
    ) {
        Text(text, fontSize = 13.sp)
    }
}
