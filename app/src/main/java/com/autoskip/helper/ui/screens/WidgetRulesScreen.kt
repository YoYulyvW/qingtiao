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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autoskip.helper.data.WidgetAction
import com.autoskip.helper.data.WidgetRuleEntity
import com.autoskip.helper.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetRulesScreen(onBack: () -> Unit, vm: MainViewModel) {
    val rules by vm.widgetRules.collectAsState()
    var editing by remember { mutableStateOf<WidgetRuleEntity?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.statusBarsPadding(),
        topBar = {
            TopAppBar(
                title = { Text("控件规则", fontWeight = FontWeight.Bold) },
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
                Modifier.fillMaxSize().padding(com.autoskip.helper.ui.theme.Dimens.SpaceL),
                verticalArrangement = Arrangement.spacedBy(com.autoskip.helper.ui.theme.Dimens.SpaceS)
            ) {
                item {
                    Text(
                        "按控件 ID 定位控件并执行动作。优先级高于条件规则与普通规则。",
                        style = MaterialTheme.typography.bodySmall,
                        color = com.autoskip.helper.ui.theme.HarmonyColor.Gray6
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (rules.isEmpty()) {
                    item { Text("暂无控件规则，点右下角 + 添加。") }
                }
                items(rules, key = { it.id }) { rule ->
                    WidgetRuleItem(rule, vm, onClick = { editing = rule })
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
        WidgetRuleDialog(
            title = "添加控件规则",
            initial = null,
            onDismiss = { showAdd = false },
            onConfirm = { rule -> vm.addWidgetRule(rule); showAdd = false }
        )
    }
    editing?.let { rule ->
        WidgetRuleDialog(
            title = "修改控件规则",
            initial = rule,
            onDismiss = { editing = null },
            onConfirm = { updated -> vm.updateWidgetRule(updated); editing = null }
        )
    }
}

@Composable
private fun WidgetRuleItem(rule: WidgetRuleEntity, vm: MainViewModel, onClick: () -> Unit) {
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
                        androidx.compose.foundation.layout.Spacer(Modifier.width(4.dp))
                    }
                    Text(rule.remark.ifBlank { "(无备注)" }, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Text("ID: " + rule.widgetId, style = MaterialTheme.typography.bodySmall)
                if (!rule.matchText.isNullOrBlank()) {
                    Text("且文本含: " + rule.matchText, style = MaterialTheme.typography.bodySmall)
                }
                Text(actionLabel(rule), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary)
                if (rule.hitCount > 0) {
                    Text("命中 " + rule.hitCount + " 次", style = MaterialTheme.typography.bodySmall,
                        color = com.autoskip.helper.ui.theme.HarmonyColor.Gray6)
                }
            }
            Switch(checked = rule.enabled, onCheckedChange = {
                vm.updateWidgetRule(rule.copy(enabled = it))
            })
            IconButton(onClick = { vm.deleteWidgetRule(rule) }) {
                Icon(Icons.Filled.Delete, contentDescription = "删除")
            }
        }
    }
}

private fun actionLabel(rule: WidgetRuleEntity): String = when (rule.actionType) {
    WidgetAction.CLICK -> "动作：点击"
    WidgetAction.LONG_PRESS -> "动作：长按"
    WidgetAction.BACK -> "动作：返回键"
    WidgetAction.COORD -> "动作：点击坐标 (" + rule.coordX + "," + rule.coordY + ")"
    else -> "动作：?"
}

@Composable
private fun WidgetRuleDialog(
    title: String,
    initial: WidgetRuleEntity?,
    onDismiss: () -> Unit,
    onConfirm: (WidgetRuleEntity) -> Unit
) {
    var remark by remember { mutableStateOf(initial?.remark ?: "") }
    var widgetId by remember { mutableStateOf(initial?.widgetId ?: "") }
    var matchText by remember { mutableStateOf(initial?.matchText ?: "") }
    var actionType by remember { mutableStateOf(initial?.actionType ?: WidgetAction.CLICK) }
    var coordX by remember { mutableStateOf((initial?.coordX ?: 0).toString()) }
    var coordY by remember { mutableStateOf((initial?.coordY ?: 0).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = remark, onValueChange = { remark = it },
                    label = { Text("备注（这个控件是什么）") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
                OutlinedTextField(
                    value = widgetId, onValueChange = { widgetId = it },
                    label = { Text("控件 ID（完整或后缀，如 vfd）") },
                    placeholder = { Text("com.ss.android.ugc.aweme:id/vfd") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
                OutlinedTextField(
                    value = matchText, onValueChange = { matchText = it },
                    label = { Text("且文本含（可选，控件text为空则留空）") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
                Text("动作", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                val actions = listOf(
                    WidgetAction.CLICK to "点击",
                    WidgetAction.LONG_PRESS to "长按",
                    WidgetAction.BACK to "返回",
                    WidgetAction.COORD to "坐标"
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    actions.forEach { (type, label) ->
                        val sel = actionType == type
                        Button(
                            onClick = { actionType = type },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (sel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) { Text(label, fontSize = 12.sp) }
                    }
                }
                if (actionType == WidgetAction.COORD) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("X", Modifier.width(20.dp), fontSize = 13.sp)
                        OutlinedTextField(
                            value = coordX, onValueChange = { coordX = it.filter { c -> c.isDigit() } },
                            singleLine = true, modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Y", Modifier.width(20.dp), fontSize = 13.sp)
                        OutlinedTextField(
                            value = coordY, onValueChange = { coordY = it.filter { c -> c.isDigit() } },
                            singleLine = true, modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (widgetId.isBlank()) return@TextButton
                val rule = (initial ?: WidgetRuleEntity(remark = "", widgetId = "")).copy(
                    remark = remark.trim().ifBlank { widgetId.trim() },
                    widgetId = widgetId.trim(),
                    matchText = matchText.trim().ifBlank { null },
                    actionType = actionType,
                    coordX = coordX.toIntOrNull() ?: 0,
                    coordY = coordY.toIntOrNull() ?: 0
                )
                onConfirm(rule)
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
