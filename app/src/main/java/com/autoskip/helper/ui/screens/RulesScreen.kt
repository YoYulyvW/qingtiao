package com.autoskip.helper.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.autoskip.helper.data.RuleEntity
import com.autoskip.helper.ui.MainViewModel

@Composable
fun RulesScreen(vm: MainViewModel, onOpenCondRules: () -> Unit = {}) {
    val rules by vm.rules.collectAsState()
    var editing by remember { mutableStateOf<RuleEntity?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text("规则列表", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "点击规则可修改；带「学习」标记的为学习模式自动生成。",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
            }
            item {
                Card(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenCondRules() },
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("条件规则", fontWeight = FontWeight.Bold)
                            Text(
                                "有 X 且 Y → 执行动作（点文字/点图标X/返回键）",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text("›", style = MaterialTheme.typography.headlineSmall)
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            if (rules.isEmpty()) {
                item { Text("暂无规则，点右下角 + 添加，或使用首页的学习模式。") }
            }
            items(rules, key = { it.id }) { rule ->
                RuleItem(rule, vm, onClick = { editing = rule })
            }
            item { Spacer(Modifier.height(80.dp)) }
        }

        FloatingActionButton(
            onClick = { showAdd = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "添加规则")
        }
    }

    // 新增
    if (showAdd) {
        RuleDialog(
            title = "添加规则",
            initial = null,
            onDismiss = { showAdd = false },
            onConfirm = { text, exact ->
                vm.addRule(RuleEntity(name = text, text = text, exact = exact))
                showAdd = false
            }
        )
    }

    // 编辑
    editing?.let { rule ->
        RuleDialog(
            title = "修改规则",
            initial = rule,
            onDismiss = { editing = null },
            onConfirm = { text, exact ->
                vm.updateRule(rule.copy(name = text, text = text, exact = exact))
                editing = null
            }
        )
    }
}

@Composable
private fun RuleItem(rule: RuleEntity, vm: MainViewModel, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(rule.text, fontWeight = FontWeight.Bold)
                    if (rule.learned) {
                        Text(
                            "  学习",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                val pkgText = rule.packageName?.let { "限定: $it" } ?: "全部应用"
                Text(
                    "$pkgText · 命中 ${rule.hitCount} 次 · ${if (rule.exact) "精确" else "包含"}匹配",
                    style = MaterialTheme.typography.bodySmall
                )
                if (!rule.viewId.isNullOrBlank()) {
                    Text(rule.viewId, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline, maxLines = 1)
                }
            }
            Switch(checked = rule.enabled, onCheckedChange = {
                vm.updateRule(rule.copy(enabled = it))
            })
            IconButton(onClick = { vm.deleteRule(rule) }) {
                Icon(Icons.Filled.Delete, contentDescription = "删除")
            }
        }
    }
}

@Composable
private fun RuleDialog(
    title: String,
    initial: RuleEntity?,
    onDismiss: () -> Unit,
    onConfirm: (text: String, exact: Boolean) -> Unit
) {
    var text by remember { mutableStateOf(initial?.text ?: "") }
    var exact by remember { mutableStateOf(initial?.exact ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("按钮文本，如：跳过") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("精确匹配（完全一致）")
                    Switch(checked = exact, onCheckedChange = { exact = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text, exact) }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
