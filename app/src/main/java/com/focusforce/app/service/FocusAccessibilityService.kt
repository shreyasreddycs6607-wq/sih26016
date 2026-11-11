package com.focusforce.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.focusforce.app.data.blocking.BlockCoordinator
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Primary foreground-app detector. It declares only window-state events and never requests window
 * content, so the only things it can see are the package name and window class of a window that just
 * opened. Nothing is logged, stored or sent; the pair is handed straight to [BlockCoordinator].
 */
@AndroidEntryPoint
class FocusAccessibilityService : AccessibilityService() {

    @Inject lateinit var coordinator: BlockCoordinator

    override fun onServiceConnected() {
        super.onServiceConnected()
        coordinator.accessibilityConnected = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        coordinator.onForeground(event.packageName?.toString(), event.className?.toString())
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        coordinator.accessibilityConnected = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        coordinator.accessibilityConnected = false
        super.onDestroy()
    }
}
