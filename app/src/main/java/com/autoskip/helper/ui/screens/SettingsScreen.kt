package com.autoskip.helper.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autoskip.helper.service.PushNotifier
import com.autoskip.helper.ui.MainViewModel
import kotlinx.coroutines.launch
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: MainViewModel,
    onOpenDrama: () -> Unit = {},
    onOpenFenShen: () -> Unit = {}
) {
    // 本地编辑态：避免每敲一个字符都写 DataStore（导致卡顿）
    // 初始值由 LaunchedEffect(Unit) 一次性从 DataStore 读取
    var urlText by remember { mutableStateOf("") }
    var nameText by remember { mutableStateOf("") }
    var userText by remember { mutableStateOf("") }
    var msgText by remember { mutableStateOf("") }
    var tokenText by remember { mutableStateOf("") }
    var includeClone by remember { mutableStateOf(true) }
    var testResult by remember { mutableStateOf("") }
    var rcResult by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 识别提示悬浮窗
    val overlayToastOn by vm.overlayToastEnabled.collectAsState()
    var overlayPermGranted by remember { mutableStateOf(true) }

    // 检查悬浮窗权限（每次进入设置页刷新一次）
    LaunchedEffect(Unit) {
        overlayPermGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            Settings.canDrawOverlays(context) else true
    }

    fun openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val i = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
        }
    }

    // ★ 一次性初始化：用 first() 读取 DataStore 真实值，避免 stateIn 初始空值导致的字段错乱
    LaunchedEffect(Unit) {
        val cfg = vm.loadPushConfig()
        urlText = cfg[0]
        nameText = cfg[1]
        userText = cfg[2]
        msgText = cfg[3]
        tokenText = cfg[4]
        includeClone = vm.loadPushIncludeClone()
    }

    fun saveAll() {
        vm.setPushUrl(urlText.trim())
        vm.setPushName(nameText.trim())
        vm.setPushUser(userText.trim())
        vm.setPushMsg(msgText.trim().ifBlank { "出现了广告窗口，请注意查看" })
        vm.setPushToken(tokenText.trim())
        vm.setPushIncludeClone(includeClone)
        android.widget.Toast.makeText(context, "已保存", android.widget.Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                title = { Text("高级设置", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回",
                            tint = com.autoskip.helper.ui.theme.HarmonyColor.TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = com.autoskip.helper.ui.theme.HarmonyColor.White,
                    titleContentColor = com.autoskip.helper.ui.theme.HarmonyColor.TextPrimary
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(com.autoskip.helper.ui.theme.HarmonyColor.GrayBG)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = com.autoskip.helper.ui.theme.Dimens.SpaceL)
                .padding(top = com.autoskip.helper.ui.theme.Dimens.SpaceM,
                         bottom = com.autoskip.helper.ui.theme.Dimens.SpaceXXXL),
            verticalArrangement = Arrangement.spacedBy(com.autoskip.helper.ui.theme.Dimens.SpaceS)
        ) {
            // 广告推送
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(com.autoskip.helper.ui.theme.Dimens.RadiusCard),
                colors = CardDefaults.cardColors(containerColor = com.autoskip.helper.ui.theme.HarmonyColor.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(Modifier.padding(com.autoskip.helper.ui.theme.Dimens.SpaceM)) {
                    Text("广告推送", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        "检测到短剧广告时 POST JSON 推送；地址留空则不推送。",
                        style = MaterialTheme.typography.bodySmall,
                        color = com.autoskip.helper.ui.theme.HarmonyColor.Gray6
                    )
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = urlText,
                        onValueChange = { urlText = it },
                        label = { Text("推送地址 URL", fontSize = 12.sp) },
                        placeholder = { Text("http://192.168.1.100:8080/notify", fontSize = 12.sp) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().height(58.dp),
                        shape = RoundedCornerShape(com.autoskip.helper.ui.theme.Dimens.RadiusInput)
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = nameText,
                            onValueChange = { nameText = it },
                            label = { Text("识别字符", fontSize = 12.sp) },
                            placeholder = { Text("1号板", fontSize = 12.sp) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f).height(58.dp),
                            shape = RoundedCornerShape(com.autoskip.helper.ui.theme.Dimens.RadiusInput)
                        )
                        OutlinedTextField(
                            value = userText,
                            onValueChange = { userText = it },
                            label = { Text("user", fontSize = 12.sp) },
                            placeholder = { Text("可选", fontSize = 12.sp) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f).height(58.dp),
                            shape = RoundedCornerShape(com.autoskip.helper.ui.theme.Dimens.RadiusInput)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = msgText,
                        onValueChange = { msgText = it },
                        label = { Text("推送内容", fontSize = 12.sp) },
                        placeholder = { Text("出现了广告窗口，请注意查看", fontSize = 12.sp) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().height(58.dp),
                        shape = RoundedCornerShape(com.autoskip.helper.ui.theme.Dimens.RadiusInput)
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = tokenText,
                        onValueChange = { tokenText = it },
                        label = { Text("Token（可选）", fontSize = 12.sp) },
                        placeholder = { Text("填入后请求头带 Authorization: Bearer", fontSize = 11.sp) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().height(58.dp),
                        shape = RoundedCornerShape(com.autoskip.helper.ui.theme.Dimens.RadiusInput)
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("附带当前分身名", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("如「分身34」，拼在识别字符后", style = MaterialTheme.typography.bodySmall,
                                color = com.autoskip.helper.ui.theme.HarmonyColor.Gray6)
                        }
                        androidx.compose.material3.Switch(
                            checked = includeClone,
                            onCheckedChange = { includeClone = it }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { saveAll() },
                            modifier = Modifier.weight(1f).height(com.autoskip.helper.ui.theme.Dimens.MinTouchTarget),
                            shape = RoundedCornerShape(com.autoskip.helper.ui.theme.Dimens.RadiusInput),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = com.autoskip.helper.ui.theme.HarmonyColor.BrandOrange,
                                contentColor = com.autoskip.helper.ui.theme.HarmonyColor.White
                            )
                        ) { Text("保存", fontSize = 14.sp) }
                        OutlinedButton(
                            onClick = {
                                // ★ 广告推送被关闭（未授权）→ 不允许测试推送
                                if (!com.autoskip.helper.license.FeatureGate.isEnabled(
                                        com.autoskip.helper.license.FeatureGate.Feat.PUSH)) {
                                    testResult = "广告推送未授权（已被关闭）"
                                    return@OutlinedButton
                                }
                                // 测试时先保存，再用当前值发送
                                saveAll()
                                testResult = "发送中…"
                                scope.launch {
                                    testResult = PushNotifier.testSend(
                                        urlText.trim(), nameText.trim(), userText.trim(),
                                        msgText.trim().ifBlank { "出现了广告窗口，请注意查看" },
                                        tokenText.trim()
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(com.autoskip.helper.ui.theme.Dimens.RadiusInput)
                        ) { Text("测试", fontSize = 14.sp) }
                    }
                    if (testResult.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(testResult, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "格式：{\"text\":\"<识别字符> <分身>，<推送内容>\",\"user\":\"<user>\"}；token 在请求头",
                        style = MaterialTheme.typography.bodySmall,
                        color = com.autoskip.helper.ui.theme.HarmonyColor.Gray6
                    )
                }
            }

            SectionLabelH("识别提示")

            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(com.autoskip.helper.ui.theme.Dimens.RadiusCard),
                colors = CardDefaults.cardColors(containerColor = com.autoskip.helper.ui.theme.HarmonyColor.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(Modifier.padding(com.autoskip.helper.ui.theme.Dimens.SpaceM)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("显示识别提示", fontWeight = FontWeight.Medium, fontSize = 15.sp,
                                color = com.autoskip.helper.ui.theme.HarmonyColor.TextPrimary)
                            Text("识别到弹窗/条件/控件时，在屏幕上方显示 2 秒提示",
                                fontSize = 12.sp, color = com.autoskip.helper.ui.theme.HarmonyColor.Gray6)
                        }
                        Switch(
                            checked = overlayToastOn && overlayPermGranted,
                            onCheckedChange = { on ->
                                if (on && !overlayPermGranted) {
                                    openOverlaySettings()
                                } else {
                                    vm.setOverlayToastEnabled(on)
                                }
                            }
                        )
                    }
                    if (!overlayPermGranted) {
                        Spacer(Modifier.height(6.dp))
                        Text("⚠ 需要悬浮窗权限，点击开关后去授权",
                            fontSize = 12.sp, color = com.autoskip.helper.ui.theme.HarmonyColor.Warning)
                    }
                }
            }

            SectionLabelH("功能入口")

            EntryCard(
                icon = "📺",
                iconBg = com.autoskip.helper.ui.theme.HarmonyColor.IconBlueBg,
                title = "短剧自动倍速",
                subtitle = "长按呼出菜单、自动切 3x、暂停恢复等",
                onClick = onOpenDrama
            )
            EntryCard(
                icon = "📱",
                iconBg = com.autoskip.helper.ui.theme.HarmonyColor.IconPurpleBg,
                title = "自动分身（分身大师）",
                subtitle = "批量创建抖音分身、改名、安装",
                onClick = onOpenFenShen
            )

            SectionLabelH("远程控制（Root）")

            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(com.autoskip.helper.ui.theme.Dimens.RadiusCard),
                colors = CardDefaults.cardColors(containerColor = com.autoskip.helper.ui.theme.HarmonyColor.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(Modifier.padding(com.autoskip.helper.ui.theme.Dimens.SpaceM)) {
                    Text("捕获测试（app_process）",
                        fontWeight = FontWeight.Medium, fontSize = 15.sp,
                        color = com.autoskip.helper.ui.theme.HarmonyColor.TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text("验证 Root 通路：VirtualDisplay + MediaCodec",
                        fontSize = 12.sp, color = com.autoskip.helper.ui.theme.HarmonyColor.Gray6)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            rcResult = "测试中（约 15 秒）…"
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                val out = try {
                                    com.autoskip.helper.root.RemoteControlTester.testAppProcess(context)
                                } catch (e: Exception) {
                                    "异常：" + (e.message ?: "未知")
                                }
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    rcResult = out
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(com.autoskip.helper.ui.theme.Dimens.MinTouchTarget),
                        shape = RoundedCornerShape(com.autoskip.helper.ui.theme.Dimens.RadiusInput),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = com.autoskip.helper.ui.theme.HarmonyColor.BrandOrange,
                            contentColor = com.autoskip.helper.ui.theme.HarmonyColor.White
                        )
                    ) { Text("运行测试（捕获）", fontSize = 14.sp) }

                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            rcResult = "测试 WebRTC 加载中（约 20 秒）…"
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                val out = try {
                                    com.autoskip.helper.root.RemoteControlTester.testWebRTCInAppProcess(context)
                                } catch (e: Exception) {
                                    "异常：" + (e.message ?: "未知")
                                }
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    rcResult = out
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(com.autoskip.helper.ui.theme.Dimens.MinTouchTarget),
                        shape = RoundedCornerShape(com.autoskip.helper.ui.theme.Dimens.RadiusInput)
                    ) { Text("测试 WebRTC 加载", fontSize = 14.sp) }

                    if (rcResult.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(rcResult, fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }

                    Spacer(Modifier.height(12.dp))
                    val rcSvcRunning = com.autoskip.helper.remote.RemoteControlService.isRunning()
                    OutlinedButton(
                        onClick = {
                            val intent = android.content.Intent(context, com.autoskip.helper.remote.RemoteControlService::class.java)
                            // deviceId 与 LicenseManager 保持一致（Android ID 派生的 SHA-256）
                            intent.putExtra("deviceId", com.autoskip.helper.license.LicenseManager.deviceId(context))
                            // 信令 URL：从 LicensePrefs 读 baseUrl，把 https:// 换成 wss://
                            scope.launch {
                                try {
                                    val base = com.autoskip.helper.license.LicensePrefs(context).getBestBase()
                                        .ifBlank { com.autoskip.helper.license.LicensePrefs.BUILTIN_BASE }
                                    val wsBase = when {
                                        base.startsWith("https://") -> "wss://" + base.substring(8)
                                        base.startsWith("http://") -> "ws://" + base.substring(7)
                                        else -> "ws://" + base
                                    }
                                    val signalUrl = wsBase.trimEnd('/') + "/ws/signal"
                                    intent.putExtra("signalUrl", signalUrl)
                                    if (rcSvcRunning) {
                                        context.stopService(intent)
                                    } else {
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                            context.startForegroundService(intent)
                                        } else {
                                            context.startService(intent)
                                        }
                                    }
                                    rcResult = if (rcSvcRunning) "已请求停止" else "已请求启动（看日志）"
                                } catch (e: Exception) {
                                    rcResult = "失败：" + e.message
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(com.autoskip.helper.ui.theme.Dimens.MinTouchTarget),
                        shape = RoundedCornerShape(com.autoskip.helper.ui.theme.Dimens.RadiusInput)
                    ) {
                        Text(if (rcSvcRunning) "停止远控服务" else "启动远控服务", fontSize = 14.sp)
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
        }
    }
}


@Composable
private fun SectionLabelH(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        color = com.autoskip.helper.ui.theme.HarmonyColor.Gray4,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun EntryCard(
    icon: String,
    iconBg: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(com.autoskip.helper.ui.theme.Dimens.RadiusCard),
        colors = CardDefaults.cardColors(containerColor = com.autoskip.helper.ui.theme.HarmonyColor.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(com.autoskip.helper.ui.theme.Dimens.SpaceM),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(40.dp).background(iconBg, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) { Text(icon, fontSize = 20.sp) }
            Spacer(Modifier.width(com.autoskip.helper.ui.theme.Dimens.SpaceM))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = com.autoskip.helper.ui.theme.HarmonyColor.TextPrimary)
                Text(subtitle, fontSize = 12.sp, color = com.autoskip.helper.ui.theme.HarmonyColor.Gray6)
            }
            Text("›", fontSize = 18.sp, color = com.autoskip.helper.ui.theme.HarmonyColor.Gray4)
        }
    }
}