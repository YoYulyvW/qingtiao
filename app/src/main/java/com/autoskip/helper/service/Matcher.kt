package com.autoskip.helper.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.autoskip.helper.data.CondAction
import com.autoskip.helper.data.CondRuleEntity
import com.autoskip.helper.data.RuleEntity

/** 匹配结果 */
data class MatchResult(
    val node: AccessibilityNodeInfo,
    val rule: RuleEntity,
    val matchedText: String
)

/** 条件规则匹配结果 */
data class CondMatchResult(
    val actionType: String,
    val node: AccessibilityNodeInfo?,   // CLICK_TEXT / CLICK_ICON 时的目标
    val rule: CondRuleEntity,
    val matchedText: String
)

/**
 * 规则匹配引擎。
 * 遍历无障碍节点树，找出文本命中规则、且可点击（或父节点可点击）的节点。
 */
object Matcher {

    /** 默认内置规则：全部精确匹配（用户可在「规则」页修改） */
    val DEFAULT_TEXTS = listOf(
        "点击免费看全集", "刷新", "跳过", "不再提醒", "清理缓存",
        "同意", "以后再说", "下次再说"
    )

    /**
     * 关闭 / 拒绝 / 跳过 类按钮文案。
     * 特点：点完后弹窗**本应消失**。只有这类按钮才做"重复弹窗→返回键"兜底。
     * "进入类"按钮（如 点击免费看全集）点完页面会正常切换，不做重复检测。
     */
    val DISMISS_LIKE_TEXTS = listOf(
        "跳过", "关闭", "取消", "下次再说", "不再提醒", "以后再说",
        "稍后再说", "残忍拒绝", "暂不", "我知道了", "知道了",
        "close", "skip", "×", "✕", "✖"
    )

    /**
     * 判断文本是否属于"关闭/拒绝/跳过"类（点完弹窗应消失）。
     * 严格精确匹配（trim 后完全相等），避免页面出现"跳过/关闭"等普通文字被误判。
     */
    fun isDismissLike(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val t = text.trim()
        return DISMISS_LIKE_TEXTS.any { t.equals(it, ignoreCase = true) }
    }

    /** contentDescription 中含这些词，视为"关闭图标"（兜底） */
    val CLOSE_DESCRIPTIONS = listOf(
        "关闭", "close", "取消", "返回", "dismiss", "back", "cancel"
    )

    /**
     * 系统 UI 包名黑名单：永不在这些应用上点击。
     * 覆盖多任务中心、桌面、设置等，避免误点任务卡片/图标。
     */
    val SYSTEM_UI_BLACKLIST = listOf(
        "com.android.systemui",
        "com.android.launcher",
        "com.android.launcher3",
        "com.sec.android.app.launcher",
        "com.samsung.android.app.launcher",
        "com.huawei.android.launcher",
        "com.miui.home",
        "com.oppo.launcher",
        "com.vivo.launcher",
        "com.google.android.apps.nexuslauncher",
        "com.android.settings",
        "com.samsung.android.app.settings",
        "com.samsung.android.lool",
        "com.android.quicksearchbox"
    )

    /** 验证码 / 滑块关键词：检测到则跳过整个弹窗（防误触 + 避免影响正常验证） */
    val CAPTCHA_KEYWORDS = listOf(
        "拖动滑块", "完成拼图", "滑动验证", "人机验证", "安全验证",
        "拖动左侧滑块", "向右滑动", "请按住滑块", "拖动下方滑块",
        "按住滑块拖动", "拖动到最右边"
    )

    /** 是否为系统 UI（黑名单包名） */
    fun isSystemUi(pkg: String?): Boolean {
        if (pkg.isNullOrBlank()) return false
        return SYSTEM_UI_BLACKLIST.any { pkg == it || pkg.startsWith(it + ".") }
    }

    /** 节点树里是否存在验证码/滑块关键词 */
    fun hasCaptcha(candidates: List<AccessibilityNodeInfo>): Boolean {
        for (node in candidates) {
            val text = node.text?.toString() ?: node.contentDescription?.toString()
            if (text.isNullOrBlank()) continue
            if (CAPTCHA_KEYWORDS.any { text.contains(it, ignoreCase = true) }) return true
        }
        return false
    }

    /**
     * 白名单匹配：支持以 * 结尾的前缀通配。
     * 例：com.qihoo.magic.* 可匹配 com.qihoo.magic.dl1WZ3Fm..._110 等所有分身
     */
    fun matchesWhitelist(pkg: String, whitelist: Set<String>): Boolean {
        return whitelist.any { rule ->
            if (rule.endsWith("*")) pkg.startsWith(rule.dropLast(1))
            else pkg == rule
        }
    }

    /**
     * 匹配普通规则（按文本/描述匹配）。
     * 注：「关闭/X 类按钮需登录上下文」的严格逻辑已改为「条件规则」，不再硬编码。
     */
    fun match(
        root: AccessibilityNodeInfo?,
        rules: List<RuleEntity>,
        packageName: String?
    ): MatchResult? {
        if (root == null) return null
        // 系统 UI 直接跳过（多任务中心、桌面、设置等）
        if (isSystemUi(packageName)) return null

        val candidates = ArrayList<AccessibilityNodeInfo>()
        collect(root, candidates)
        if (candidates.isEmpty()) return null

        // 检测到验证码/滑块弹窗 → 整个弹窗不点任何东西
        if (hasCaptcha(candidates)) return null

        // 按用户/内置规则做文本匹配
        for (rule in rules) {
            if (!rule.enabled) continue
            if (!rule.packageName.isNullOrBlank() && packageName != null &&
                rule.packageName != packageName) continue

            for (node in candidates) {
                val text = node.text?.toString() ?: node.contentDescription?.toString()
                if (text.isNullOrBlank()) continue
                val hit = if (rule.exact) text.equals(rule.text, ignoreCase = true)
                          else text.contains(rule.text, ignoreCase = true)
                if (!hit) continue

                val clickable = findClickable(node)
                if (clickable != null) {
                    return MatchResult(clickable, rule, text)
                }
            }
        }

        // 第二轮（兜底）已移除：
        // 原"登录上下文→点纯图标X"逻辑改为「条件规则」（有登录→点图标X），
        // 用户可在条件规则页查看、修改、启停。
        return null
    }

    /**
     * 匹配条件规则。
     * 规则：屏幕上"有" hasText，且（若填了 andText）还包含 andText → 执行 action。
     */
    fun matchCondRule(
        root: AccessibilityNodeInfo?,
        condRules: List<CondRuleEntity>,
        packageName: String?
    ): CondMatchResult? {
        if (root == null) return null
        if (isSystemUi(packageName)) return null

        val candidates = ArrayList<AccessibilityNodeInfo>()
        collect(root, candidates)
        if (candidates.isEmpty()) return null

        // 收集全屏所有文字
        val allTexts = candidates.mapNotNull { n ->
            (n.text?.toString() ?: n.contentDescription?.toString())?.trim()?.takeIf { it.isNotBlank() }
        }

        for (rule in condRules) {
            if (!rule.enabled) continue
            if (!rule.packageName.isNullOrBlank() && packageName != null &&
                rule.packageName != packageName) continue

            // 【有】hasText：支持多个关键词（用 | 分隔），任一命中即可
            if (rule.hasText.isBlank()) continue
            val hasKeys = rule.hasText.split("|", "｜")
                .map { it.trim() }.filter { it.isNotBlank() }
            val anyHasHit = hasKeys.isEmpty() ||
                hasKeys.any { key -> allTexts.any { it.contains(key, ignoreCase = true) } }
            if (!anyHasHit) continue

            // 【且】andText（可选）：支持多个关键词，用 | 分隔，全部命中才算
            if (!rule.andText.isNullOrBlank()) {
                val andKeys = rule.andText.split("|", "｜")
                    .map { it.trim() }.filter { it.isNotBlank() }
                val allAndHit = andKeys.all { key ->
                    allTexts.any { it.contains(key, ignoreCase = true) }
                }
                if (!allAndHit) continue
            }

            // 条件满足 → 执行动作
            when (rule.actionType) {
                CondAction.BACK -> {
                    return CondMatchResult(CondAction.BACK, null, rule, rule.hasText)
                }
                CondAction.CLICK_TEXT -> {
                    val target = rule.actionText?.trim()
                    if (target.isNullOrBlank()) continue
                    val node = candidates.firstOrNull {
                        val t = it.text?.toString() ?: it.contentDescription?.toString() ?: ""
                        t.trim().contains(target, ignoreCase = true)
                    } ?: continue
                    val clickable = findClickable(node)
                    if (clickable != null && clickable.isEnabled) {
                        return CondMatchResult(CondAction.CLICK_TEXT, clickable, rule, target)
                    }
                }
                CondAction.CLICK_ICON -> {
                    val icon = findIconClose(candidates, root)
                    if (icon != null) {
                        return CondMatchResult(CondAction.CLICK_ICON, icon, rule, "[图标关闭]")
                    }
                }
            }
        }
        return null
    }

    /** 提取"找图标关闭"逻辑，供条件规则复用 */
    private fun findIconClose(candidates: List<AccessibilityNodeInfo>, root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // 优先 contentDescription
        for (node in candidates) {
            val desc = node.contentDescription?.toString() ?: continue
            if (CLOSE_DESCRIPTIONS.any { desc.contains(it, ignoreCase = true) }) {
                val clickable = findClickable(node)
                if (clickable != null && clickable.isEnabled) return clickable
            }
        }
        // 其次小图标
        val screen = Rect()
        root.getBoundsInScreen(screen)
        val screenW = screen.width()
        val screenH = screen.height()
        val maxW = (screenW * 0.25).toInt()
        val maxH = (screenH * 0.15).toInt()
        val minSize = 20
        var best: AccessibilityNodeInfo? = null
        var bestScore = Double.MAX_VALUE
        val rect = Rect()
        for (node in candidates) {
            val t = node.text?.toString()
            val d = node.contentDescription?.toString()
            if (!t.isNullOrBlank() || !d.isNullOrBlank()) continue
            if (node.childCount > 2) continue
            node.getBoundsInScreen(rect)
            val w = rect.width()
            val h = rect.height()
            if (w < minSize || h < minSize) continue
            if (w > maxW || h > maxH) continue
            val ratio = if (w > h) w.toDouble() / h else h.toDouble() / w
            if (ratio > 2.5) continue
            val clickTarget = findClickable(node) ?: continue
            if (!clickTarget.isEnabled) continue
            val cx = rect.centerX()
            val cy = rect.centerY()
            val distToEdge = minOf(cx, screenW - cx)
            val score = (cy.toDouble() / screenH) * 1000 +
                        (distToEdge.toDouble() / screenW) * 500 +
                        ratio * 100 + w + h
            if (score < bestScore) {
                bestScore = score
                best = clickTarget
            }
        }
        return best
    }

    /** 向上查找可点击的祖先节点（含自身） */
    fun findClickable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var cur: AccessibilityNodeInfo? = node
        var depth = 0
        while (cur != null && depth < 6) {
            if (cur.isClickable && cur.isEnabled) return cur
            cur = cur.parent
            depth++
        }
        return node
    }

    private fun collect(node: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>) {
        out.add(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collect(child, out)
        }
    }

    /** 描述节点，用于学习模式展示 */
    fun describeNode(node: AccessibilityNodeInfo): String {
        val text = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
        val id = node.viewIdResourceName ?: ""
        return if (id.isNotBlank()) text + " (" + id + ")" else text
    }
}
