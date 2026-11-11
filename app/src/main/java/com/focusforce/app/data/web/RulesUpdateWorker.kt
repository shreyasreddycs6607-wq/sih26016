package com.focusforce.app.data.web

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/** Once a day, on any connection, fetch newer detection rules. Scheduled only if the build has a rules URL. */
@HiltWorker
class RulesUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val rules: RulesRepository,
    private val prefs: WebPrefs,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!prefs.current().autoUpdateRules) return Result.success()
        return when (rules.fetchRemote()) {
            RulesUpdate.NetworkError -> if (runAttemptCount < 3) Result.retry() else Result.success()
            else -> Result.success()
        }
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RulesUpdateWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("rules_update", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
