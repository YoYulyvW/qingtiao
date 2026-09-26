package com.autoskip.helper.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.autoskip.helper.App
import com.autoskip.helper.data.LogEntity
import com.autoskip.helper.data.RuleEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 核心无障碍服务：监听窗口变化，匹配规则并自动点击。
 */
class AutoClickAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var enabled = true
    @Volatile private var clickDelay = 600L
    @Volatile private var cachedRules: List<RuleEntity> = emptyList()

    /** 白名单模式：仅对白名单内的包名生效 */
    @Volatile private var whitelistEnabled = false
    @Volatile private var whitelistPkgs: Set<String> = emptySet()

    /** 严格模式：关闭/X 类按钮仅在登录/广告上下文出现时才点击 */
    @Volatile private var strictClose = true

    /** 学习模式回调：抓到用户点击的节点时触发 */
    @Volatile var learnCallback: ((MatchedNode) -> Unit)? = null

    private var lastClickTime = 0L
    private val CLICK_COOLDOWN = 800L

    // 重复弹窗检测：若同一弹窗点击后很快再次出现，说明点击无效，改用返回键
    private var lastPopupSignature: String? = null
    private var lastPopupTime = 0L
    private val REPEAT_WINDOW = 3000L  // 3 秒内同一弹窗再次出现 → 判定点击无效

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        val repo = App.instance.repo
        scope.launch { repo.enabled.collect { enabled = it } }
        scope.launch { repo.clickDelayMs.collect { clickDelay = it } }
        scope.launch { repo.rules.collect { cachedRules = it } }
        scope.launch { repo.whitelistEnabled.collect { whitelistEnabled = it } }
        scope.launch { repo.whitelistPkgs.collect { whitelistPkgs = it } }
        scope.launch { repo.strictClose.collect { strictClose = it } }
        Log.i(TAG, "无障碍服务已连接")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !enabled) return
        val type = event.eventType

        // 学习模式：只监听用户的点击事件，抓取被点击控件
        val cb = learnCallback
        if (cb != null) {
            if (type == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                val pkg = event.packageName?.toString() ?: return
                if (pkg == packageName) return
                captureForLearning(pkg, event, cb)
            }
            return
        }

        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        // 系统 UI（多任务中心、桌面、设置等）永不处理
        if (Matcher.isSystemUi(pkg)) return

        // 白名单模式：非白名单应用直接忽略（支持通配符）
        if (whitelistEnabled && !Matcher.matchesWhitelist(pkg, whitelistPkgs)) return

        val root = rootInActiveWindow ?: return
        val now = System.currentTimeMillis()
        if (now - lastClickTime < CLICK_COOLDOWN) return

        val result = Matcher.match(root, cachedRules, pkg, strictClose)
        if (result != null) {
            // 弹窗签名：包名 + 匹配到的文字
            val signature = pkg + "|" + result.matchedText
            val nowT = System.currentTimeMillis()
            val isRepeat = signature == lastPopupSignature && (nowT - lastPopupTime) < REPEAT_WINDOW

            if (isRepeat) {
                // 同一弹窗又出现了 → 上次点击无效，改用返回键
                Log.i(TAG, "重复弹窗，改用返回键: " + signature)
                mainHandler.postDelayed({
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    val repo = App.instance.repo
                    scope.launch {
                        repo.addLog(
                            LogEntity(
                                packageName = pkg,
                                appLabel = runCatching {
                                    packageManager.getApplicationLabel(
                                        packageManager.getApplicationInfo(pkg, 0)
                                    ).toString()
                                }.getOrNull(),
                                rule = "[返回键] " + result.rule.text,
                                matchedText = result.matchedText
                            )
                        )
                    }
                }, clickDelay)
                lastPopupSignature = null   // 避免连续触发
                lastClickTime = nowT
            } else {
                lastPopupSignature = signature
                lastPopupTime = nowT
                performClick(result, pkg)
            }
        }
    }

    private fun performClick(result: MatchResult, pkg: String) {
        lastClickTime = System.currentTimeMillis()
        mainHandler.postDelayed({
            try {
                val node = result.node
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)

                val appLabel = runCatching {
                    packageManager.getApplicationLabel(
                        packageManager.getApplicationInfo(pkg, 0)
                    ).toString()
                }.getOrNull()

                val ruleName = if (result.rule.learned) "[学习] " + result.rule.text else result.rule.text
                val log = LogEntity(
                    packageName = pkg,
                    appLabel = appLabel,
                    rule = ruleName,
                    matchedText = result.matchedText
                )
                val repo = App.instance.repo
                scope.launch {
                    repo.addLog(log)
                    // id = -1 是图标兜底的虚拟规则，不入库
                    if (result.rule.id > 0) repo.bumpHit(result.rule.id)
                }
                Log.i(TAG, "已点击 pkg=" + pkg + " rule=" + result.rule.text + " text=" + result.matchedText)
            } catch (e: Exception) {
                Log.e(TAG, "点击失败", e)
            }
        }, clickDelay)
    }

    /** 学习模式：抓取用户点击的可点击控件文本 */
    private fun captureForLearning(pkg: String, event: AccessibilityEvent, cb: (MatchedNode) -> Unit) {
        val node = event.source ?: return
        val text = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
        if (text.isNotBlank()) {
            val id = node.viewIdResourceName
            mainHandler.post { cb(MatchedNode(pkg, text, id, node.isClickable)) }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        scope.cancel()
    }

    companion object {
        private const val TAG = "AutoSkip"
        @Volatile var instance: AutoClickAccessibilityService? = null
            private set
        fun isRunning(): Boolean = instance != null
    }
}

/** 学习模式抓取到的节点 */
data class MatchedNode(
    val packageName: String,
    val text: String,
    val viewId: String?,
    val clickable: Boolean
)
