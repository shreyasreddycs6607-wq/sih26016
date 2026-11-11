package com.focusforce.app.service

import android.view.accessibility.AccessibilityNodeInfo

/**
 * The only code that touches the screen's node tree. It returns two things and nothing else: the SET of view-id names
 * in a window (no text, no content descriptions, no positions) and the text of one named address-bar field.
 */
object ScreenInspector {
    private const val MAX_NODES = 600

    fun collectViewIds(root: AccessibilityNodeInfo): Set<String> {
        val ids = HashSet<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited++
            node.viewIdResourceName?.let(ids::add)
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
            if (node !== root) release(node)
        }
        queue.forEach { if (it !== root) release(it) }
        return ids
    }

    /** Text of the address-bar field, capped at 500 characters. The caller extracts the site name and drops the rest. */
    fun readAddressBar(root: AccessibilityNodeInfo, viewIds: List<String>): String? {
        for (id in viewIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id).orEmpty()
            val text = nodes.firstOrNull()?.text?.toString()
            nodes.forEach(::release)
            if (!text.isNullOrBlank()) return text.take(500)
        }
        return null
    }

    @Suppress("DEPRECATION") // recycle() is a no-op from API 33 but still required on API 26-32
    fun release(node: AccessibilityNodeInfo) {
        node.recycle()
    }
}
