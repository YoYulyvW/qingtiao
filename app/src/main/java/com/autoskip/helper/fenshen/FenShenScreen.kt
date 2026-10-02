package com.autoskip.helper.fenshen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
    var installX by remember { mutableStateOf("760") }
    var installY by remember { mutableStateOf("1620") }
    var permX by remember { mutableStateOf("540") }
    var permY by remember { mutableStateOf("1465") }
    var locX by remember { mutableStateOf("523") }
    var locY by remember { mutableStateOf("1308") }

    var serviceOn by remember { mutableStateOf(false) }
    var overlayOn by remember { mutableStateOf(false) }
    var useStopIndex by remember { mutableStateOf(false) }
    var stopIndex by remember { mutableStateOf("50") }
    var logHeight by remember { mutableStateOf("90") }
    var loaded by remember { mutableStateOf(false) }

    // 首次进入：加载已保存配置
    LaunchedEffect(Unit) {
        vm.loadConfig { c ->
            totalCount = c.totalCount.toString()
            suffixFmt = c.suffixFmt
            installTimeout = c.installTimeoutSec.toString()
            retryTimes = c.retryTimes.toString()
            maxFail = c.maxFail.toString()
            saveLog = c.saveLog
            testW = c.testW.toString()
            testH = c.testH.toString()
            installX = c.installX.toString()
            installY = c.installY.toString()
            permX = c.permX.toString()
            permY = c.permY.toString()
            locX = c.locX.toString()
            locY = c.locY.toString()
            useStopIndex = c.useStopIndex
            stopIndex = c.stopIndex.toString()
            logHeight = c.logHeightDp.toString()
            loaded = true
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            serviceOn = vm.isServiceEnabled()
            overlayOn = vm.hasOverlayPermission()
            kotlinx.coroutines.delay(1000)
        }
    }

    fun buildConfig() = FenShenConfig(
        totalCount = totalCount.toIntOrNull() ?: 20,
        suffixFmt = suffixFmt.ifBlank { "-{date}号" },
        installTimeoutSec = installTimeout.toIntOrNull() ?: 60,
        retryTimes = retryTimes.toIntOrNull() ?: 0,
        maxFail = maxFail.toIntOrNull() ?: 3,
        saveLog = saveLog,
        testW = testW.toIntOrNull() ?: 1080,
        testH = testH.toIntOrNull() ?: 1920,
        installX = installX.toIntOrNull() ?: 760,
        installY = installY.toIntOrNull() ?: 1620,
        permX = permX.toIntOrNull() ?: 540,
        permY = permY.toIntOrNull() ?: 1465,
        logHeightDp = logHeight.toIntOrNull() ?: 90,
        locX = locX.toIntOrNull() ?: 523,
        locY = locY.toIntOrNull() ?: 1308,
        useStopIndex = useStopIndex,
        stopIndex = stopIndex.toIntOrNull() ?: 50
    )

    Scaffold(
        modifier = Modifier.statusBarsPadding(),
        topBar = {
            TopAppBar(
                title = { Text("自动分身 · 分身大师", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = com.autoskip.helper.ui.theme.HarmonyColor.White,
                    titleContentColor = com.autoskip.helper.ui.theme.HarmonyColor.TextPrimary,
                    navigationIconContentColor = com.autoskip.helper.ui.theme.HarmonyColor.TextPrimary
                )
            )
        },
        bottomBar = {
            // 开始/终止按钮常驻底部
            Surface(color = com.autoskip.helper.ui.theme.HarmonyColor.White, shadowElevation = 8.dp) {
                Box(Modifier.fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(com.autoskip.helper.ui.theme.Dimens.SpaceL)) {
                    if (state.running) {
                        Button(
                            onClick = { vm.stop() },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(com.autoskip.helper.ui.theme.Dimens.RadiusSmall),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Filled.Stop, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("终止运行", fontSize = 16.sp)
                        }
                    } else {
                        Button(
                            onClick = { vm.start(buildConfig()) { vm.requestOverlayPermission() } },
                            enabled = serviceOn,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(com.autoskip.helper.ui.theme.Dimens.RadiusSmall)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("开始运行", fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(com.autoskip.helper.ui.theme.Dimens.SpaceL),
            verticalArrangement = Arrangement.spacedBy(com.autoskip.helper.ui.theme.Dimens.SpaceS)
        ) {
            // 权限状态卡
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(com.autoskip.helper.ui.theme.Dimens.RadiusCard),
                colors = CardDefaults.cardColors(
                    containerColor = if (serviceOn && overlayOn)
                        com.autoskip.helper.ui.theme.HarmonyColor.StatusOKBg
                    else com.autoskip.helper.ui.theme.HarmonyColor.StatusWarnBg
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(Modifier.padding(com.autoskip.helper.ui.theme.Dimens.SpaceM)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (serviceOn && overlayOn) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when {
                                !serviceOn && !overlayOn -> "需要：无障碍服务 + 悬浮窗权限"
                                !serviceOn -> "需要：分身无障碍服务"
                                !overlayOn -> "需要：悬浮窗权限"
                                else -> "权限已就绪"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (!serviceOn || !overlayOn) {
                        Spacer(Modifier.height(10.dp))
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
            }

            // 运行状态卡
            if (state.running || state.currentIndex > 0) {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(com.autoskip.helper.ui.theme.Dimens.RadiusCard),
                    colors = CardDefaults.cardColors(containerColor = com.autoskip.helper.ui.theme.HarmonyColor.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(Modifier.padding(com.autoskip.helper.ui.theme.Dimens.SpaceM)) {
                        Text(state.statusText, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("成功 ${state.successCount}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Text("失败 ${state.failCount}", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                        if (state.lastLog.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(state.lastLog, style = MaterialTheme.typography.bodySmall,
                                color = com.autoskip.helper.ui.theme.HarmonyColor.Gray6, maxLines = 2)
                        }
                    }
                }
            }

            SectionTitle("基础配置")

            // 停止条件：数量 / 截止序号 二选一
            Text("停止条件（二选一）", style = MaterialTheme.typography.bodySmall,
                color = com.autoskip.helper.ui.theme.HarmonyColor.Gray6)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.RadioButton(
                    selected = !useStopIndex,
                    onClick = { useStopIndex = false }
                )
                Text("按数量", Modifier.weight(1f))
                OutlinedTextField(
                    value = totalCount,
                    onValueChange = { totalCount = it },
                    enabled = !useStopIndex,
                    singleLine = true,
                    modifier = Modifier.weight(1.6f),
                    shape = RoundedCornerShape(com.autoskip.helper.ui.theme.Dimens.RadiusInput),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.RadioButton(
                    selected = useStopIndex,
                    onClick = { useStopIndex = true }
                )
                Text("按序号截止", Modifier.weight(1f))
                OutlinedTextField(
                    value = stopIndex,
                    onValueChange = { stopIndex = it },
                    enabled = useStopIndex,
                    singleLine = true,
                    modifier = Modifier.weight(1.6f),
                    shape = RoundedCornerShape(com.autoskip.helper.ui.theme.Dimens.RadiusInput),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
            Text(
                "按序号：分身名中「抖音XX」的 XX ≥ 截止值即停止（如截止 50 → 抖音50 及以上不再处理）",
                style = MaterialTheme.typography.bodySmall,
                color = com.autoskip.helper.ui.theme.HarmonyColor.Gray6
            )

            LabeledField("后缀格式", suffixFmt) { suffixFmt = it }
            Text("支持 {date} 日期、{date2} 补零日期、{time} 时间",
                style = MaterialTheme.typography.bodySmall, color = com.autoskip.helper.ui.theme.HarmonyColor.Gray6)

            SectionTitle("高级配置")
            LabeledField("安装超时(秒)", installTimeout, true) { installTimeout = it }
            LabeledField("失败重试次数", retryTimes, true) { retryTimes = it }
            LabeledField("连续失败停止", maxFail, true) { maxFail = it }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("保存详细日志", Modifier.weight(1f))
                Switch(checked = saveLog, onCheckedChange = { saveLog = it })
            }
            LabeledField("悬浮窗日志高度(dp)", logHeight, true) { logHeight = it }
            Text("悬浮窗日志区高度，默认 90；喜欢大可自行调高",
                style = MaterialTheme.typography.bodySmall, color = com.autoskip.helper.ui.theme.HarmonyColor.Gray6)

            SectionTitle("基准分辨率")
            Text("坐标基于此分辨率填写，运行时按真机等比换算",
                style = MaterialTheme.typography.bodySmall, color = com.autoskip.helper.ui.theme.HarmonyColor.Gray6)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = testW, onValueChange = { testW = it },
                    label = { Text("宽") }, singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(com.autoskip.helper.ui.theme.Dimens.RadiusInput),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Text("  ×  ", Modifier.padding(horizontal = 4.dp))
                OutlinedTextField(
                    value = testH, onValueChange = { testH = it },
                    label = { Text("高") }, singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(com.autoskip.helper.ui.theme.Dimens.RadiusInput),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            SectionTitle("系统弹窗坐标")
            Text("设备不同可在此微调；用截图工具的坐标读数填入",
                style = MaterialTheme.typography.bodySmall, color = com.autoskip.helper.ui.theme.HarmonyColor.Gray6)
            Text("安装 / 打开 / 确定 / 不允许", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            CoordRow("X", installX, "Y", installY, { installX = it }, { installY = it })
            Spacer(Modifier.height(6.dp))
            Text("允许（权限弹窗）", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            CoordRow("X", permX, "Y", permY, { permX = it }, { permY = it })
            Spacer(Modifier.height(6.dp))
            Text("定位（仅在使用该应用时允许）", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            CoordRow("X", locX, "Y", locY, { locX = it }, { locY = it })

            Spacer(Modifier.height(8.dp))
            Text(
                "提示：运行时弹出悬浮窗显示实时进度，可暂停/终止。请保持分身大师与抖音的分身权限正常。",
                style = MaterialTheme.typography.bodySmall,
                color = com.autoskip.helper.ui.theme.HarmonyColor.Gray6
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(4.dp)
                .height(18.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(8.dp))
        Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    numberOnly: Boolean = false,
    onChange: (String) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(110.dp), fontSize = 14.sp)
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(com.autoskip.helper.ui.theme.Dimens.RadiusInput),
            keyboardOptions = if (numberOnly)
                KeyboardOptions(keyboardType = KeyboardType.Number)
            else KeyboardOptions.Default
        )
    }
}

@Composable
private fun CoordRow(
    label1: String, v1: String,
    label2: String, v2: String,
    on1: (String) -> Unit, on2: (String) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = v1, onValueChange = on1,
            label = { Text(label1) }, singleLine = true,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(com.autoskip.helper.ui.theme.Dimens.RadiusInput),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        OutlinedTextField(
            value = v2, onValueChange = on2,
            label = { Text(label2) }, singleLine = true,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(com.autoskip.helper.ui.theme.Dimens.RadiusInput),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
    }
}
