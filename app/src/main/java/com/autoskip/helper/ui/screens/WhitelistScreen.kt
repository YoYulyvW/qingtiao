package com.autoskip.helper.ui.screens

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.core.graphics.drawable.toBitmap
import com.autoskip.helper.ui.MainViewModel

/** 已安装应用信息 */
data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: android.graphics.drawable.Drawable?,
    val system: Boolean
)

@Composable
fun WhitelistScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val wlEnabled by vm.whitelistEnabled.collectAsState()
    val wlPkgs by vm.whitelistPkgs.collectAsState()

    // 读取一次已安装应用列表（按名称排序，已选的排前面）
    val allApps = remember { loadInstalledApps(context) }
    var query by remember { mutableStateOf("") }

    val filtered = remember(query, wlPkgs) {
        val base = allApps.filter {
            query.isBlank() || it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
        base.sortedWith(
            compareByDescending<AppInfo> { it.packageName in wlPkgs }
                .thenBy { it.label }
        )
    }

    Column(Modifier.fillMaxSize().background(com.autoskip.helper.ui.theme.HarmonyColor.GrayBG)) {
        // 顶部：总开关 + 说明
        Card(
            Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (wlEnabled) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surface
            )
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("仅对白名单应用生效", fontWeight = FontWeight.Bold)
                        Text(
                            if (wlEnabled) "已开启：只有勾选的应用会被自动点击"
                            else "已关闭：对所有应用生效（系统桌面等也可能被误点）",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(checked = wlEnabled, onCheckedChange = { vm.setWhitelistEnabled(it) })
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "建议开启。使用「学习模式」时，学到的应用会自动加入白名单。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // 通配规则区：展示所有含 * 的规则 + 自定义添加
        Card(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(com.autoskip.helper.ui.theme.Dimens.RadiusCard),
            colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = com.autoskip.helper.ui.theme.HarmonyColor.White),
            elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.padding(12.dp)) {
                Text("通配 / 自定义包名", fontWeight = FontWeight.Bold)
                Text(
                    "支持 * 前缀通配。如 com.qihoo.magic.* 可匹配所有分身。",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))

                val patterns = wlPkgs.filter { it.contains("*") }.sorted()
                if (patterns.isEmpty()) {
                    Text("暂无通配规则", style = MaterialTheme.typography.bodySmall,
                        color = com.autoskip.helper.ui.theme.HarmonyColor.Gray6)
                } else {
                    patterns.forEach { p ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(p, style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { vm.removeFromWhitelist(p) }) {
                                Text("删除", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                var newPattern by remember { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newPattern,
                        onValueChange = { newPattern = it },
                        label = { Text("如 com.xx.yy.*") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.size(8.dp))
                    Button(onClick = {
                        vm.addWhitelistPattern(newPattern)
                        newPattern = ""
                    }) { Text("添加") }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("搜索应用名 / 包名") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(8.dp))

        Text(
            "已选 ${wlPkgs.size} 个应用",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(filtered, key = { it.packageName }) { app ->
                AppRow(
                    app = app,
                    checked = com.autoskip.helper.service.Matcher.matchesWhitelist(app.packageName, wlPkgs),
                    onToggle = { vm.toggleWhitelistPkg(app.packageName) }
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun AppRow(app: AppInfo, checked: Boolean, onToggle: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(com.autoskip.helper.ui.theme.Dimens.RadiusCard),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = com.autoskip.helper.ui.theme.HarmonyColor.White),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (app.icon != null) {
                Image(
                    bitmap = remember(app.packageName) { app.icon.toBitmap().asImageBitmap() },
                    contentDescription = null,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.size(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(app.label, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(
                    app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = com.autoskip.helper.ui.theme.HarmonyColor.Gray6,
                    maxLines = 1
                )
            }
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
        }
    }
}

/** 读取所有可启动的应用（含系统应用） */
private fun loadInstalledApps(context: android.content.Context): List<AppInfo> {
    val pm = context.packageManager
    val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
        .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
    val resolved = pm.queryIntentActivities(intent, 0)
    val seen = HashSet<String>()
    val list = ArrayList<AppInfo>()
    for (ri in resolved) {
        val pkg = ri.activityInfo.packageName
        if (!seen.add(pkg)) continue
        if (pkg == context.packageName) continue
        val ai: ApplicationInfo = ri.activityInfo.applicationInfo
        list.add(
            AppInfo(
                packageName = pkg,
                label = pm.getApplicationLabel(ai).toString(),
                icon = runCatching { pm.getApplicationIcon(ai) }.getOrNull(),
                system = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            )
        )
    }
    return list
}
