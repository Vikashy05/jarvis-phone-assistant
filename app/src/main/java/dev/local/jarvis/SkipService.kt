package dev.local.jarvis

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

internal const val PREFERENCES = "jarvis_controls"
internal const val HELPER_ALLOWED = "helper_allowed"

class SkipService : AccessibilityService() {
    private val preferences by lazy { getSharedPreferences(PREFERENCES, MODE_PRIVATE) }
    private val skipIds = listOf("skip_ad_button", "skip_ad_button_text", "ad_skip_button")
        .map { "$YOUTUBE:id/$it" }
    private val labels = setOf("Skip Ad", "Skip Ads", "Skip")
    private var nextAttempt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() != YOUTUBE || !preferences.getBoolean(HELPER_ALLOWED, false)) return
        val now = SystemClock.elapsedRealtime()
        if (now < nextAttempt) return
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != YOUTUBE || root.windowId != event.windowId) return
        for (id in skipIds) {
            for (node in root.findAccessibilityNodeInfosByViewId(id)) {
                if (!isUsable(node)) continue
                val button = when {
                    node.isClickable && (hasLabel(node) || hasLabelledChild(node)) -> node
                    hasLabel(node) -> node.parent?.takeIf {
                        isUsable(it) && it.isClickable && it.viewIdResourceName in skipIds
                    }
                    else -> null
                } ?: continue
                nextAttempt = now + 1500
                button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return
            }
        }
    }

    private fun isUsable(node: AccessibilityNodeInfo) =
        node.packageName?.toString() == YOUTUBE && node.isVisibleToUser && node.isEnabled

    private fun hasLabel(node: AccessibilityNodeInfo) =
        listOf(node.text, node.contentDescription).any { value ->
            labels.any { it.equals(value?.toString()?.trim(), ignoreCase = true) }
        }

    private fun hasLabelledChild(node: AccessibilityNodeInfo) = (0 until node.childCount).any { index ->
        node.getChild(index)?.let { isUsable(it) && hasLabel(it) } == true
    }

    override fun onInterrupt() {}
}
