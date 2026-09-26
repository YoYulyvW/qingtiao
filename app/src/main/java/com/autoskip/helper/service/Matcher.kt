package com.autoskip.helper.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.autoskip.helper.data.RuleEntity

/** 匹配结果 */
data class MatchResult(
    val node: AccessibilityNodeInfo,
    val rule: RuleEntity,
    val matchedText: String
)

/**
 * 规则匹配引擎。
 * 遍历无障碍节点树，找出文本命中规则、且可点击（或父节点可点击）的节点。
 */
object Matcher {

    /** 默认内置规则：明确表示"跳过/拒绝"的文案（不含"关闭"，交由用户自定义） */
    val DEFAULT_TEXTS = listOf(
        "跳过", "下次再说", "不再提醒", "以后再说", "暂不",
        "我知道了", "知道了", "残忍拒绝", "稍后再说", "取消",
        "跳过广告", "点击跳过", "skip"
    )

    /** 关闭 / X 类按钮文案（严格模式下需要登录上下文才点） */
    val CLOSE_LIKE_TEXTS = listOf(
        "关闭", "close", "×", "✕", "✖", "x"
    )

    /** 登录 / 广告类关键词 —— 出现这些文字时才允许点 X / 关闭 */
    val LOGIN_KEYWORDS = listOf(
        "登录", "一键登录", "本机号码", "获取验证码", "验证码",
        "手机号", "注册", "用户协议", "隐私政策", "同意并",
        "广告", "跳过广告", "开通会员", "立即购买"
    )

    /** contentDescription 中含这些词，视为"关闭图标"（兜底） */
    val CLOSE_DESCRIPTIONS = listOf(
        "关闭", "close", "取消", "返回", "dismiss", "back", "cancel"
    )

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

    /** 判断某文本是否属于"关闭/X"类 */
    fun isCloseLike(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val t = text.trim()
        return CLOSE_LIKE_TEXTS.any { it.equals(t, ignoreCase = true) }
    }

    /** 判断整棵节点树中是否存在登录/广告类关键词 */
    fun hasLoginContext(candidates: List<AccessibilityNodeInfo>): Boolean {
        for (node in candidates) {
            val text = node.text?.toString() ?: node.contentDescription?.toString()
            if (text.isNullOrBlank()) continue
            if (LOGIN_KEYWORDS.any { text.contains(it, ignoreCase = true) }) return true
        }
        return false
    }

    /**
     * 匹配。
     * @param strictClose 严格模式：关闭/X 类按钮仅在登录/广告上下文出现时才点击
     */
    fun match(
        root: AccessibilityNodeInfo?,
        rules: List<RuleEntity>,
        packageName: String?,
        strictClose: Boolean = true
    ): MatchResult? {
        if (root == null) return null
        val candidates = ArrayList<AccessibilityNodeInfo>()
        collect(root, candidates)
        if (candidates.isEmpty()) return null

        // 严格模式下预先计算一次"是否存在登录上下文"
        val loginCtx = if (strictClose) hasLoginContext(candidates) else true

        // 第一轮：按用户/内置规则做文本匹配
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

                // 严格模式：关闭/X 类按钮必须要有登录/广告上下文才点
                if (strictClose && isCloseLike(text) && !loginCtx) continue

                val clickable = findClickable(node)
                if (clickable != null) {
                    return MatchResult(clickable, rule, text)
                }
            }
        }

        // 第二轮（兜底）：严格模式 + 有登录上下文 → 尝试点纯图标 X
        if (strictClose && loginCtx) {
            return matchIconClose(root, candidates)
        }
        return null
    }

    /**
     * 兜底匹配：在登录/广告弹窗中，找到"关闭图标"并点击。
     * 不限制位置（右上角、中心右侧均可），依据：无文字 + 尺寸较小 + 接近方形。
     */
    private fun matchIconClose(
        root: AccessibilityNodeInfo,
        candidates: List<AccessibilityNodeInfo>
    ): MatchResult? {
        // 1. 优先：contentDescription 明确含"关闭/close/返回"等
        for (node in candidates) {
            val desc = node.contentDescription?.toString() ?: continue
            if (CLOSE_DESCRIPTIONS.any { desc.contains(it, ignoreCase = true) }) {
                val clickable = findClickable(node)
                if (clickable != null && clickable.isEnabled) {
                    return buildIconResult(clickable, "[关闭图标]")
                }
            }
        }

        // 2. 其次：无文字、无描述的小方形可点击图标
        val screen = Rect()
        root.getBoundsInScreen(screen)
        val maxW = (screen.width() * 0.20).toInt()
        val maxH = (screen.height() * 0.12).toInt()
        val minSize = 24

        var best: AccessibilityNodeInfo? = null
        var bestScore = Double.MAX_VALUE
        val rect = Rect()

        for (node in candidates) {
            if (!node.isClickable || !node.isEnabled) continue
            val t = node.text?.toString()
            val d = node.contentDescription?.toString()
            if (!t.isNullOrBlank() || !d.isNullOrBlank()) continue
            // 图标通常没有子节点，排除掉容器
            if (node.childCount > 1) continue

            node.getBoundsInScreen(rect)
            val w = rect.width()
            val h = rect.height()
            if (w < minSize || h < minSize) continue
            if (w > maxW || h > maxH) continue

            val ratio = if (w > h) w.toDouble() / h else h.toDouble() / w
            if (ratio > 2.0) continue // 太扁长的不是图标

            // 越接近方形、面积越小，越像关闭图标
            val score = ratio * 10000 + w + h
            if (score < bestScore) {
                bestScore = score
                best = node
            }
        }

        val target = best ?: return null
        return buildIconResult(target, "[图标关闭]")
    }

    /** 用虚拟规则包装图标点击结果（id = -1，不参与规则命中统计） */
    private fun buildIconResult(node: AccessibilityNodeInfo, label: String): MatchResult {
        val rule = RuleEntity(id = -1L, name = label, text = label)
        return MatchResult(node, rule, label)
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
