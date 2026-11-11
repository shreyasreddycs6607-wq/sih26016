package com.focusforce.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.focusforce.app.data.usage.UsageSyncWorker
import com.focusforce.app.domain.repo.BlockSettingsRepository
import com.focusforce.app.service.BlockerController
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltAndroidApp
class FocusForceApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var blockSettings: BlockSettingsRepository

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        UsageSyncWorker.schedule(this)
        // Process death recovery: whenever the process starts and blocking is on, bring the service back.
        CoroutineScope(Dispatchers.Default).launch {
            if (blockSettings.settings.first().enabled) BlockerController.ensureRunning(this@FocusForceApp)
        }
    }
}
