package com.focusforce.app.ui.permissions

import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import com.focusforce.app.R
import com.focusforce.app.permissions.Oem
import com.focusforce.app.permissions.PermissionKind
import com.focusforce.app.ui.components.FocusIcon

data class PermissionCopy(
    @StringRes val title: Int,
    @StringRes val why: Int,
    @StringRes val never: Int,
    @StringRes val action: Int,
    val icon: FocusIcon,
)

val PermissionKind.copy: PermissionCopy
    get() = when (this) {
        PermissionKind.USAGE_ACCESS -> PermissionCopy(R.string.perm_usage_title, R.string.perm_usage_why, R.string.perm_usage_never, R.string.perm_usage_action, FocusIcon.Clock)
        PermissionKind.OVERLAY -> PermissionCopy(R.string.perm_overlay_title, R.string.perm_overlay_why, R.string.perm_overlay_never, R.string.perm_overlay_action, FocusIcon.Layers)
        PermissionKind.ACCESSIBILITY -> PermissionCopy(R.string.perm_accessibility_title, R.string.perm_accessibility_why, R.string.perm_accessibility_never, R.string.perm_accessibility_action, FocusIcon.Eye)
        PermissionKind.NOTIFICATIONS -> PermissionCopy(R.string.perm_notifications_title, R.string.perm_notifications_why, R.string.perm_notifications_never, R.string.perm_notifications_action, FocusIcon.Bell)
        PermissionKind.BATTERY -> PermissionCopy(R.string.perm_battery_title, R.string.perm_battery_why, R.string.perm_battery_never, R.string.perm_battery_action, FocusIcon.Battery)
        PermissionKind.DND_ACCESS -> PermissionCopy(R.string.perm_dnd_title, R.string.perm_dnd_why, R.string.perm_dnd_never, R.string.perm_dnd_action, FocusIcon.Bell)
        PermissionKind.EXACT_ALARMS -> PermissionCopy(R.string.perm_alarms_title, R.string.perm_alarms_why, R.string.perm_alarms_never, R.string.perm_alarms_action, FocusIcon.Alarm)
    }

val Oem.labelRes: Int @StringRes get() = when (this) {
    Oem.XIAOMI -> R.string.oem_xiaomi
    Oem.OPPO -> R.string.oem_oppo
    Oem.VIVO -> R.string.oem_vivo
    Oem.SAMSUNG -> R.string.oem_samsung
    Oem.REALME -> R.string.oem_realme
}

val Oem.tipsRes: Int @ArrayRes get() = when (this) {
    Oem.XIAOMI -> R.array.oem_tips_xiaomi
    Oem.OPPO -> R.array.oem_tips_oppo
    Oem.VIVO -> R.array.oem_tips_vivo
    Oem.SAMSUNG -> R.array.oem_tips_samsung
    Oem.REALME -> R.array.oem_tips_realme
}
