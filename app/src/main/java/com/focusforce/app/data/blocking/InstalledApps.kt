package com.focusforce.app.data.blocking

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.focusforce.app.data.usage.ExcludedPackages
import com.focusforce.app.domain.blocking.SafetyPackages
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Launchable apps the user may choose to block. Calls, emergency, Settings, installers, launchers and FocusForce are hidden. */
@Singleton
class InstalledApps @Inject constructor(
    @ApplicationContext private val context: Context,
    private val excluded: ExcludedPackages,
) {
    suspend fun blockable(): List<String> = withContext(Dispatchers.IO) {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val hidden = excluded.current() + SafetyPackages.neverBlock + SafetyPackages.settingsApps + SafetyPackages.installers
        context.packageManager.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it !in hidden }
    }
}
