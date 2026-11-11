package com.focusforce.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.focusforce.app.data.blocking.BlockCoordinator
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground-app detector and, only when you switch those features on, the detector for short-video sections and
 * blocked websites.
 *
 * What it reads: the package and window class of a window that just opened; and, only for an app that has a
 * short-video rule or a browser being checked, the view-id names of that window (never text) or the one address-bar
 * field of the browser. Screen content is processed in memory for one decision and dropped: nothing is logged, stored or sent.
 * It subscribes to screen-content events at all only while some rule is switched on.
 */
@AndroidEntryPoint
class FocusAccessibilityService : AccessibilityService() {

    @Inject lateinit var coordinator: BlockCoordinator

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private var pendingPackage: String? = null
    private val inspectRunnable = Runnable { pendingPackage?.let(::inspect) }

    override fun onServiceConnected() {
        super.onServiceConnected()
        coordinator.accessibilityConnected = true
        coordinator.backAction = { performGlobalAction(GLOBAL_ACTION_BACK) }
        scope.launch {
            coordinator.contentEventsWanted.collect { wanted ->
                val info = serviceInfo ?: return@collect
                info.eventTypes = if (wanted) {
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                } else {
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                }
                serviceInfo = info
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        val pkg = e.packageName?.toString()
        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                coordinator.onForeground(pkg, e.className?.toString())
                if (pkg != null) scheduleInspect(pkg)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> if (pkg != null) scheduleInspect(pkg)
        }
    }

    /** Coalesces bursts of events: look at the screen at most every 250 ms, and only for apps a rule cares about. */
    private fun scheduleInspect(pkg: String) {
        if (!coordinator.wantsInspection(pkg)) return
        pendingPackage = pkg
        handler.removeCallbacks(inspectRunnable)
        handler.postDelayed(inspectRunnable, INSPECT_DELAY_MS)
    }

    private fun inspect(pkg: String) {
        val root: AccessibilityNodeInfo = rootInActiveWindow ?: return
        try {
            if (root.packageName?.toString() != pkg) return
            val feed = coordinator.feedRule(pkg)
            val browser = coordinator.browserRule(pkg)
            val ids = if (feed != null) ScreenInspector.collectViewIds(root) else emptySet()
            val bar = if (browser != null) ScreenInspector.readAddressBar(root, browser.urlBarIds) else null
            coordinator.onScreen(pkg, ids, bar)
        } finally {
            ScreenInspector.release(root)
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        release()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        release()
        super.onDestroy()
    }

    private fun release() {
        coordinator.accessibilityConnected = false
        coordinator.backAction = null
        handler.removeCallbacks(inspectRunnable)
        scope.cancel()
    }

    private companion object {
        const val INSPECT_DELAY_MS = 250L
    }
}
