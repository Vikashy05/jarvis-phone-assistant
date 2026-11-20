package dev.local.jarvis

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

internal data class ScreenControl(
    val node: AccessibilityNodeInfo, val label: String, val id: String?, val bounds: Rect,
    val click: Boolean, val edit: Boolean, val scroll: Boolean, val checked: Boolean?
) {
    fun sameTarget(other: ScreenControl) =
        node == other.node && label == other.label && id == other.id && bounds == other.bounds &&
            click == other.click && edit == other.edit && scroll == other.scroll && checked == other.checked

    fun summary(index: Int) = listOfNotNull(
        index.toString(), label, "tap".takeIf { click }, "field".takeIf { edit },
        "list".takeIf { scroll }, checked?.let { "checked=$it" }
    ).joinToString(" ")
}

internal data class AgentScreen(val pkg: String, val window: Int, val controls: List<ScreenControl>) {
    companion object {
        fun read(root: AccessibilityNodeInfo, ownPackage: String): AgentScreen {
            val pkg = root.packageName?.toString().orEmpty()
            require(pkg.isNotBlank() && !pkg.contains("permissioncontroller") && !pkg.contains("packageinstaller") &&
                pkg !in setOf("com.android.systemui", "com.miui.securitycenter")) { "Hand control back to the user on this system screen." }
            if (pkg == ownPackage) return AgentScreen(pkg, root.windowId, emptyList())
            val controls = mutableListOf<ScreenControl>()
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            var visited = 0
            while (queue.isNotEmpty() && visited++ < 250) {
                val node = queue.removeFirst()
                if (node.packageName?.toString() != pkg || !node.isVisibleToUser) continue
                require(!node.isPassword) { "Password screen. Continue manually." }
                for (index in 0 until minOf(node.childCount, 80, (250 - visited - queue.size).coerceAtLeast(0))) {
                    node.getChild(index)?.let(queue::addLast)
                }
                if (!node.isEnabled) continue
                val label = sequenceOf(node.contentDescription, node.text, node.hintText,
                    node.viewIdResourceName?.substringAfterLast('/'), node.className?.toString()?.substringAfterLast('.'))
                    .firstOrNull { !it.isNullOrBlank() }?.toString()?.replace(Regex("""[\r\n\t]+"""), " ")?.take(50) ?: continue
                val bounds = Rect().also(node::getBoundsInScreen)
                if (bounds.isEmpty) continue
                controls.add(ScreenControl(node, label, node.viewIdResourceName, bounds, node.isClickable,
                    node.isEditable, node.isScrollable, if (node.isCheckable) node.isChecked else null))
            }
            return AgentScreen(pkg, root.windowId, controls.sortedByDescending { it.click || it.edit || it.scroll }.take(24))
        }
    }
}
