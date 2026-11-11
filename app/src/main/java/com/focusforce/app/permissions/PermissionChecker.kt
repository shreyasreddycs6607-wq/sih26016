package com.focusforce.app.permissions

import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.text.TextUtils
import androidx.core.app.NotificationManagerCompat
import com.focusforce.app.service.FocusAccessibilityService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Reads the live grant state of each [PermissionKind]. Cheap; safe to call on every ON_RESUME. */
@Singleton
class PermissionChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun isGranted(kind: PermissionKind): Boolean =
        if (!kind.isApplicable()) true else when (kind) {
            PermissionKind.USAGE_ACCESS -> usageAccessGranted()
            PermissionKind.OVERLAY -> Settings.canDrawOverlays(context)
            PermissionKind.ACCESSIBILITY -> accessibilityEnabled()
            PermissionKind.NOTIFICATIONS -> NotificationManagerCompat.from(context).areNotificationsEnabled()
            PermissionKind.BATTERY -> context.getSystemService(PowerManager::class.java)
                .isIgnoringBatteryOptimizations(context.packageName)
            PermissionKind.DND_ACCESS -> context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
            PermissionKind.EXACT_ALARMS -> Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        }

    fun snapshot(): Map<PermissionKind, Boolean> =
        PermissionKind.applicable().associateWith(::isGranted)

    private fun usageAccessGranted(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            // Some OEM builds report MODE_DEFAULT even when granted; fall back to the manifest permission.
            context.checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    private fun accessibilityEnabled(): Boolean {
        val resolver = context.contentResolver
        if (Settings.Secure.getInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 1) return false
        val enabled = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val ours = ComponentName(context, FocusAccessibilityService::class.java)
        val splitter = TextUtils.SimpleStringSplitter(':').apply { setString(enabled) }
        while (splitter.hasNext()) {
            if (ComponentName.unflattenFromString(splitter.next()) == ours) return true
        }
        return false
    }
}
