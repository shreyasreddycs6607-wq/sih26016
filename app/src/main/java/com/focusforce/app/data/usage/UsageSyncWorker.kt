package com.focusforce.app.data.usage

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.focusforce.app.domain.repo.UsageRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/** Periodic background sync so history persists even if the app is rarely opened. */
@HiltWorker
class UsageSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: UsageRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        repository.refresh() // PERMISSION_MISSING is a normal no-op, not a failure
        com.focusforce.app.widget.WidgetUpdater.updateAll(applicationContext)
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
    }

    companion object {
        private const val UNIQUE_NAME = "usage_sync"
        private const val MAX_ATTEMPTS = 3

        /**
         * Every 30 minutes (WorkManager may defer it under Doze or OEM battery limits). Opening the app
         * always syncs immediately, and Android keeps ~7 days of events, so a deferred run loses nothing.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UsageSyncWorker>(30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
