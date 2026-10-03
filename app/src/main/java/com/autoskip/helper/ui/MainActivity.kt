package com.autoskip.helper.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autoskip.helper.fenshen.FenShenScreen
import com.autoskip.helper.license.FeatureGate
import com.autoskip.helper.ui.screens.ActivateScreen
import com.autoskip.helper.ui.screens.CondRulesScreen
import com.autoskip.helper.ui.screens.DramaScreen
import com.autoskip.helper.ui.screens.HomeScreen
import com.autoskip.helper.ui.screens.LogsScreen
import com.autoskip.helper.ui.screens.RulesScreen
import com.autoskip.helper.ui.screens.SettingsScreen
import com.autoskip.helper.ui.screens.WhitelistScreen
import com.autoskip.helper.ui.screens.WidgetRulesScreen
import com.autoskip.helper.ui.theme.AutoSkipTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ★ 沉浸式：内容延伸到状态栏/导航栏下方，状态栏透明
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            AutoSkipTheme {
                AppRoot(vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // ★ 回前台立即心跳：后台封禁后，一进前台即生效（无需等 1 分钟）
        try { com.autoskip.helper.license.LicenseManager.checkNow(this) } catch (_: Exception) {}
    }
}

@Composable
private fun AppRoot(vm: MainViewModel) {
    val licenseState by FeatureGate.state.collectAsState()

    // ★ 强制/可选更新弹窗（覆盖在最上层，优先于所有界面）
    val updateInfo by com.autoskip.helper.license.LicenseManager.updateInfo.collectAsState()
    updateInfo?.let { info ->
        UpdateDialog(info)
        // 强制更新时：其余界面全部禁用（弹窗本身就会挡住，这里可加一层遮罩）
        if (info.force) {
            // 弹窗 Modal 已覆盖，无需额外处理
        }
    }

    // ★ 授权状态判断
    when (licenseState) {
        FeatureGate.State.LOADING -> {
            // 启动瞬间：加载缓存中，显示占位，避免误显示激活页
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) { CircularProgressIndicator() }
            return
        }
        FeatureGate.State.LOCKED -> {
            ActivateScreen()
            return
        }
        else -> { /* ACTIVE → 继续主界面 */ }
    }

    var tab by remember { mutableStateOf(0) }
    var showFenShen by remember { mutableStateOf(false) }
    var showDrama by remember { mutableStateOf(false) }
    var showCondRules by remember { mutableStateOf(false) }
    var showWidgetRules by remember { mutableStateOf(false) }

    if (showFenShen) {
        FenShenScreen(onBack = { showFenShen = false })
        return
    }
    if (showDrama) {
        DramaScreen(onBack = { showDrama = false }, vm = vm)
        return
    }
    if (showCondRules) {
        CondRulesScreen(onBack = { showCondRules = false }, vm = vm)
        return
    }
    if (showWidgetRules) {
        WidgetRulesScreen(onBack = { showWidgetRules = false }, vm = vm)
        return
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        bottomBar = {
            // HarmonyOS 风格底部导航
            NavigationBar(
                modifier = Modifier.navigationBarsPadding(),
                containerColor = com.autoskip.helper.ui.theme.HarmonyColor.White,
                tonalElevation = 0.dp
            ) {
                val tabs = listOf(
                    Triple(0, Icons.Filled.Home, "首页"),
                    Triple(1, Icons.Filled.Edit, "规则"),
                    Triple(2, Icons.Filled.Shield, "白名单"),
                    Triple(3, Icons.Filled.List, "记录"),
                    Triple(4, Icons.Filled.Settings, "设置")
                )
                tabs.forEach { (idx, icon, label) ->
                    NavigationBarItem(
                        selected = tab == idx,
                        onClick = { tab = idx },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = com.autoskip.helper.ui.theme.HarmonyColor.BrandOrange,
                            selectedTextColor = com.autoskip.helper.ui.theme.HarmonyColor.BrandOrange,
                            unselectedIconColor = com.autoskip.helper.ui.theme.HarmonyColor.Gray4,
                            unselectedTextColor = com.autoskip.helper.ui.theme.HarmonyColor.Gray4,
                            indicatorColor = com.autoskip.helper.ui.theme.HarmonyColor.IconOrangeBg
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).statusBarsPadding()) {
            // 页面切换淡入淡出动画（HarmonyOS 标准转场 250ms）
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    fadeIn(tween(250)) togetherWith fadeOut(tween(200))
                },
                label = "tabContent"
            ) { currentTab ->
            when (currentTab) {
                0 -> HomeScreen(vm)
                1 -> RulesScreen(vm, onOpenCondRules = { showCondRules = true }, onOpenWidgetRules = { showWidgetRules = true })
                2 -> WhitelistScreen(vm)
                3 -> LogsScreen(vm)
                else -> SettingsScreen(
                    onBack = { tab = 0 },
                    vm = vm,
                    onOpenDrama = { showDrama = true },
                    onOpenFenShen = { showFenShen = true }
                )
            }
            }
        }
    }
}
