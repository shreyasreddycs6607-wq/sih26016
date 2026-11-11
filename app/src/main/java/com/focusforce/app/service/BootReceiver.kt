package com.focusforce.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focusforce.app.data.focus.FocusEnvironment
import com.focusforce.app.data.focus.PomodoroController
import com.focusforce.app.domain.repo.BlockSettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Restarts the blocker after a reboot or an app update if blocking was on. */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var settings: BlockSettingsRepository
    @Inject lateinit var pomodoro: PomodoroController
    @Inject lateinit var environment: FocusEnvironment

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (settings.settings.first().enabled) BlockerController.ensureRunning(context.applicationContext)
                // Alarms do not survive a reboot: re-arm the Pomodoro and schedule alarms.
                pomodoro.rearm()
                environment.refresh()
            } finally {
                pending.finish()
            }
        }
    }
}
