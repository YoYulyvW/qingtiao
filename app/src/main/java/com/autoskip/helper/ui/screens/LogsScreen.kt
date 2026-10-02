package com.autoskip.helper.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autoskip.helper.data.LogEntity
import com.autoskip.helper.ui.MainViewModel
import com.autoskip.helper.ui.theme.Dimens
import com.autoskip.helper.ui.theme.HarmonyColor
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
        Modifier.fillMaxSize().background(HarmonyColor.GrayBG)
            .padding(horizontal = Dimens.SpaceL)
            .padding(top = Dimens.SpaceM),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)
    ) {
        item {
            Text("跳过记录", fontSize = 20.sp, fontWeight = FontWeight.Medium, color = HarmonyColor.TextPrimary)
            Spacer(Modifier.height(Dimens.SpaceM))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                StatText("今日", today)
                StatText("累计", total)
            }
            Spacer(Modifier.height(Dimens.SpaceM))
            Text("最近 500 条", fontSize = 12.sp, color = HarmonyColor.Gray6)
            Spacer(Modifier.height(Dimens.SpaceXS))
        }
        if (logs.isEmpty()) {
            item { Text("暂无记录", fontSize = 14.sp, color = HarmonyColor.Gray6) }
        }
        items(logs, key = { it.id }) { log ->
            LogItem(log)
        }
        item { Spacer(Modifier.height(Dimens.SpaceM)) }
    }
}

@Composable
private fun StatText(label: String, value: Int) {
    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        Text(value.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = HarmonyColor.BrandOrange)
        Text(label, fontSize = 12.sp, color = HarmonyColor.Gray6)
    }
}

@Composable
private fun LogItem(log: LogEntity) {
    val fmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.RadiusCard),
        colors = CardDefaults.cardColors(containerColor = HarmonyColor.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(Dimens.SpaceM), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text(log.appLabel ?: log.packageName, fontWeight = FontWeight.Medium,
                    fontSize = 15.sp, color = HarmonyColor.TextPrimary)
                Text(fmt.format(Date(log.timestamp)), fontSize = 12.sp, color = HarmonyColor.Gray6)
            }
            Text("规则：" + log.rule, fontSize = 13.sp, color = HarmonyColor.Gray7)
            if (!log.matchedText.isNullOrBlank()) {
                Text("按钮：" + log.matchedText, fontSize = 12.sp, color = HarmonyColor.Gray5)
            }
        }
    }
}
