package com.autoskip.helper.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.Box
import com.autoskip.helper.data.RuleEntity
import com.autoskip.helper.ui.MainViewModel

@Composable
fun RulesScreen(vm: MainViewModel) {
    val rules by vm.rules.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text("规则列表", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "文本匹配弹窗按钮；带「学习」标记的为学习模式自动生成。",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
            }
            if (rules.isEmpty()) {
                item { Text("暂无规则，点右下角 + 添加，或使用首页的学习模式。") }
            }
            items(rules, key = { it.id }) { rule ->
                RuleItem(rule, vm)
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

    if (showAdd) {
        AddRuleDialog(
            onDismiss = { showAdd = false },
            onConfirm = { name, text, exact ->
                vm.addRule(RuleEntity(name = name, text = text, exact = exact))
                showAdd = false
            }
        )
    }
}

@Composable
private fun RuleItem(rule: RuleEntity, vm: MainViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(rule.text, fontWeight = FontWeight.Bold)
                    if (rule.learned) {
                        Spacer(Modifier.height(0.dp))
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
private fun AddRuleDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, text: String, exact: Boolean) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var exact by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加规则") },
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
                    Spacer(Modifier.height(0.dp))
                    Switch(checked = exact, onCheckedChange = { exact = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text, text, exact) }
            ) { Text("添加") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
