package com.focusforce.app.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Registered so the user can enable it from Settings during onboarding and so the permission
 * screen can report its live status.
 *
 * Phase 1: intentionally inert. It does not read, log, store or transmit anything. Foreground
 * app detection (package name only) arrives with the blocking engine in Phase 3.
 */
class FocusAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
}
