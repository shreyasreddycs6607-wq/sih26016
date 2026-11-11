package com.focusforce.app.data.usage

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Packages that must not count as "screen time": FocusForce itself, the system UI and launchers. */
@Singleton
class ExcludedPackages @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun current(): Set<String> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val launchers = context.packageManager
            .queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName }
        return (launchers + context.packageName + "com.android.systemui" + "android").toSet()
    }
}
