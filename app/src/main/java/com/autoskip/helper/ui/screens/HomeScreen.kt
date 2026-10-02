package com.autoskip.helper.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autoskip.helper.service.AutoClickAccessibilityService
import com.autoskip.helper.ui.MainViewModel
import com.autoskip.helper.ui.theme.Dimens
import com.autoskip.helper.ui.theme.HarmonyColor

@Composable
fun HomeScreen(vm: MainViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val enabled by vm.enabled.collectAsState()
    val today by vm.todayCount.collectAsState()
    val total by vm.totalCount.collectAsState()
    val delay by vm.clickDelay.collectAsState()
    val learnMode by vm.learnMode.collectAsState()

    var serviceOn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            serviceOn = isAccessibilityEnabled(context)
            kotlinx.coroutines.delay(1000)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.SpaceL)
            .padding(top = Dimens.SpaceM, bottom = Dimens.SpaceXXXL),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)
    ) {
        StatusCard(serviceOn) {
            if (!serviceOn) openAccessibilitySettings(context)
        }

        SectionLabel("快捷开关")
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Dimens.RadiusCard),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column {
                SwitchRow(
                    title = "自动点击总开关",
                    subtitle = if (enabled) "当前：开启" else "当前：已暂停",
                    checked = enabled,
                    onCheckedChange = { vm.setEnabled(it) }
                )
                HDivider()
                SwitchRow(
                    title = "学习模式",
                    subtitle = "手动点一次，自动生成规则",
                    checked = learnMode,
                    onCheckedChange = { vm.setLearnMode(it) }
                )
                AnimatedVisibility(
                    visible = learnMode,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Text(
                        "学习进行中…… 请切换到目标 App，点击需要自动跳过的按钮。",
                        color = HarmonyColor.BrandOrange,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = Dimens.SpaceM, vertical = Dimens.SpaceS)
                    )
                }
            }
        }

        SectionLabel("今日数据", topSpace = true)
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM)) {
            StatCard("今日跳过", today.toString(), Modifier.weight(1f))
            StatCard("累计跳过", total.toString(), Modifier.weight(1f))
        }

        SectionLabel("点击延迟", topSpace = true)
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Dimens.RadiusCard),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.padding(Dimens.SpaceM)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("弹窗出现后等待", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(delay.toString() + " ms", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = HarmonyColor.BrandOrange)
                }
                Spacer(Modifier.height(Dimens.SpaceXS))
                Slider(
                    value = delay.toFloat(),
                    onValueChange = { vm.setClickDelay(it.toLong()) },
                    valueRange = 0f..2000f,
                    steps = 19,
                    colors = SliderDefaults.colors(
                        thumbColor = HarmonyColor.BrandOrange,
                        activeTrackColor = HarmonyColor.BrandOrange
                    )
                )
            }
        }

        Spacer(Modifier.height(Dimens.SpaceXS))
        OutlinedButton(
            onClick = { vm.clearLogs() },
            modifier = Modifier.fillMaxWidth().height(Dimens.MinTouchTarget),
            shape = RoundedCornerShape(Dimens.RadiusInput),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
        ) {
            Text("清空统计与记录", fontSize = 14.sp)
        }

        Spacer(Modifier.height(Dimens.SpaceM))
        Text(
            "小提示：为确保长期后台存活，请在系统「电池 / 应用启动管理」中将本应用设为允许自启动、不受限制。",
            fontSize = 11.sp,
            color = HarmonyColor.Gray4,
            lineHeight = 16.sp
        )
    }
}

@Composable
private fun StatusCard(serviceOn: Boolean, onEnable: () -> Unit) {
    val bg by animateColorAsState(
        if (serviceOn) HarmonyColor.StatusOKBg else HarmonyColor.StatusWarnBg,
        animationSpec = tween(300), label = "statusBg"
    )
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.RadiusCard),
        colors = CardDefaults.cardColors(containerColor = bg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(Dimens.SpaceM),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (serviceOn) HarmonyColor.Success else HarmonyColor.Warning)
            )
            Spacer(Modifier.width(Dimens.SpaceM))
            Column(Modifier.weight(1f)) {
                Text(
                    if (serviceOn) "无障碍服务已开启" else "无障碍服务未开启",
                    fontSize = 15.sp, fontWeight = FontWeight.Medium,
                    color = HarmonyColor.TextPrimary
                )
                Text(
                    if (serviceOn) "自动跳过已就绪" else "需开启才能自动点击",
                    fontSize = 12.sp, color = HarmonyColor.Gray6
                )
            }
            if (!serviceOn) {
                Button(
                    onClick = onEnable,
                    shape = RoundedCornerShape(Dimens.RadiusInput),
                    colors = ButtonDefaults.buttonColors(containerColor = HarmonyColor.BrandOrange)
                ) { Text("去开启", fontSize = 13.sp) }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, topSpace: Boolean = false) {
    if (topSpace) Spacer(Modifier.height(Dimens.SpaceS))
    Text(
        text,
        fontSize = 13.sp,
        color = HarmonyColor.Gray4,
        modifier = Modifier.padding(start = Dimens.SpaceXS, bottom = Dimens.SpaceXS)
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(Dimens.SpaceM),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 12.sp, color = HarmonyColor.Gray6)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = HarmonyColor.White,
                checkedTrackColor = HarmonyColor.BrandOrange,
                uncheckedThumbColor = HarmonyColor.White,
                uncheckedTrackColor = HarmonyColor.Gray3
            )
        )
    }
}

@Composable
private fun HDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.SpaceM)
            .height(1.dp)
            .background(HarmonyColor.Gray2)
    )
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier,
        shape = RoundedCornerShape(Dimens.RadiusCard),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = Dimens.SpaceL),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = HarmonyColor.BrandOrange)
            Spacer(Modifier.height(Dimens.SpaceXS))
            Text(label, fontSize = 12.sp, color = HarmonyColor.Gray6)
        }
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
