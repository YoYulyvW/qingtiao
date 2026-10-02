package com.autoskip.helper.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.material3.TextButton
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
            .background(com.autoskip.helper.ui.theme.HarmonyColor.GrayBG)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = com.autoskip.helper.ui.theme.Dimens.SpaceXXL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🍚", fontSize = 56.sp)
        Spacer(Modifier.height(com.autoskip.helper.ui.theme.Dimens.SpaceM))
        Text("开饭小工具", fontSize = 24.sp, fontWeight = FontWeight.Bold,
            color = com.autoskip.helper.ui.theme.HarmonyColor.TextPrimary)
        Spacer(Modifier.height(com.autoskip.helper.ui.theme.Dimens.SpaceS))
        Text(
            if (locked) "授权已失效，请重新激活" else "本应用需联网验证授权后使用",
            color = com.autoskip.helper.ui.theme.HarmonyColor.Gray4, fontSize = 13.sp
        )
        Spacer(Modifier.height(com.autoskip.helper.ui.theme.Dimens.SpaceXXXL))

        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(com.autoskip.helper.ui.theme.Dimens.RadiusCard),
            colors = CardDefaults.cardColors(containerColor = com.autoskip.helper.ui.theme.HarmonyColor.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.padding(com.autoskip.helper.ui.theme.Dimens.SpaceXL),
                verticalArrangement = Arrangement.spacedBy(com.autoskip.helper.ui.theme.Dimens.SpaceM)) {

                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.replace(Regex("\\s+"), "") },
                    label = { Text("激活码") },
                    placeholder = { Text("如 VIP-2609-1F0EF8C0") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(com.autoskip.helper.ui.theme.Dimens.RadiusInput)
                )

                // ★ 粘贴按钮：直接读剪贴板整段赋值，绕过输入法分词
                TextButton(
                    onClick = {
                        try {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = cm.primaryClip
                            if (clip != null && clip.itemCount > 0) {
                                val raw = clip.getItemAt(0).coerceToText(context).toString()
                                // 去掉所有空白（含换行/空格），避免激活码被拆
                                code = raw.replace(Regex("\\s+"), "")
                                message = ""
                            } else {
                                message = "剪贴板为空"
                            }
                        } catch (e: Exception) {
                            message = "粘贴失败：" + (e.message ?: "")
                        }
                    },
                    modifier = Modifier.align(Alignment.End)
                ) { Text("从剪贴板粘贴", fontSize = 12.sp, color = com.autoskip.helper.ui.theme.HarmonyColor.BrandOrange) }

                OutlinedTextField(
                    value = deviceName,
                    onValueChange = { deviceName = it },
                    label = { Text("设备名（可选）") },
                    placeholder = { Text("如 1号板") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(com.autoskip.helper.ui.theme.Dimens.RadiusInput)
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
                    modifier = Modifier.fillMaxWidth().height(com.autoskip.helper.ui.theme.Dimens.MinTouchTarget),
                    enabled = !loading,
                    shape = RoundedCornerShape(com.autoskip.helper.ui.theme.Dimens.RadiusLarge),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = com.autoskip.helper.ui.theme.HarmonyColor.BrandOrange,
                        contentColor = com.autoskip.helper.ui.theme.HarmonyColor.White
                    )
                ) {
                    if (loading) {
                        CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp,
                            color = com.autoskip.helper.ui.theme.HarmonyColor.White)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (loading) "激活中…" else "激活", fontSize = 15.sp, fontWeight = FontWeight.Medium)
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
