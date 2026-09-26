package com.autoskip.helper.service

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

    /** 默认内置规则：常见弹窗按钮文案 */
    val DEFAULT_TEXTS = listOf(
        "跳过", "关闭", "下次再说", "不再提醒", "以后再说", "暂不",
        "我知道了", "知道了", "残忍拒绝", "稍后再说", "取消",
        "跳过广告", "点击跳过", "close", "skip"
    )

    fun match(
        root: AccessibilityNodeInfo?,
        rules: List<RuleEntity>,
        packageName: String?
    ): MatchResult? {
        if (root == null) return null
        val candidates = ArrayList<AccessibilityNodeInfo>()
        collect(root, candidates)
        if (candidates.isEmpty()) return null

        for (rule in rules) {
            if (!rule.enabled) continue
            if (!rule.packageName.isNullOrBlank() && packageName != null &&
                rule.packageName != packageName) continue

            for (node in candidates) {
                val text = node.text?.toString() ?: node.contentDescription?.toString()
                if (text.isNullOrBlank()) continue
                val hit = if (rule.exact) text.equals(rule.text, ignoreCase = true)
                          else text.contains(rule.text, ignoreCase = true)
                if (hit) {
                    val clickable = findClickable(node)
                    if (clickable != null) {
                        return MatchResult(clickable, rule, text)
                    }
                }
            }
        }
        return null
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
