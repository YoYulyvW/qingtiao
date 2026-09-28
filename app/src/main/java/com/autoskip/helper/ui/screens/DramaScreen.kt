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
import androidx.compose.material3.Divider
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
    val debugEnabled by vm.dramaDebug.collectAsState()
    val imgInterval by vm.dramaImgInterval.collectAsState()
    val normalInterval by vm.dramaNormalInterval.collectAsState()
    val longPressOn by vm.dramaLongPress.collectAsState()
    val clickSpeedOn by vm.dramaClickSpeed.collectAsState()
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
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 无障碍状态（紧凑）
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (serviceOn) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (serviceOn) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                        contentDescription = null,
                        modifier = Modifier.width(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (serviceOn) "无障碍服务已开启" else "需要先开启无障碍服务",
                        fontWeight = FontWeight.Bold, fontSize = 14.sp
                    )
                }
            }

            // 主开关组
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column {
                    SwitchRow("自动倍速总开关", "抖音短剧中自动切到目标倍速", dramaEnabled, 14.dp) {
                        vm.setDramaEnabled(it)
                    }
                    Divider(Modifier.padding(horizontal = 14.dp))
                    SwitchRow("自动进入短剧", "识别「短剧｜xxx」挂载并点进去", autoMount, 14.dp) {
                        vm.setDramaAutoMount(it)
                    }
                }
            }

            // 功能开关组
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column {
                    SwitchRow("自动长按", "长按视频呼出倍速菜单", longPressOn, 14.dp) {
                        vm.setDramaLongPress(it)
                    }
                    Divider(Modifier.padding(horizontal = 14.dp))
                    SwitchRow("自动点击倍数", "菜单里自动点目标倍速", clickSpeedOn, 14.dp) {
                        vm.setDramaClickSpeed(it)
                    }
                }
            }

            // 目标倍速
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text("目标倍速", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    val speeds = listOf("0.75x", "1x", "1.25x", "1.5x", "2x", "3x")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        speeds.forEach { s -> SpeedChip(s, targetSpeed, Modifier.weight(1f)) { vm.setDramaTargetSpeed(s) } }
                    }
                }
            }

            // 呼出间隔
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text("菜单呼出间隔（按集数）", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    CompactNumField("识别图片版", imgInterval.toString()) { v ->
                        if (v in 1..50) vm.setDramaImgInterval(v)
                    }
                    Spacer(Modifier.height(8.dp))
                    CompactNumField("其他菜单版", normalInterval.toString()) { v ->
                        if (v in 1..50) vm.setDramaNormalInterval(v)
                    }
                }
            }

            // 检测间隔
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(14.dp)) {
                    CompactNumField("检测间隔(ms)", intervalMs.toString()) { v ->
                        if (v in 200..5000) vm.setDramaInterval(v.toLong())
                    }
                }
            }

            // 识别日志
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("识别日志", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = debugEnabled, onCheckedChange = { vm.setDramaDebug(it) })
                            TextButton(onClick = {
                                com.autoskip.helper.service.DramaDebug.clear()
                            }) { Text("清空", fontSize = 12.sp) }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    if (debugLogs.isEmpty()) {
                        Text("暂无日志", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline)
                    } else {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        ) {
                            androidx.compose.foundation.lazy.LazyColumn(
                                Modifier.fillMaxSize().padding(8.dp)
                            ) {
                                items(debugLogs.reversed()) { line ->
                                    Text(line, fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 1.dp))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

/** 一行开关（紧凑） */
@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, pad: androidx.compose.ui.unit.Dp, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = pad, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** 紧凑数字输入行 */
@Composable
private fun CompactNumField(label: String, initial: String, onChange: (Int) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    LaunchedEffect(initial) { text = initial }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(110.dp), fontSize = 13.sp)
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                it.toIntOrNull()?.let { v -> onChange(v) }
            },
            singleLine = true,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(8.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
    }
}

@Composable
private fun SpeedChip(text: String, target: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val selected = text.equals(target, ignoreCase = true)
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) Color.White
            else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Text(text, fontSize = 12.sp, maxLines = 1)
    }
}
