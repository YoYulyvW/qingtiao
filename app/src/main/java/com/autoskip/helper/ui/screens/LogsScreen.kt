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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.autoskip.helper.data.LogEntity
import com.autoskip.helper.ui.MainViewModel
import androidx.compose.runtime.remember
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogsScreen(vm: MainViewModel) {
    val logs by vm.logs.collectAsState()
    val today by vm.todayCount.collectAsState()
    val total by vm.totalCount.collectAsState()

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("跳过记录", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                Text("今日：$today 次", fontWeight = FontWeight.Bold)
                Text("累计：$total 次", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text("最近 500 条", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
        }
        if (logs.isEmpty()) {
            item { Text("暂无记录") }
        }
        items(logs, key = { it.id }) { log ->
            LogItem(log)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun LogItem(log: LogEntity) {
    val fmt = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text(log.appLabel ?: log.packageName, fontWeight = FontWeight.Bold)
                Text(fmt.format(Date(log.timestamp)), style = MaterialTheme.typography.bodySmall)
            }
            Text("规则：${log.rule}", style = MaterialTheme.typography.bodySmall)
            if (!log.matchedText.isNullOrBlank()) {
                Text("按钮：${log.matchedText}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}
