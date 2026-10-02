package com.autoskip.helper.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autoskip.helper.data.CondAction
import com.autoskip.helper.data.CondRuleEntity
import com.autoskip.helper.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CondRulesScreen(onBack: () -> Unit, vm: MainViewModel) {
    val context = LocalContext.current
    val condRules by vm.condRules.collectAsState()
    val condDramaOnly by vm.condDramaOnly.collectAsState()
    var editing by remember { mutableStateOf<CondRuleEntity?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }

    fun copyToClipboard(text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("cond_rules", text))
        Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("条件规则", fontWeight = FontWeight.Bold) },
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
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().background(com.autoskip.helper.ui.theme.HarmonyColor.GrayBG).padding(padding)) {
            LazyColumn(
                Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        "条件规则：满足「有 + 且」条件时执行动作，优先级高于普通规则。",
                        style = MaterialTheme.typography.bodySmall,
                        color = com.autoskip.helper.ui.theme.HarmonyColor.Gray6
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { vm.exportCondRules { copyToClipboard(it) } },
                            modifier = Modifier.weight(1f).height(com.autoskip.helper.ui.theme.Dimens.MinTouchTarget),
                            shape = RoundedCornerShape(com.autoskip.helper.ui.theme.Dimens.RadiusInput),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = com.autoskip.helper.ui.theme.HarmonyColor.BrandOrange,
                                contentColor = com.autoskip.helper.ui.theme.HarmonyColor.White
                            )
                        ) { Text("导出", fontSize = 14.sp) }
                        OutlinedButton(
                            onClick = { showImport = true },
                            modifier = Modifier.weight(1f).height(com.autoskip.helper.ui.theme.Dimens.MinTouchTarget),
                            shape = RoundedCornerShape(com.autoskip.helper.ui.theme.Dimens.RadiusInput),
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                contentColor = com.autoskip.helper.ui.theme.HarmonyColor.TextPrimary
                            )
                        ) { Text("导入", fontSize = 14.sp) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(com.autoskip.helper.ui.theme.Dimens.RadiusCard),
                        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = com.autoskip.helper.ui.theme.HarmonyColor.White),
                        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("仅在抖音/白名单内生效", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    "开启后避免在其它界面误触发返回，推荐开启。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = com.autoskip.helper.ui.theme.HarmonyColor.Gray6
                                )
                            }
                            Switch(checked = condDramaOnly, onCheckedChange = { vm.setCondDramaOnly(it) })
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                if (condRules.isEmpty()) {
                    item {
                        Text(
                            "暂无条件规则。点右下角 + 添加，例如：有「登录」且包含「帮助」→ 点图标X",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                items(condRules, key = { it.id }) { rule ->
                    CondRuleItem(rule, vm, onClick = { editing = rule })
                }
                item { Spacer(Modifier.height(80.dp)) }
            }

            FloatingActionButton(
                onClick = { showAdd = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "添加")
            }
        }
    }

    if (showAdd) {
        CondRuleDialog(
            title = "添加条件规则",
            initial = null,
            onDismiss = { showAdd = false },
            onConfirm = { rule ->
                vm.addCondRule(rule)
                showAdd = false
            }
        )
    }

    editing?.let { rule ->
        CondRuleDialog(
            title = "修改条件规则",
            initial = rule,
            onDismiss = { editing = null },
            onConfirm = { updated ->
                vm.updateCondRule(updated)
                editing = null
            }
        )
    }

    if (showImport) {
        ImportDialog(
            onDismiss = { showImport = false },
            onConfirm = { text, clearFirst ->
                vm.importCondRules(text, clearFirst) { msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
                showImport = false
            }
        )
    }
}

@Composable
private fun ImportDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Boolean) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var clearFirst by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入条件规则") },
        text = {
            Column {
                Text(
                    "每行一条：\n规则名|有|且|动作|动作文字|延时\n动作用：返回 / 点图标X / 点文字\n「且」可空，多个用 | 分隔",
                    fontSize = 12.sp,
                    color = com.autoskip.helper.ui.theme.HarmonyColor.Gray6
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    shape = RoundedCornerShape(com.autoskip.helper.ui.theme.Dimens.RadiusInput)
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = clearFirst, onCheckedChange = { clearFirst = it })
                    Text("先清空原有规则（不清空则同名跳过）", fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text, clearFirst) }
            ) { Text("导入") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun CondRuleItem(rule: CondRuleEntity, vm: MainViewModel, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(com.autoskip.helper.ui.theme.Dimens.RadiusCard),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = com.autoskip.helper.ui.theme.HarmonyColor.White),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (rule.source == "server") {
                        Text("[云端]", color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(rule.name, fontWeight = FontWeight.Bold)
                }
                val cond = if (rule.andText.isNullOrBlank()) "有「${rule.hasText}」"
                           else "有「${rule.hasText}」且含「${rule.andText}」"
                Text("$cond → ${actionLabel(rule)}", style = MaterialTheme.typography.bodySmall)
                Text("命中 ${rule.hitCount} 次", style = MaterialTheme.typography.bodySmall,
                    color = com.autoskip.helper.ui.theme.HarmonyColor.Gray6)
            }
            Switch(checked = rule.enabled, onCheckedChange = {
                vm.updateCondRule(rule.copy(enabled = it))
            })
            IconButton(onClick = { vm.deleteCondRule(rule) }) {
                Icon(Icons.Filled.Delete, contentDescription = "删除")
            }
        }
    }
}

private fun actionLabel(rule: CondRuleEntity): String = when (rule.actionType) {
    CondAction.CLICK_TEXT -> "点击「${rule.actionText ?: ""}」"
    CondAction.CLICK_ICON -> "点击图标 X"
    CondAction.BACK -> "按返回键"
    else -> "?"
}

@Composable
private fun CondRuleDialog(
    title: String,
    initial: CondRuleEntity?,
    onDismiss: () -> Unit,
    onConfirm: (CondRuleEntity) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var hasText by remember { mutableStateOf(initial?.hasText ?: "") }
    var andText by remember { mutableStateOf(initial?.andText ?: "") }
    var actionType by remember { mutableStateOf(initial?.actionType ?: CondAction.CLICK_ICON) }
    var actionText by remember { mutableStateOf(initial?.actionText ?: "") }
    var delaySec by remember { mutableStateOf((initial?.delaySec ?: 3).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = hasText, onValueChange = {
                        hasText = it
                        if (name.isBlank()) name = it
                    },
                    label = { Text("【有】必须出现的文字") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = andText, onValueChange = { andText = it },
                    label = { Text("【且】还须包含（多个用 | 分隔）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 延时检测
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("延时检测(秒)", Modifier.width(100.dp), fontSize = 13.sp)
                    OutlinedTextField(
                        value = delaySec,
                        onValueChange = { delaySec = it.filter { c -> c.isDigit() } },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(com.autoskip.helper.ui.theme.Dimens.RadiusInput),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        )
                    )
                }
                Text("检测到条件后等 N 秒，仍存在才执行；0=立即", fontSize = 11.sp,
                    color = com.autoskip.helper.ui.theme.HarmonyColor.Gray6)

                Text("【则】执行动作", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                val actions = listOf(
                    CondAction.CLICK_ICON to "点击图标X",
                    CondAction.CLICK_TEXT to "点击文字",
                    CondAction.BACK to "按返回键"
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    actions.forEach { (type, label) ->
                        val sel = actionType == type
                        Button(
                            onClick = { actionType = type },
                            shape = RoundedCornerShape(com.autoskip.helper.ui.theme.Dimens.RadiusInput),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 10.dp, vertical = 4.dp
                            ),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = if (sel) MaterialTheme.colorScheme.primary
                                else com.autoskip.helper.ui.theme.HarmonyColor.Gray1,
                                contentColor = if (sel) Color.White
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text(label, fontSize = 12.sp)
                        }
                    }
                }

                if (actionType == CondAction.CLICK_TEXT) {
                    OutlinedTextField(
                        value = actionText, onValueChange = { actionText = it },
                        label = { Text("要点击的文字") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (hasText.isBlank()) return@TextButton
                val rule = (initial ?: CondRuleEntity(name = "", hasText = "")).copy(
                    name = name.ifBlank { hasText },
                    hasText = hasText.trim(),
                    andText = andText.trim().ifBlank { null },
                    actionType = actionType,
                    actionText = if (actionType == CondAction.CLICK_TEXT) actionText.trim() else null,
                    delaySec = delaySec.toIntOrNull()?.coerceIn(0, 60) ?: 3
                )
                onConfirm(rule)
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
