package com.autoskip.helper.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
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
import kotlinx.coroutines.delay
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

    // ===== 短剧自动倍速 =====
    @Volatile private var dramaEnabled = false
    @Volatile private var dramaAutoMount = true
    @Volatile private var dramaIntervalMs = 1000L
    @Volatile private var dramaTargetSpeed = "3x"

    /** 上次长按时间（冷却，避免重复长按） */
    private var lastLongPressTime = 0L
    private val LONG_PRESS_COOLDOWN = 8000L

    /** 倍速按钮文字格式：数字 + x，如 1x / 1.25x / 3x */
    private val SPEED_REGEX = Regex("^[0-9]+(\\.[0-9]+)?x$", RegexOption.IGNORE_CASE)

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

        // 短剧自动倍速配置
        scope.launch { repo.dramaEnabled.collect { dramaEnabled = it } }
        scope.launch { repo.dramaAutoMount.collect { dramaAutoMount = it } }
        scope.launch { repo.dramaIntervalMs.collect { dramaIntervalMs = it } }
        scope.launch { repo.dramaTargetSpeed.collect { dramaTargetSpeed = it } }

        // 启动短剧加速轮询
        scope.launch { dramaLoop() }

        Log.i(TAG, "无障碍服务已连接")
    }

    /**
     * 短剧加速轮询主循环。
     * 规则：每秒检测一次；找到倍速按钮时——
     *   已经是目标倍速 → 什么都不做（保护：3x 再点会变 0.75x）
     *   不是目标倍速   → 点一下，下一轮再读
     * 没有倍速按钮时（普通视频流）：若开启自动挂载，找"短剧｜xxx"挂载按钮点击。
     */
    private suspend fun dramaLoop() {
        var lastDiag = 0L
        while (true) {
            try {
                delay(dramaIntervalMs)
                if (!dramaEnabled) continue
                // 只在抖音系应用生效
                val pkg = currentRootPackage() ?: continue
                if (!isDouyin(pkg)) continue
                // 每 3 秒输出一次诊断
                val now = System.currentTimeMillis()
                val diag = (now - lastDiag) > 3000
                if (diag) lastDiag = now
                handleDrama(pkg, diag)
            } catch (e: Exception) {
                Log.e(TAG, "dramaLoop error", e)
                DramaDebug.add("异常: ${e.message}")
            }
        }
    }

    /** 当前窗口根节点所在包名 */
    private fun currentRootPackage(): String? {
        return try { rootInActiveWindow?.packageName?.toString() } catch (e: Exception) { null }
    }

    /** 是否抖音系（含分身） */
    private fun isDouyin(pkg: String): Boolean =
        pkg == "com.ss.android.ugc.aweme" ||
        pkg == "com.ss.android.ugc.aweme.lite" ||
        pkg.startsWith("com.qihoo.magic.") ||
        pkg.startsWith("com.douyin.")

    /** 短剧页面核心处理 */
    private suspend fun handleDrama(pkg: String, diag: Boolean) {
        val root = rootInActiveWindow ?: run {
            if (diag) DramaDebug.add("pkg=$pkg 但 root 为空")
            return
        }
        val nodes = collectAllNodes(root)

        // 0) 长按菜单是否已弹出？（有"倍速"标题）
        val hasMenu = nodes.any { it.text?.toString()?.trim() == "倍速" }
        if (hasMenu) {
            val menuTarget = speedToMenuText(dramaTargetSpeed)
            val target = nodes.firstOrNull { it.text?.toString()?.trim() == menuTarget }
            if (target != null) {
                DramaDebug.add("菜单已弹出: 点击 $menuTarget")
                clickNode(findClickableAncestor(target) ?: target)
            } else {
                DramaDebug.add("菜单已弹出，但找不到 $menuTarget")
            }
            return
        }

        // 1) 找底部倍速按钮（若存在，优先点击循环切换）
        val speedNode = findSpeedButton(nodes)
        if (speedNode != null) {
            val cur = (speedNode.text?.toString() ?: speedNode.contentDescription?.toString())?.trim() ?: return
            if (cur.equals(dramaTargetSpeed, ignoreCase = true)) {
                if (diag) DramaDebug.add("已在目标倍速 $cur，不点（节点数 ${nodes.size}）")
                return
            }
            DramaDebug.add("倍速 $cur → 点击切换（目标 $dramaTargetSpeed）")
            clickNode(speedNode)
            return
        }

        // 1.5) 是短剧页面但读不到倍速按钮 → 长按屏幕中央唤出菜单
        val isDramaPage = nodes.any {
            val t = it.text?.toString() ?: ""
            t.contains("集全") || (t.contains("免费") && t.length < 6)
        }
        if (isDramaPage) {
            val now = System.currentTimeMillis()
            if (now - lastLongPressTime > LONG_PRESS_COOLDOWN) {
                lastLongPressTime = now
                DramaDebug.add("短剧页面，长按屏幕中央唤出菜单")
                longPressCenter()
            } else if (diag) {
                DramaDebug.add("短剧页面，长按冷却中")
            }
            return
        }

        // 没有倍速按钮 → 可能是普通视频流
        if (!dramaAutoMount) {
            if (diag) DramaDebug.add("无倍速按钮，且已关自动挂载（节点数 ${nodes.size}）")
            return
        }
        val mountNode = findDramaMount(nodes)
        if (mountNode != null) {
            DramaDebug.add("短剧挂载: 点击进入 -> ${mountNode.text}")
            clickNode(mountNode)
        } else if (diag) {
            DramaDebug.add("未找到倍速/挂载（pkg=$pkg 节点数 ${nodes.size}）")

            // dump 全屏有文字/描述的节点，便于分析
            val rect = android.graphics.Rect()
            val info = ArrayList<String>()
            for (n in nodes) {
                val t = n.text?.toString()?.trim()
                val d = n.contentDescription?.toString()?.trim()
                if (t.isNullOrBlank() && d.isNullOrBlank()) continue
                n.getBoundsInScreen(rect)
                val yPct = (rect.centerY() * 100 / resources.displayMetrics.heightPixels)
                info.add("[${t ?: d}@${yPct}%]")
                if (info.size >= 40) break
            }
            DramaDebug.add("可见节点(text@Y%): " + info.joinToString(" "))
        }
    }

    /** 收集所有节点 */
    private fun collectAllNodes(root: android.view.accessibility.AccessibilityNodeInfo): List<android.view.accessibility.AccessibilityNodeInfo> {
        val out = ArrayList<android.view.accessibility.AccessibilityNodeInfo>()
        fun rec(n: android.view.accessibility.AccessibilityNodeInfo) {
            out.add(n)
            for (i in 0 until n.childCount) {
                val c = n.getChild(i) ?: continue
                rec(c)
            }
        }
        rec(root)
        return out
    }

    /**
     * 找底部倍速按钮：文字形如 "1x"/"1.25x"/"3x"，且 Y 坐标在屏幕下方 85% 以下。
     * 用位置约束避免误命中评论区里的同类文字。
     */
    private fun findSpeedButton(nodes: List<android.view.accessibility.AccessibilityNodeInfo>): android.view.accessibility.AccessibilityNodeInfo? {
        val screenH = resources.displayMetrics.heightPixels
        val minY = screenH * 0.85f
        val rect = android.graphics.Rect()
        for (n in nodes) {
            // 同时看 text 和 contentDescription
            val t = (n.text?.toString() ?: n.contentDescription?.toString())?.trim() ?: continue
            if (!SPEED_REGEX.matches(t)) continue
            n.getBoundsInScreen(rect)
            if (rect.centerY() >= minY) {
                return findClickableAncestor(n) ?: n
            }
        }
        return null
    }

    /**
     * 找短剧挂载按钮。
     * 特征：一个节点文字为"短剧"，其右侧同一水平线上有"|"节点（两节点是分开的）。
     * 位置约束：屏幕 42%~82% 区间（发布者上方）。
     */
    private fun findDramaMount(nodes: List<android.view.accessibility.AccessibilityNodeInfo>): android.view.accessibility.AccessibilityNodeInfo? {
        val screenH = resources.displayMetrics.heightPixels
        val minY = (screenH * 0.42f).toInt()
        val maxY = (screenH * 0.82f).toInt()
        val rect = android.graphics.Rect()

        // 1) 收集所有"短剧"节点（Y 在区间内）
        val dramaNodes = ArrayList<android.view.accessibility.AccessibilityNodeInfo>()
        for (n in nodes) {
            val t = n.text?.toString()?.trim() ?: continue
            if (t != "短剧") continue
            n.getBoundsInScreen(rect)
            val cy = rect.centerY()
            if (cy in minY..maxY) dramaNodes.add(n)
        }

        // 2) 对每个"短剧"节点，检查右侧 150px 内、同高度(±40px) 是否有"|"节点
        for (d in dramaNodes) {
            val dRect = android.graphics.Rect()
            d.getBoundsInScreen(dRect)
            val cy = dRect.centerY()
            val cx = dRect.centerX()
            val hasBar = nodes.any { n ->
                val t = n.text?.toString()?.trim() ?: return@any false
                if (t != "|" && t != "｜" && t != "I" && t != "l") return@any false
                val r = android.graphics.Rect()
                n.getBoundsInScreen(r)
                kotlin.math.abs(r.centerY() - cy) < 40 &&
                    r.centerX() > cx && (r.centerX() - cx) < 150
            }
            if (hasBar) {
                return findClickableAncestor(d) ?: d
            }
        }

        // 3) 备用：单节点文本形如 "短剧｜xxx"
        val fallback = Regex("^短剧[\\s\\|｜·:：].+")
        for (n in nodes) {
            val t = n.text?.toString()?.trim() ?: continue
            if (!fallback.containsMatchIn(t)) continue
            n.getBoundsInScreen(rect)
            val cy = rect.centerY()
            if (cy in minY..maxY) return findClickableAncestor(n) ?: n
        }
        return null
    }

    /** 向上找可点击祖先 */
    private fun findClickableAncestor(node: android.view.accessibility.AccessibilityNodeInfo): android.view.accessibility.AccessibilityNodeInfo? {
        var cur: android.view.accessibility.AccessibilityNodeInfo? = node
        var depth = 0
        while (cur != null && depth < 6) {
            if (cur.isClickable && cur.isEnabled) return cur
            cur = cur.parent
            depth++
        }
        return null
    }

    /** 长按屏幕中央偏左（避开右侧互动栏），唤出倍速菜单 */
    private suspend fun longPressCenter() {
        try {
            val w = resources.displayMetrics.widthPixels.toFloat()
            val h = resources.displayMetrics.heightPixels.toFloat()
            // 水平 30%、垂直 55%：纯视频区，避开点赞/评论/分享栏和作者信息
            val x = w * 0.30f
            val y = h * 0.55f
            val path = Path().apply { moveTo(x, y) }
            // 长按 800ms（长按阈值通常 500ms）
            val stroke = GestureDescription.StrokeDescription(path, 0, 800)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            // 带超时保护：2 秒未回调就放弃，避免协程永久挂起
            kotlinx.coroutines.withTimeoutOrNull(2000L) {
                kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
                    dispatchGesture(gesture, object : GestureResultCallback() {
                        override fun onCompleted(d: GestureDescription?) {
                            if (cont.isActive) cont.resumeWith(Result.success(Unit))
                        }
                        override fun onCancelled(d: GestureDescription?) {
                            if (cont.isActive) cont.resumeWith(Result.success(Unit))
                        }
                    }, null)
                }
            }
        } catch (e: Exception) {
            DramaDebug.add("长按失败: ${e.message}")
        }
    }

    /**
     * 倍速值转菜单文字：界面显示 "3x"，菜单里是 "3.0"。
     * 3x→3.0, 1x→1.0, 0.75x→0.75, 1.25x→1.25
     */
    private fun speedToMenuText(speed: String): String {
        val num = speed.removeSuffix("x").removeSuffix("X")
        return if (num.contains(".")) num else "$num.0"
    }

    /** 点击节点 */
    private fun clickNode(node: android.view.accessibility.AccessibilityNodeInfo) {
        try {
            node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
        } catch (e: Exception) {
            Log.e(TAG, "clickNode failed", e)
        }
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
