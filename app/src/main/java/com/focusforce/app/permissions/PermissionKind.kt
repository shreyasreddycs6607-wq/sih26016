package com.focusforce.app.permissions

import android.os.Build

/**
 * Everything FocusForce asks the user for. Order = order of the guided flow: the two the blocker
 * cannot work without come first.
 *
 * @property minSdk first Android version where the permission exists; below it the capability is
 * implicitly available and the step is skipped.
 * @property essential true when the core blocker cannot work without it.
 */
enum class PermissionKind(val minSdk: Int, val essential: Boolean) {
    USAGE_ACCESS(Build.VERSION_CODES.LOLLIPOP, true),
    OVERLAY(Build.VERSION_CODES.M, true),
    ACCESSIBILITY(Build.VERSION_CODES.LOLLIPOP, true),
    NOTIFICATIONS(Build.VERSION_CODES.TIRAMISU, false),
    BATTERY(Build.VERSION_CODES.M, false),
    EXACT_ALARMS(Build.VERSION_CODES.S, false);

    fun isApplicable(sdk: Int = Build.VERSION.SDK_INT): Boolean = sdk >= minSdk

    companion object {
        fun applicable(sdk: Int = Build.VERSION.SDK_INT): List<PermissionKind> =
            entries.filter { it.isApplicable(sdk) }
    }
}
