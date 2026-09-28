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
import kotlinx.coroutines.asCoroutineDispatcher
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

    /** 学习模式回调：抓到用户点击的节点时触发 */
    @Volatile var learnCallback: ((MatchedNode) -> Unit)? = null

    private var lastClickTime = 0L
    private val CLICK_COOLDOWN = 800L

    /** 事件标记：onAccessibilityEvent 只置位，由 processEventLoop 在 IO 上处理（防主线程阻塞） */
    @Volatile private var pendingEvent = false

    /**
     * 单线程调度器：所有无障碍节点树访问都串行化到这里。
     * 多协程（dramaLoop / processEventLoop / 延时检测）并发遍历节点树会与
     * 无障碍框架死锁，表现为"卡住、切后台才恢复"。串行后彻底避免。
     */
    private val nodeDispatcher = java.util.concurrent.Executors
        .newSingleThreadExecutor { r -> Thread(r, "autoskip-node") }
        .asCoroutineDispatcher()

    // 重复弹窗检测：若同一弹窗点击后很快再次出现，说明点击无效，改用返回键
    private var lastPopupSignature: String? = null
    private var lastPopupTime = 0L
    private val REPEAT_WINDOW = 3000L  // 3 秒内同一弹窗再次出现 → 判定点击无效

    // ===== 短剧自动倍速 =====
    @Volatile private var dramaEnabled = false
    @Volatile private var dramaLongPressOn = true
    @Volatile private var condDramaOnlyOn = true
    @Volatile private var dramaClickSpeedOn = true
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

    /** 菜单项黑名单：这些文字绝不能点（误点会触发分享/举报等）。
     *  注意：不含倍速值（0.75~3.0），否则会把要点的目标倍速也拦掉。 */
    private val MENU_BLACKLIST = setOf(
        "推荐", "转发到日常", "合拍", "举报", "清屏播放",
        "收藏", "分享", "复制链接", "保存本地", "不感兴趣",
        "倍速", "识别图片"
    )

    /** 系统UI（多任务中心/桌面）是否在前台。为 true 时暂停所有节点遍历，避免卡顿 */
    @Volatile private var systemUiForeground = false

    /** 系统UI（多任务中心/桌面）进入前台的时间戳，期间暂停轮询，避免卡顿 */
    @Volatile private var systemUiForegroundTime = 0L
    private val SYSTEM_UI_PAUSE_WINDOW = 5000L

    /** 是否处于"退出短剧"页面：为 true 时暂停所有动作 */
    @Volatile private var exitDramaPaused = false

    /** 条件规则延时检测：记录已触发待确认的规则（签名 -> 触发时间） */
    private val pendingCondRules = HashMap<String, Long>()

    // ===== 呼出间隔配置 =====
    @Volatile private var dramaImgInterval = 1     // 「识别图片」版：每N集呼出
    @Volatile private var dramaNormalInterval = 5  // 其他版：每N集呼出
    /** 上次长按呼出的集数数字，-1 表示未呼出过（新剧/刚进入） */
    private var lastLongPressEpNum = -1
    /** 本次长按前的集数数字（菜单弹出后用它记录） */
    private var pendingEpisodeNum: Int? = null
    /** 上次的发布者（@xxx），用于检测新剧 */
    private var lastAuthor: String? = null
    /** 上次菜单是否是「识别图片」版（决定用哪个间隔） */
    @Volatile private var menuHasImg = false

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

        // 短剧自动倍速配置
        scope.launch { repo.dramaEnabled.collect { dramaEnabled = it } }
        scope.launch { repo.dramaLongPress.collect { dramaLongPressOn = it } }
        scope.launch { repo.condDramaOnly.collect { condDramaOnlyOn = it } }
        scope.launch { repo.dramaClickSpeed.collect { dramaClickSpeedOn = it } }
        scope.launch { repo.dramaAutoMount.collect { dramaAutoMount = it } }
        scope.launch { repo.dramaIntervalMs.collect { dramaIntervalMs = it } }
        scope.launch { repo.dramaTargetSpeed.collect { dramaTargetSpeed = it } }
        scope.launch { repo.dramaBaseW.collect { dramaBaseW = it } }
        scope.launch { repo.dramaBaseH.collect { dramaBaseH = it } }
        scope.launch { repo.dramaTapX.collect { dramaTapX = it } }
        scope.launch { repo.dramaTapY.collect { dramaTapY = it } }
        scope.launch { repo.dramaClicks.collect { dramaClicks = it } }
        scope.launch { repo.dramaDebug.collect { DramaDebug.enabled = it } }
        scope.launch { repo.dramaImgInterval.collect { dramaImgInterval = it } }
        scope.launch { repo.dramaNormalInterval.collect { dramaNormalInterval = it } }

        // 启动短剧加速轮询（单线程节点调度器：与事件处理串行，避免并发遍历死锁）
        scope.launch(nodeDispatcher) { dramaLoop() }
        // 启动事件处理循环（同上，串行化节点访问）
        scope.launch(nodeDispatcher) { processEventLoop() }
        // 启动主动兜底轮询：不依赖无障碍事件，定期检查屏幕，防事件流中断导致停摆
        scope.launch(nodeDispatcher) { screenWatchdog() }

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
                // 只用"当前前台窗口"判断，无状态、绝不卡住
                val pkg = currentRootPackage() ?: continue
                // 前台是系统UI（桌面/多任务）→ 跳过这轮
                if (Matcher.isSystemUi(pkg)) continue
                // 非抖音系 → 跳过
                if (!isDouyin(pkg)) continue
                // 白名单模式：非白名单应用忽略（与规则一致，支持通配符）
                if (whitelistEnabled && !Matcher.matchesWhitelist(pkg, whitelistPkgs)) continue
                val now = System.currentTimeMillis()
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
        if (expectingMenuTime != 0L && nowMs - expectingMenuTime < 2500L) {
            if (diag) DramaDebug.add("长按后等待窗口稳定（${(nowMs - expectingMenuTime) / 1000} 秒）")
            return
        }
        val root = rootInActiveWindow ?: run {
            if (diag) DramaDebug.add("pkg=$pkg 但 root 为空")
            return
        }
        // ★ 重新校验：当前活动窗口必须仍是抖音系（防多任务/桌面的陈旧 root 误触发长按）
        val activePkg = try { root.packageName?.toString() } catch (e: Exception) { null }
        if (activePkg == null || !isDouyin(activePkg) || Matcher.isSystemUi(activePkg)) {
            if (diag) DramaDebug.add("当前活动窗口非抖音（$activePkg），跳过")
            return
        }
        val nodes = collectAllNodes(root)

        // ★ "退出短剧"弹窗（含「退出短剧」「返回并退出」）→ 暂停所有动作，不做任何点击
        val hasExitDrama = nodes.any {
            val t = it.text?.toString()?.trim() ?: ""
            t == "退出短剧" || t == "返回并退出"
        }
        if (hasExitDrama) {
            if (!exitDramaPaused) {
                exitDramaPaused = true
                DramaDebug.add("检测到「退出短剧」弹窗，暂停所有动作")
            }
            return
        }
        // 恢复条件：出现 集 / 全 / 免费（正常短剧页特征）→ 解除暂停
        val hasEpisode = nodes.any {
            val t = it.text?.toString() ?: ""
            Regex("第\\s*\\d+\\s*集").containsMatchIn(t) ||
                t.contains("集全") || t.contains("免费")
        }
        if (exitDramaPaused) {
            if (hasEpisode) {
                exitDramaPaused = false
                DramaDebug.add("短剧页面已恢复，继续工作")
            } else {
                return   // 仍在退出页/过渡页，继续暂停
            }
        }

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
            // 记录菜单类型（是否含"识别图片"），决定下次间隔
            menuHasImg = nodes.any { it.text?.toString()?.trim() == "识别图片" }

            // 只在首次看到菜单时处理（menuClickedTime 非 0 表示本次已处理）
            if (menuClickedTime == 0L) {
                val menuTarget = speedToMenuText(dramaTargetSpeed)
                val target = nodes.firstOrNull {
                    val t = it.text?.toString()?.trim() ?: ""
                    // 只点目标倍速，且必须不在黑名单里
                    t == menuTarget && t !in MENU_BLACKLIST
                }
                if (target != null) {
                    if (dramaClickSpeedOn) {
                        val ep = pendingEpisode ?: currentEpisode
                        DramaDebug.add("菜单已弹出: 点击 $menuTarget（集 ${ep ?: "?"}）")
                        clickNode(findClickableAncestor(target) ?: target)
                        if (ep != null) lastSpedEpisode = ep
                    } else {
                        DramaDebug.add("菜单已弹出，但「自动点击倍数」已关闭")
                    }
                } else {
                    val menuTexts = nodes.mapNotNull { it.text?.toString()?.trim()?.takeIf { t -> t.isNotBlank() } }
                        .filter { it in setOf("0.75","1.0","1.25","1.5","2.0","3.0") || it.contains("倍速") }
                    DramaDebug.add("菜单已弹出，找不到 $menuTarget（实际倍速项: ${menuTexts.joinToString(",")}）")
                }
                menuClickedTime = System.currentTimeMillis()
                // 记录本次呼出的集数数字（无论是否点到 3.0，都算呼出过）
                if (pendingEpisodeNum != null) lastLongPressEpNum = pendingEpisodeNum!!
            }
            return
        } else {
            // 菜单已消失
            menuClickedTime = 0L
        }

        // 1) 找底部倍速按钮（若某版本能读到文字）
        val speedNode = findSpeedButton(nodes)
        if (speedNode != null) {
            val cur = (speedNode.text?.toString() ?: speedNode.contentDescription?.toString())?.trim() ?: return
            if (cur.equals(dramaTargetSpeed, ignoreCase = true)) {
                if (diag) DramaDebug.add("已在目标倍速 $cur，不点")
                return
            }
            if (dramaClickSpeedOn) {
                DramaDebug.add("倍速 $cur → 点击切换")
                clickNode(speedNode)
            } else if (diag) {
                DramaDebug.add("倍速 $cur，但「自动点击倍数」已关闭")
            }
            return
        }

        // 2) 是短剧页面 → 长按视频中心唤出菜单
        val isDramaPage = nodes.any {
            val t = it.text?.toString() ?: ""
            t.contains("集全") || (t.contains("免费") && t.length < 6)
        }
        // 短剧播放页顶部【没有"搜索"】（普通视频流有搜索栏）
        val hasSearch = nodes.any {
            val t = it.text?.toString()?.trim() ?: ""
            t == "搜索" || t == "搜你想看的"
        }
        if (isDramaPage && !hasSearch) {
            // 已发起长按、正等菜单出现 → 绝不重复长按（避免点到菜单项）
            val now0 = System.currentTimeMillis()
            if (expectingMenuTime != 0L && now0 - expectingMenuTime < EXPECT_MENU_WINDOW) {
                if (diag) DramaDebug.add("已长按，等待菜单出现中（${(now0 - expectingMenuTime) / 1000} 秒）")
                return
            }

            val curNum = extractEpisodeNumber(nodes)
            val curAuthor = extractAuthor(nodes)

            // 新剧检测：① 集数回退 ② 发布者变化 ③ 集数跨度异常大
            val newByEpisode = curNum != null && lastLongPressEpNum > 0 && curNum < lastLongPressEpNum
            val newByAuthor = curAuthor != null && lastAuthor != null && curAuthor != lastAuthor
            val newByGap = curNum != null && lastLongPressEpNum > 0 &&
                (curNum - lastLongPressEpNum) > 30   // 跨度>30 视为跨剧
            if (newByEpisode || newByAuthor || newByGap) {
                val reason = when {
                    newByEpisode -> "集数回退"
                    newByAuthor -> "发布者变化"
                    else -> "集数跨度大"
                }
                DramaDebug.add("检测到新剧（$reason），重置呼出计数与菜单类型")
                lastLongPressEpNum = -1
                menuHasImg = false
            }
            if (curAuthor != null) lastAuthor = curAuthor

            // 本次用哪个间隔：上次菜单是"识别图片"版→img间隔，否则→普通间隔
            val interval = if (menuHasImg) dramaImgInterval else dramaNormalInterval

            val shouldPress: Boolean = when {
                curNum == null -> {
                    // 读不到集数 → 退化为"每集一次"（用字符串对比）
                    currentEpisode != null && currentEpisode != lastSpedEpisode
                }
                lastLongPressEpNum == -1 -> true                    // 首次/新剧 → 呼出
                curNum - lastLongPressEpNum >= interval -> true     // 达到间隔 → 呼出
                else -> false
            }

            if (!shouldPress) {
                if (diag) DramaDebug.add("集 ${currentEpisode ?: "?"}，距上次呼出（第 $lastLongPressEpNum 集）未达间隔 $interval，跳过")
                return
            }

            // 短时冷却：仅同一集内生效（换集后立即允许长按，避免等待）
            val now = System.currentTimeMillis()
            val sameEpisode = curNum != null && curNum == pendingEpisodeNum
            if (sameEpisode && now - lastDramaClickTime < 3000) {
                if (diag) DramaDebug.add("同集长按冷却中")
                return
            }

            if (!dramaLongPressOn) {
                if (diag) DramaDebug.add("「自动长按」已关闭，不呼出菜单")
                return
            }
            lastDramaClickTime = now
            pendingEpisode = currentEpisode
            pendingEpisodeNum = curNum
            expectingMenuTime = now
            // ★ 立即记录本次呼出集数（不等菜单检测，避免窗口期错过导致重复长按）
            if (curNum != null) lastLongPressEpNum = curNum
            DramaDebug.add("短剧页面（集 ${currentEpisode ?: "?"}），长按呼出菜单（间隔 $interval，${if (menuHasImg) "识别图片版" else "普通版"}）")
            longPressCenter()
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
            // 进入新剧：重置呼出计数与菜单类型
            lastLongPressEpNum = -1
            menuHasImg = false
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

    /** 提取发布者（@开头），用于检测新剧 */
    private fun extractAuthor(nodes: List<android.view.accessibility.AccessibilityNodeInfo>): String? {
        for (n in nodes) {
            val t = n.text?.toString()?.trim() ?: continue
            if (t.startsWith("@") && t.length > 1) return t
        }
        return null
    }

    /** 提取当前集数的数字，如"第43集"→43；找不到返回 null */
    private fun extractEpisodeNumber(nodes: List<android.view.accessibility.AccessibilityNodeInfo>): Int? {
        val ep = extractEpisode(nodes) ?: return null
        val m = Regex("(\\d+)").find(ep) ?: return null
        return m.groupValues[1].toIntOrNull()
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
            val screenW = dm.widthPixels
            val screenH = dm.heightPixels
            val cx = screenW * 0.5f
            // Y 基准：中心上移 80dp
            val cyBase = screenH * 0.5f - 80f * density
            // X：中心 ±200dp 随机；Y：基准 ±50dp 随机
            val randX = (Math.random() * 2 - 1).toFloat() * 200f * density
            val randY = (Math.random() * 2 - 1).toFloat() * 50f * density
            // ★ 钳制到屏幕内（留 20px 边距），避免负坐标或越界导致手势派发失败
            val margin = 20f
            val x = (cx + randX).coerceIn(margin, screenW - margin)
            val y = (cyBase + randY).coerceIn(margin, screenH - margin)
            DramaDebug.add("长按位置: (${x.toInt()},${y.toInt()})")
            val path = Path().apply { moveTo(x, y) }
            // 长按 800ms（长按阈值通常 500ms）
            val stroke = GestureDescription.StrokeDescription(path, 0, 800)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            mainHandler.post {
                try {
                    val ok = dispatchGesture(gesture, null, null)
                    if (!ok) DramaDebug.add("长按手势派发返回 false")
                } catch (e: Exception) {
                    Log.e(TAG, "dispatchGesture failed", e)
                    DramaDebug.add("长按手势异常: ${e.message}")
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

        // 系统 UI 状态检测：多任务中心/桌面在前台时，暂停短剧轮询（避免卡顿）
        if (Matcher.isSystemUi(pkg)) {
            systemUiForeground = true
            return
        } else if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // 非系统 UI 的应用进入前台 → 解除暂停
            systemUiForeground = false
        }

        // 白名单模式：非白名单应用直接忽略（支持通配符）
        if (whitelistEnabled && !Matcher.matchesWhitelist(pkg, whitelistPkgs)) return

        // ★ 关键：不在主线程做节点遍历（会阻塞无障碍框架，表现为"卡住"），
        //   只置标记，由 processEventLoop 在 IO 线程处理。
        pendingEvent = true
    }

    /**
     * 事件处理循环（IO 线程）：onAccessibilityEvent 只置 pendingEvent，
     * 这里做实际的节点遍历/匹配/点击，避免阻塞主线程导致无障碍事件停摆。
     */
    private suspend fun processEventLoop() {
        while (true) {
            try {
                delay(120)
                if (!pendingEvent) continue
                pendingEvent = false
                doProcessEvent()
            } catch (e: Exception) {
                Log.e(TAG, "processEventLoop error", e)
            }
        }
    }

    /**
     * 主动兜底轮询：不依赖无障碍事件流，每 600ms 主动检查一次屏幕。
     * 解决"事件流中断导致识别停摆、切前台才恢复"的问题。
     * 与 processEventLoop 同跑在单线程调度器上，串行执行不冲突（有冷却保护，不会重复点击）。
     */
    private suspend fun screenWatchdog() {
        while (true) {
            try {
                delay(600)
                if (!enabled) continue
                if (learnCallback != null) continue
                doProcessEvent()
            } catch (e: Exception) {
                Log.e(TAG, "screenWatchdog error", e)
            }
        }
    }

    /** 实际事件处理（在 IO 线程执行） */
    private fun doProcessEvent() {
        val pkg = currentRootPackage() ?: return
        if (pkg == packageName) return
        if (Matcher.isSystemUi(pkg)) return
        if (whitelistEnabled && !Matcher.matchesWhitelist(pkg, whitelistPkgs)) return

        val root = rootInActiveWindow ?: return
        val now = System.currentTimeMillis()
        if (now - lastClickTime < CLICK_COOLDOWN) return

        // 0) 条件规则优先（更具体："有X且Y→动作"）
        val condAllowed = !condDramaOnlyOn ||
            isDouyin(pkg) || Matcher.matchesWhitelist(pkg, whitelistPkgs)
        if (cachedCondRules.isNotEmpty() && condAllowed) {
            val cond = Matcher.matchCondRule(root, cachedCondRules, pkg)
            if (cond != null) {
                val rule = cond.rule
                val key = rule.id.toString()
                val nowC = System.currentTimeMillis()
                if (rule.delaySec <= 0) {
                    pendingCondRules.remove(key)
                    performCondAction(cond, pkg)
                    return
                }
                if (!pendingCondRules.containsKey(key)) {
                    pendingCondRules[key] = nowC
                    scheduleCondCheck(cond, pkg, rule.delaySec)
                }
                return
            } else {
                pendingCondRules.clear()
            }
        } else {
            pendingCondRules.clear()
        }

        val result = Matcher.match(root, cachedRules, pkg)
        if (result != null) {
            val posRect = android.graphics.Rect()
            try { result.node.getBoundsInScreen(posRect) } catch (_: Exception) {}
            val signature = pkg + "|" + result.matchedText + "|" + posRect.centerX() + "," + posRect.centerY()
            val nowT = System.currentTimeMillis()
            val dismissLike = Matcher.isDismissLike(result.matchedText) ||
                Matcher.isDismissLike(result.rule.text)
            val reallyClickable = try { result.node.isClickable } catch (_: Exception) { false }
            val isRepeat = dismissLike && reallyClickable &&
                signature == lastPopupSignature && (nowT - lastPopupTime) < REPEAT_WINDOW

            if (isRepeat) {
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
                lastPopupSignature = null
                lastClickTime = nowT
            } else {
                lastPopupSignature = signature
                lastPopupTime = nowT
                performClick(result, pkg)
            }
        }
    }

    /**
     * 延时检测：等 delaySec 秒后重新检查条件是否仍存在，仍存在才执行动作。
     */
    private fun scheduleCondCheck(cond: CondMatchResult, pkg: String, delaySec: Int) {
        val rule = cond.rule
        val key = rule.id.toString()
        scope.launch(nodeDispatcher) {
            delay(delaySec * 1000L)
            try {
                // 若等待期间条件已消失（被清理），放弃
                if (!pendingCondRules.containsKey(key)) return@launch
                // ★ 延时结束时前台必须仍是抖音系/白名单，否则放弃
                val curPkg = currentRootPackage()
                val stillAllowed = !condDramaOnlyOn || (curPkg != null &&
                    (isDouyin(curPkg) || Matcher.matchesWhitelist(curPkg, whitelistPkgs)))
                if (!stillAllowed) {
                    pendingCondRules.remove(key)
                    DramaDebug.add("条件规则延时结束但已切出抖音，放弃")
                    return@launch
                }
                val root = rootInActiveWindow
                if (root == null) {
                    pendingCondRules.remove(key)
                    return@launch
                }
                // 重新匹配该规则（用延时结束时的实际前台包名）
                val again = Matcher.matchCondRule(root, listOf(rule), curPkg)
                pendingCondRules.remove(key)
                if (again != null) {
                    DramaDebug.add("条件规则延时 ${delaySec} 秒后仍存在，执行动作")
                    mainHandler.post { performCondAction(again, pkg) }
                } else {
                    DramaDebug.add("条件规则延时 ${delaySec} 秒后已消失，放弃")
                }
            } catch (e: Exception) {
                pendingCondRules.remove(key)
                Log.e(TAG, "scheduleCondCheck error", e)
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
        runCatching { nodeDispatcher.close() }
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
