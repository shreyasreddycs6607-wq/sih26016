package com.focusforce.app.permissions

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/** Builds and fires the deep link to the right Settings page for each permission. */
object PermissionLauncher {

    /** Candidate intents, most specific first. The first one the device can resolve wins. */
    fun intents(context: Context, kind: PermissionKind): List<Intent> {
        val pkg = Uri.fromParts("package", context.packageName, null)
        val list = when (kind) {
            PermissionKind.USAGE_ACCESS -> buildList {
                // Per-app page exists on Android 10+, other versions open the global list.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    add(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, pkg))
                }
                add(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            PermissionKind.OVERLAY -> listOf(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg))
            // Android has no public per-service deep link; the user picks FocusForce from the list.
            PermissionKind.ACCESSIBILITY -> listOf(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            PermissionKind.NOTIFICATIONS -> listOf(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
            PermissionKind.BATTERY -> listOf(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg),
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            )
            PermissionKind.EXACT_ALARMS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                listOf(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg))
            } else {
                emptyList()
            }
        }
        return list + Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
    }

    /** Opens the Settings page for [kind]; falls back down the list, never crashes. */
    fun open(context: Context, kind: PermissionKind) {
        for (intent in intents(context, kind)) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
                // try the next candidate
            } catch (_: SecurityException) {
                // some OEM builds reject a specific intent; try the next candidate
            }
        }
    }

    fun openAppDetails(context: Context) {
        val pkg = Uri.fromParts("package", context.packageName, null)
        try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: ActivityNotFoundException) {
            // nothing sensible left to open
        }
    }
}
