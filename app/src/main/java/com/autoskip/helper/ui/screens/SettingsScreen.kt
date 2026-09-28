package com.autoskip.helper.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: MainViewModel,
    onOpenDrama: () -> Unit = {},
    onOpenFenShen: () -> Unit = {}
) {
    val pushUrl by vm.pushUrl.collectAsState()
    val pushName by vm.pushName.collectAsState()
    val pushUser by vm.pushUser.collectAsState()
    val pushMsg by vm.pushMsg.collectAsState()
    val pushToken by vm.pushToken.collectAsState()

    // 本地编辑态：避免每敲一个字符都写 DataStore（导致卡顿）
    var urlText by remember { mutableStateOf("") }
    var nameText by remember { mutableStateOf("") }
    var userText by remember { mutableStateOf("") }
    var msgText by remember { mutableStateOf("") }
    var tokenText by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 仅在首次加载时把持久化值填入本地输入框
    LaunchedEffect(pushUrl, pushName, pushUser, pushMsg, pushToken) {
        if (!loaded) {
            urlText = pushUrl
            nameText = pushName
            userText = pushUser
            msgText = pushMsg
            tokenText = pushToken
            loaded = true
        }
    }

    fun saveAll() {
        vm.setPushUrl(urlText.trim())
        vm.setPushName(nameText.trim())
        vm.setPushUser(userText.trim())
        vm.setPushMsg(msgText.trim().ifBlank { "出现了广告窗口，请注意查看" })
        vm.setPushToken(tokenText.trim())
        android.widget.Toast.makeText(context, "已保存", android.widget.Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("高级设置", fontWeight = FontWeight.Bold) },
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
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 广告推送
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("广告推送", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        "检测到短剧广告时 POST JSON 推送；地址留空则不推送。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = urlText,
                        onValueChange = { urlText = it },
                        label = { Text("推送地址 URL", fontSize = 12.sp) },
                        placeholder = { Text("http://192.168.1.100:8080/notify", fontSize = 12.sp) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(8.dp)
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
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(8.dp)
                        )
                        OutlinedTextField(
                            value = userText,
                            onValueChange = { userText = it },
                            label = { Text("user", fontSize = 12.sp) },
                            placeholder = { Text("可选", fontSize = 12.sp) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(8.dp)
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
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = tokenText,
                        onValueChange = { tokenText = it },
                        label = { Text("Token（可选，请求头 Authorization: Bearer）", fontSize = 12.sp) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { saveAll() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) { Text("保存", fontSize = 14.sp) }
                        OutlinedButton(
                            onClick = {
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
                            shape = RoundedCornerShape(8.dp)
                        ) { Text("测试", fontSize = 14.sp) }
                    }
                    if (testResult.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(testResult, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "格式：{\"text\":\"<识别字符>，<推送内容>\",\"user\":\"<user>\"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            // 短剧入口
            Card(
                Modifier.fillMaxWidth().clickable { onOpenDrama() },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("短剧自动倍速", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("长按呼出菜单、自动切 3x、暂停恢复等", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("›", style = MaterialTheme.typography.headlineSmall)
                }
            }

            // 分身入口
            Card(
                Modifier.fillMaxWidth().clickable { onOpenFenShen() },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("自动分身（分身大师）", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("批量创建抖音分身、改名、安装", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("›", style = MaterialTheme.typography.headlineSmall)
                }
            }

            Spacer(Modifier.height(6.dp))
        }
    }
}
