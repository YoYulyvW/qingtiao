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
    @Volatile private var cachedCondRules: List<com.autoskip.helper.data.CondRuleEntity> = emptyList()

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

    // ===== 倍速按钮坐标点击（因按钮是 ImageView，无文字，只能按坐标点）=====
    @Volatile private var dramaBaseW = 1080
    @Volatile private var dramaBaseH = 2340
    @Volatile private var dramaTapX = 652      // 基准分辨率下的 X
    @Volatile private var dramaTapY = 1137     // 基准分辨率下的 Y
    @Volatile private var dramaClicks = 4      // 连点次数（1x→3x 需 4 次）
    private var lastDramaClickTime = 0L
    private val DRAMA_CLICK_COOLDOWN = 12000L  // 长按冷却
    /** 菜单已点击的时间戳（>0 表示本轮已点过倍速，需检查菜单是否缩回） */
    private var menuClickedTime = 0L
    /** 已切过倍速的集数标识（如"第1集"），避免同一集重复长按 */
    private var lastSpedEpisode: String? = null
    /** 当前看到的集数 */
    private var currentEpisode: String? = null
    /** 长按前保存的集数（菜单会遮住屏幕，届时读不到集数） */
    private var pendingEpisode: String? = null
    /** 已发起长按、等待菜单弹出的时间戳（>0 表示"菜单即将出现"，禁止再长按） */
    private var expectingMenuTime = 0L
    private val EXPECT_MENU_WINDOW = 6000L

    /** 倍速按钮文字格式：数字 + x，如 1x / 1.25x / 3x */
    private val SPEED_REGEX = Regex("^[0-9]+(\\.[0-9]+)?x$", RegexOption.IGNORE_CASE)

    /** 菜单项黑名单：这些文字绝不能点（误点会触发分享/举报等） */
    private val MENU_BLACKLIST = setOf(
        "推荐", "转发到日常", "合拍", "举报", "清屏播放",
        "收藏", "分享", "复制链接", "保存本地", "不感兴趣",
        "倍速", "0.75", "1.0", "1.25", "1.5", "2.0", "3.0"
    )

    /** 卡死看门狗：最近一次"有进展"的时间戳 */
    @Volatile private var lastProgressTime = 0L
    /** 菜单关闭看门狗：最近一次检测到菜单存在的时间 */
    @Volatile private var menuVisibleSince = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        val repo = App.instance.repo
        scope.launch { repo.enabled.collect { enabled = it } }
        scope.launch { repo.clickDelayMs.collect { clickDelay = it } }
        scope.launch { repo.rules.collect { cachedRules = it } }
        scope.launch { repo.condRules.collect { cachedCondRules = it } }
        scope.launch { repo.whitelistEnabled.collect { whitelistEnabled = it } }
        scope.launch { repo.whitelistPkgs.collect { whitelistPkgs = it } }
        scope.launch { repo.strictClose.collect { strictClose = it } }

        // 短剧自动倍速配置
        scope.launch { repo.dramaEnabled.collect { dramaEnabled = it } }
        scope.launch { repo.dramaAutoMount.collect { dramaAutoMount = it } }
        scope.launch { repo.dramaIntervalMs.collect { dramaIntervalMs = it } }
        scope.launch { repo.dramaTargetSpeed.collect { dramaTargetSpeed = it } }
        scope.launch { repo.dramaBaseW.collect { dramaBaseW = it } }
        scope.launch { repo.dramaBaseH.collect { dramaBaseH = it } }
        scope.launch { repo.dramaTapX.collect { dramaTapX = it } }
        scope.launch { repo.dramaTapY.collect { dramaTapY = it } }
        scope.launch { repo.dramaClicks.collect { dramaClicks = it } }
        scope.launch { repo.dramaDebug.collect { DramaDebug.enabled = it } }

        // 启动短剧加速轮询
        scope.launch { dramaLoop() }
        // 启动看门狗（独立协程，主循环卡住时也能兜底）
        scope.launch { menuWatchdog() }

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
        var lastHeartbeat = 0L
        while (true) {
            try {
                delay(dramaIntervalMs)
                if (!dramaEnabled) continue
                // 只在抖音系应用生效
                val pkg = currentRootPackage() ?: continue
                if (!isDouyin(pkg)) continue
                // 系统 UI 一律忽略（与规则一致）
                if (Matcher.isSystemUi(pkg)) continue
                // 白名单模式：非白名单应用忽略（与规则一致，支持通配符）
                if (whitelistEnabled && !Matcher.matchesWhitelist(pkg, whitelistPkgs)) continue
                val now = System.currentTimeMillis()
                lastProgressTime = now   // 看门狗用：标记循环有进展
                // 心跳：每 5 秒输出一次，证明循环没卡住
                if (now - lastHeartbeat > 5000) {
                    lastHeartbeat = now
                    DramaDebug.add("轮询中…")
                }
                // 每 3 秒输出一次诊断
                val diag = (now - lastDiag) > 3000
                if (diag) lastDiag = now
                handleDrama(pkg, diag)
            } catch (e: Exception) {
                Log.e(TAG, "dramaLoop error", e)
                DramaDebug.add("异常: ${e.message}")
            }
        }
    }

    /**
     * 看门狗：独立协程，每 2 秒检查一次。
     * 若菜单出现后超过 6 秒仍未消失 → 强制按返回键关闭，并复位状态。
     * 即使主循环(dramaLoop)卡住，这个协程也能兜底恢复。
     */
    private suspend fun menuWatchdog() {
        while (true) {
            try {
                delay(2000)
                if (!dramaEnabled) continue
                val now = System.currentTimeMillis()

                // 判据1：主循环超过 6 秒无进展 → 判定卡死，强制恢复
                val stuck = lastProgressTime > 0 && now - lastProgressTime > 6000
                // 判据2：菜单已出现但超过 6 秒未消失
                val menuTooLong = menuVisibleSince > 0 && now - menuVisibleSince > 6000

                if (stuck || menuTooLong) {
                    DramaDebug.add(
                        if (stuck) "看门狗: 主循环卡死>6秒，强制返回键"
                        else "看门狗: 菜单超过6秒未关，强制返回键"
                    )
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        performGlobalAction(GLOBAL_ACTION_BACK)
                    }
                    // 复位所有状态，让主循环重新开始
                    menuVisibleSince = 0
                    menuClickedTime = 0
                    expectingMenuTime = 0
                    lastDramaClickTime = 0
                    lastProgressTime = System.currentTimeMillis()
                }
            } catch (e: Exception) {
                Log.e(TAG, "watchdog error", e)
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
        // ★ 长按后窗口期：完全不碰节点树。
        //   长按会弹菜单+播放窗口动画，此期间读节点树会与无障碍框架死锁（卡死）。
        val nowMs = System.currentTimeMillis()
        if (expectingMenuTime != 0L && nowMs - expectingMenuTime < 4000L) {
            if (diag) DramaDebug.add("长按后等待窗口稳定（${(nowMs - expectingMenuTime) / 1000} 秒）")
            return
        }
        val root = rootInActiveWindow ?: run {
            if (diag) DramaDebug.add("pkg=$pkg 但 root 为空")
            return
        }
        val nodes = collectAllNodes(root)

        // 提取当前集数（发布者下方，形如"第1集"）
        currentEpisode = extractEpisode(nodes)

        // 0) 长按菜单是否已弹出？（多重特征，任一命中即视为菜单）
        val hasMenu = nodes.any {
            val t = it.text?.toString()?.trim() ?: ""
            t == "倍速" || t == "清屏播放" || t == "合拍" ||
                t == "转发到日常" || t == "举报" ||
                t == "0.75" || t == "1.0" || t == "1.25" ||
                t == "1.5" || t == "2.0" || t == "3.0"
        }
        if (hasMenu) {
            expectingMenuTime = 0L   // 菜单已确认出现，清除等待标记
            // 看门狗：记录菜单出现时间
            if (menuVisibleSince == 0L) menuVisibleSince = System.currentTimeMillis()

            // 只在首次看到菜单时处理（menuClickedTime 非 0 表示本次已处理）
            if (menuClickedTime == 0L) {
                val menuTarget = speedToMenuText(dramaTargetSpeed)
                val target = nodes.firstOrNull {
                    val t = it.text?.toString()?.trim() ?: ""
                    // 只点目标倍速，且必须不在黑名单里
                    t == menuTarget && t !in MENU_BLACKLIST
                }
                if (target != null) {
                    val ep = pendingEpisode ?: currentEpisode
                    DramaDebug.add("菜单已弹出: 点击 $menuTarget（集 ${ep ?: "?"}）")
                    clickNode(findClickableAncestor(target) ?: target)
                    if (ep != null) lastSpedEpisode = ep
                } else {
                    DramaDebug.add("菜单已弹出，找不到 $menuTarget（或命中黑名单）")
                }
                menuClickedTime = System.currentTimeMillis()
                startMenuCloseCheck()
            }
            return
        } else {
            // 菜单已消失
            menuClickedTime = 0L
            menuVisibleSince = 0L
        }

        // 1) 找底部倍速按钮（若某版本能读到文字）
        val speedNode = findSpeedButton(nodes)
        if (speedNode != null) {
            val cur = (speedNode.text?.toString() ?: speedNode.contentDescription?.toString())?.trim() ?: return
            if (cur.equals(dramaTargetSpeed, ignoreCase = true)) {
                if (diag) DramaDebug.add("已在目标倍速 $cur，不点")
                return
            }
            DramaDebug.add("倍速 $cur → 点击切换")
            clickNode(speedNode)
            return
        }

        // 2) 是短剧页面 → 长按视频中心唤出菜单
        val isDramaPage = nodes.any {
            val t = it.text?.toString() ?: ""
            t.contains("集全") || (t.contains("免费") && t.length < 6)
        }
        if (isDramaPage) {
            // 已发起长按、正等菜单出现 → 绝不重复长按（避免点到菜单项）
            val now0 = System.currentTimeMillis()
            if (expectingMenuTime != 0L && now0 - expectingMenuTime < EXPECT_MENU_WINDOW) {
                if (diag) DramaDebug.add("已长按，等待菜单出现中（${(now0 - expectingMenuTime) / 1000} 秒）")
                return
            }
            // 当前集已切过倍速 → 不再长按
            if (currentEpisode != null && currentEpisode == lastSpedEpisode) {
                if (diag) DramaDebug.add("集 ${currentEpisode} 已切过倍速，跳过")
                return
            }
            val now = System.currentTimeMillis()
            if (now - lastDramaClickTime > DRAMA_CLICK_COOLDOWN) {
                lastDramaClickTime = now
                pendingEpisode = currentEpisode   // 保存集数，菜单弹出后用它
                expectingMenuTime = now           // 标记"等菜单出现"
                DramaDebug.add("短剧页面（集 ${currentEpisode ?: "?"}），长按视频中心唤出菜单")
                longPressCenter()
            } else if (diag) {
                DramaDebug.add("长按冷却中（剩余 ${(DRAMA_CLICK_COOLDOWN - (now - lastDramaClickTime)) / 1000} 秒）")
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

    /**
     * 菜单点击后检查是否缩回，未缩回则按返回键。
     * 循环重试：按返回 → 等 1 秒 → 再检查，最多 3 次，直到菜单消失。
     * 判断依据：菜单里有"倍速"标题；短剧页面有集数/发布者。
     */
    private fun startMenuCloseCheck() {
        scope.launch {
            for (attempt in 1..3) {
                delay(1000)
                try {
                    val root = rootInActiveWindow ?: continue
                    val nodes = collectAllNodes(root)
                    val stillHasMenu = nodes.any { it.text?.toString()?.trim() == "倍速" }
                    if (!stillHasMenu) {
                        DramaDebug.add("菜单已缩回（第 ${attempt} 次检查）")
                        menuClickedTime = 0L
                        return@launch
                    }
                    // 菜单还在 → 按返回键
                    DramaDebug.add("菜单未缩回，按返回键（第 $attempt 次）")
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        performGlobalAction(GLOBAL_ACTION_BACK)
                    }
                } catch (e: Exception) {
                    DramaDebug.add("关闭菜单异常: ${e.message}")
                }
            }
            // 3 次后仍可能没关掉，最后再检查一次
            delay(1000)
            val root = rootInActiveWindow
            val stillHasMenu = root != null &&
                collectAllNodes(root).any { it.text?.toString()?.trim() == "倍速" }
            if (stillHasMenu) {
                DramaDebug.add("重试 3 次菜单仍未缩回")
            } else {
                DramaDebug.add("菜单已缩回")
            }
            menuClickedTime = 0L
        }
    }

    /**
     * 提取当前集数标识，如"第1集"。
     * 抖音会预加载相邻集、描述区也可能出现集数，屏幕上可能有多个"第N集"节点。
     * 策略：优先取【紧跟在发布者(@xxx)下方】的那个（当前集标题位置）；
     *       找不到发布者时，退化为取可见屏幕内最靠上的。
     */
    private fun extractEpisode(nodes: List<android.view.accessibility.AccessibilityNodeInfo>): String? {
        val regex = Regex("第\\s*(\\d+)\\s*集")
        val screenH = resources.displayMetrics.heightPixels
        val screenW = resources.displayMetrics.widthPixels
        val rect = android.graphics.Rect()

        // 1) 找发布者节点：@开头，取最靠上的那个
        var authorBottom = Int.MIN_VALUE
        for (n in nodes) {
            val t = n.text?.toString()?.trim() ?: continue
            if (!t.startsWith("@") || t.length < 2) continue
            n.getBoundsInScreen(rect)
            if (rect.bottom <= 0 || rect.top >= screenH) continue
            if (rect.bottom > authorBottom) authorBottom = rect.bottom
        }

        var best: String? = null
        var bestY = Int.MAX_VALUE

        for (n in nodes) {
            val t = n.text?.toString()?.trim() ?: continue
            val m = regex.find(t) ?: continue
            n.getBoundsInScreen(rect)
            // 只考虑可见屏幕内
            if (rect.bottom <= 0 || rect.top >= screenH) continue
            if (rect.right <= 0 || rect.left >= screenW) continue

            val hasAuthor = authorBottom != Int.MIN_VALUE
            if (hasAuthor) {
                // 必须位于发布者下方
                if (rect.top < authorBottom) continue
            }
            // 取最靠上的（离发布者最近）
            if (rect.top < bestY) {
                bestY = rect.top
                best = "第${m.groupValues[1]}集"
            }
        }

        // 2) 若按发布者过滤后没结果，退化为可见屏幕内最靠上
        if (best == null) {
            for (n in nodes) {
                val t = n.text?.toString()?.trim() ?: continue
                val m = regex.find(t) ?: continue
                n.getBoundsInScreen(rect)
                if (rect.bottom <= 0 || rect.top >= screenH) continue
                if (rect.right <= 0 || rect.left >= screenW) continue
                if (rect.top < bestY) {
                    bestY = rect.top
                    best = "第${m.groupValues[1]}集"
                }
            }
        }
        return best
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
     * 找短剧挂载按钮（严格版）。
     * 三重特征同时满足才算：
     *   1. 存在文字为"短剧"的节点
     *   2. 其右侧同一水平线（±40px）150px 内有"|"节点
     *   3. 该位置在"@发布者"节点的【正上方】，且 X 轴与发布者大致对齐
     * 这样能排除掉描述区里出现的"短剧"字样。
     */
    private fun findDramaMount(nodes: List<android.view.accessibility.AccessibilityNodeInfo>): android.view.accessibility.AccessibilityNodeInfo? {
        val rect = android.graphics.Rect()

        // 先找发布者节点：文字以 @ 开头
        val authorNode = nodes.firstOrNull {
            val t = it.text?.toString()?.trim() ?: ""
            t.startsWith("@") && t.length > 1
        }
        val authorRect = android.graphics.Rect()
        val hasAuthor = authorNode != null
        if (hasAuthor) authorNode!!.getBoundsInScreen(authorRect)

        // 收集所有"短剧"节点
        val dramaNodes = ArrayList<android.view.accessibility.AccessibilityNodeInfo>()
        for (n in nodes) {
            val t = n.text?.toString()?.trim() ?: continue
            if (t != "短剧") continue
            dramaNodes.add(n)
        }

        for (d in dramaNodes) {
            val dRect = android.graphics.Rect()
            d.getBoundsInScreen(dRect)
            val cy = dRect.centerY()
            val cx = dRect.centerX()

            // 条件A：右侧 150px 内、同高度(±40px) 有 "|"
            val hasBar = nodes.any { n ->
                val t = n.text?.toString()?.trim() ?: return@any false
                if (t != "|" && t != "｜" && t != "I" && t != "l") return@any false
                val r = android.graphics.Rect()
                n.getBoundsInScreen(r)
                kotlin.math.abs(r.centerY() - cy) < 40 &&
                    r.centerX() > cx && (r.centerX() - cx) < 150
            }
            if (!hasBar) continue

            // 条件B：在发布者【上方】（短剧的 Y < 发布者的 Y），且 X 轴大致对齐（左侧）
            if (hasAuthor) {
                val isAbove = cy < authorRect.centerY()
                val xAligned = kotlin.math.abs(cx - authorRect.centerX()) < 400 ||
                    kotlin.math.abs(dRect.left - authorRect.left) < 300
                if (!isAbove || !xAligned) continue
            } else {
                // 没有发布者时，退化为位置区间约束
                val screenH = resources.displayMetrics.heightPixels
                if (cy < screenH * 0.42f || cy > screenH * 0.82f) continue
            }

            return findClickableAncestor(d) ?: d
        }

        // 备用：单节点文本形如 "短剧｜xxx"，且位于发布者上方
        val fallback = Regex("^短剧[\\s\\|｜·:：].+")
        for (n in nodes) {
            val t = n.text?.toString()?.trim() ?: continue
            if (!fallback.containsMatchIn(t)) continue
            n.getBoundsInScreen(rect)
            if (hasAuthor && rect.centerY() >= authorRect.centerY()) continue
            return findClickableAncestor(n) ?: n
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

    /** 物理全屏尺寸 */
    private fun realScreenSize(): Pair<Float, Float> {
        val wm = getSystemService(WINDOW_SERVICE) as android.view.WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.currentWindowMetrics.bounds
            Pair(b.width().toFloat(), b.height().toFloat())
        } else {
            val p = android.graphics.Point()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealSize(p)
            Pair(p.x.toFloat(), p.y.toFloat())
        }
    }

    /**
     * 长按视频区域唤出倍速菜单。
     * 随机位置：X 轴在中心 ±200dp 内随机；Y 轴在（中心上移 80dp）±50dp 内随机。
     * 避免固定坐标落在菜单项上（如"推荐"）导致误触。
     */
    private fun longPressCenter() {
        try {
            val dm = resources.displayMetrics
            val density = dm.density
            val cx = dm.widthPixels * 0.5f
            // Y 基准：中心上移 80dp
            val cyBase = dm.heightPixels * 0.5f - 80f * density
            // X：中心 ±200dp 随机；Y：基准 ±50dp 随机
            val randX = (Math.random() * 2 - 1).toFloat() * 200f * density
            val randY = (Math.random() * 2 - 1).toFloat() * 50f * density
            val x = cx + randX
            val y = cyBase + randY
            DramaDebug.add("长按位置: (${x.toInt()},${y.toInt()})")
            val path = Path().apply { moveTo(x, y) }
            // 长按 800ms（长按阈值通常 500ms）
            val stroke = GestureDescription.StrokeDescription(path, 0, 800)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            mainHandler.post {
                try {
                    dispatchGesture(gesture, null, null)
                } catch (e: Exception) {
                    Log.e(TAG, "dispatchGesture failed", e)
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

        // 0) 条件规则优先（更具体："有X且Y→动作"）
        if (cachedCondRules.isNotEmpty()) {
            val cond = Matcher.matchCondRule(root, cachedCondRules, pkg)
            if (cond != null) {
                performCondAction(cond, pkg)
                return
            }
        }

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

    /** 执行条件规则的动作 */
    private fun performCondAction(cond: CondMatchResult, pkg: String) {
        lastClickTime = System.currentTimeMillis()
        mainHandler.postDelayed({
            try {
                when (cond.actionType) {
                    com.autoskip.helper.data.CondAction.BACK -> {
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        Log.i(TAG, "条件规则[返回键]: ${cond.rule.name}")
                    }
                    com.autoskip.helper.data.CondAction.CLICK_TEXT -> {
                        cond.node?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                        Log.i(TAG, "条件规则[点击文字]: ${cond.matchedText}")
                    }
                    com.autoskip.helper.data.CondAction.CLICK_ICON -> {
                        cond.node?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                        Log.i(TAG, "条件规则[点击图标]: ${cond.rule.name}")
                    }
                }

                val appLabel = runCatching {
                    packageManager.getApplicationLabel(
                        packageManager.getApplicationInfo(pkg, 0)
                    ).toString()
                }.getOrNull()

                val actionDesc = when (cond.actionType) {
                    com.autoskip.helper.data.CondAction.BACK -> "返回键"
                    com.autoskip.helper.data.CondAction.CLICK_TEXT -> "点击 ${cond.matchedText}"
                    else -> "点击图标"
                }
                val log = LogEntity(
                    packageName = pkg,
                    appLabel = appLabel,
                    rule = "[条件] ${cond.rule.name}",
                    matchedText = actionDesc
                )
                val repo = App.instance.repo
                scope.launch {
                    repo.addLog(log)
                    repo.bumpCondHit(cond.rule.id)
                }
            } catch (e: Exception) {
                Log.e(TAG, "条件规则执行失败", e)
            }
        }, clickDelay)
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
