package com.autoskip.helper.ui.screens

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autoskip.helper.license.FeatureGate
import com.autoskip.helper.license.LicenseManager
import com.autoskip.helper.license.LicensePrefs
import kotlinx.coroutines.launch

/**
 * 激活页：未激活 / 授权失效时显示，挡住所有功能。
 */
@Composable
fun ActivateScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var code by remember { mutableStateOf("") }
    var deviceName by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 初始化：读取已有激活码 / 设备名 / 服务端地址
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val p = LicensePrefs(context)
        code = p.getCode()
        deviceName = p.getDeviceName()
    }

    val locked = FeatureGate.state.value == FeatureGate.State.LOCKED

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("开饭小工具", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            if (locked) "授权已失效，请重新激活" else "本应用需联网验证授权后使用",
            color = MaterialTheme.colorScheme.outline, fontSize = 13.sp
        )
        Spacer(Modifier.height(24.dp))

        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("激活码") },
                    placeholder = { Text("如 VIP-0601-A1B2C3D4") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = deviceName,
                    onValueChange = { deviceName = it },
                    label = { Text("设备名（可选，便于识别）") },
                    placeholder = { Text("如 1号板") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                Button(
                    onClick = {
                        if (code.isBlank()) { message = "请输入激活码"; return@Button }
                        loading = true
                        message = ""
                        scope.launch {
                            try {
                                val p = LicensePrefs(context)
                                p.setDeviceName(deviceName.trim())
                                val msg = LicenseManager.activate(context, code.trim())
                                message = msg
                            } catch (e: Exception) {
                                message = "激活失败：" + (e.message ?: "未知错误")
                            } finally {
                                loading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (loading) {
                        CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (loading) "激活中…" else "激活", fontSize = 15.sp)
                }

                if (message.isNotBlank()) {
                    Text(
                        message,
                        color = if (message.contains("成功")) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "激活后，功能可用性由服务端控制；服务端离线时已授权设备仍可正常使用（直至到期）。",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center
        )
    }
}
