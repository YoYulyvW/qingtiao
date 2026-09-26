package com.autoskip.helper.fenshen

import android.view.accessibility.AccessibilityNodeInfo

/**
 * 节点查找与点击辅助，对应 Auto.js 的 text()/id()/desc()/click()。
 */
object NodeHelper {

    /** 递归收集全部节点 */
    fun collectAll(root: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> {
        if (root == null) return emptyList()
        val out = ArrayList<AccessibilityNodeInfo>()
        fun rec(n: AccessibilityNodeInfo) {
            out.add(n)
            for (i in 0 until n.childCount) {
                val c = n.getChild(i) ?: continue
                rec(c)
            }
        }
        rec(root)
        return out
    }

    private fun nodeText(n: AccessibilityNodeInfo): String =
        n.text?.toString() ?: n.contentDescription?.toString() ?: ""

    /** 精确文本 */
    fun findByText(root: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? =
        collectAll(root).firstOrNull { nodeText(it) == text }

    /** 文本包含 */
    fun findByTextContains(root: AccessibilityNodeInfo?, sub: String): AccessibilityNodeInfo? =
        collectAll(root).firstOrNull { nodeText(it).contains(sub) }

    /** 文本正则 */
    fun findByTextMatches(root: AccessibilityNodeInfo?, regex: Regex): AccessibilityNodeInfo? =
        collectAll(root).firstOrNull { regex.matches(nodeText(it)) }

    /** 文本以某串开头 */
    fun findByTextStartsWith(root: AccessibilityNodeInfo?, prefix: String): AccessibilityNodeInfo? =
        collectAll(root).firstOrNull { nodeText(it).startsWith(prefix) }

    /** viewId 包含（对应 idContains） */
    fun findByIdContains(root: AccessibilityNodeInfo?, sub: String): AccessibilityNodeInfo? =
        collectAll(root).firstOrNull { (it.viewIdResourceName ?: "").contains(sub) }

    /** viewId 精确（对应 id()） */
    fun findById(root: AccessibilityNodeInfo?, id: String): AccessibilityNodeInfo? =
        collectAll(root).firstOrNull { (it.viewIdResourceName ?: "").endsWith("/$id") }

    /** 描述包含 */
    fun findByDesc(root: AccessibilityNodeInfo?, desc: String): AccessibilityNodeInfo? =
        collectAll(root).firstOrNull { (it.contentDescription?.toString() ?: "").contains(desc) }

    /** 全部匹配文本（对应 text().find()） */
    fun findAllByText(root: AccessibilityNodeInfo?, text: String): List<AccessibilityNodeInfo> =
        collectAll(root).filter { nodeText(it) == text }

    /** 判断节点是否存在（对应 .exists()） */
    fun existsByText(root: AccessibilityNodeInfo?, text: String): Boolean =
        findByText(root, text) != null

    /** 向上找到可点击祖先并点击 */
    fun clickNode(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        var cur: AccessibilityNodeInfo? = node
        var depth = 0
        while (cur != null && depth < 8) {
            if (cur.isClickable && cur.isEnabled) {
                return cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            cur = cur.parent
            depth++
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    /** 设置 EditText 文本 */
    fun setText(node: AccessibilityNodeInfo?, text: String): Boolean {
        if (node == null) return false
        val args = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    /** 节点中心坐标 */
    fun center(node: AccessibilityNodeInfo?): Pair<Float, Float>? {
        if (node == null) return null
        val r = android.graphics.Rect()
        node.getBoundsInScreen(r)
        return Pair(r.centerX().toFloat(), r.centerY().toFloat())
    }
}
