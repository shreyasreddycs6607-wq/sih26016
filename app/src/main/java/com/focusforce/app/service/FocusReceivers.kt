package com.focusforce.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focusforce.app.data.focus.FocusEnvironment
import com.focusforce.app.data.focus.PomodoroController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Fires at the end of a Pomodoro phase. */
@AndroidEntryPoint
class PomodoroAlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var controller: PomodoroController

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try { controller.sync() } finally { pending.finish() }
        }
    }
}

/** Fires when a focus schedule starts or ends: Do Not Disturb, notifications and the next alarm. */
@AndroidEntryPoint
class ScheduleAlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var environment: FocusEnvironment

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try { environment.refresh() } finally { pending.finish() }
        }
    }
}
