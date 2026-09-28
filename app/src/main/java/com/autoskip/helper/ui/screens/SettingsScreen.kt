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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autoskip.helper.ui.MainViewModel

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

    var urlText by remember { mutableStateOf(pushUrl) }
    var nameText by remember { mutableStateOf(pushName) }
    LaunchedEffect(pushUrl) { urlText = pushUrl }
    LaunchedEffect(pushName) { nameText = pushName }

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
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 广告推送
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text("广告推送", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "检测到短剧广告（退出短剧/返回并退出）时，POST JSON 推送到下面地址。地址留空则不推送。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = urlText,
                        onValueChange = { urlText = it; vm.setPushUrl(it) },
                        label = { Text("推送地址(URL)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = nameText,
                        onValueChange = { nameText = it; vm.setPushName(it) },
                        label = { Text("识别字符(设备名，可空)") },
                        placeholder = { Text("如：1号板XX号机") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "推送内容格式：{\"text\":\"<识别字符>，出现了广告窗口，请留意\"}",
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
                    Modifier.fillMaxWidth().padding(14.dp),
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
                    Modifier.fillMaxWidth().padding(14.dp),
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

            Spacer(Modifier.height(8.dp))
        }
    }
}
