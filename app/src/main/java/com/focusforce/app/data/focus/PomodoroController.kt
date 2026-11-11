package com.focusforce.app.data.focus

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.focusforce.app.R
import com.focusforce.app.data.blocking.BlockingManager
import com.focusforce.app.data.blocking.Outcome
import com.focusforce.app.domain.blocking.BlockingRules
import com.focusforce.app.domain.pomodoro.Pomodoro
import com.focusforce.app.domain.pomodoro.PomodoroEvent
import com.focusforce.app.domain.pomodoro.PomodoroPhase
import com.focusforce.app.domain.pomodoro.PomodoroRun
import com.focusforce.app.domain.repo.BlockSettingsRepository
import com.focusforce.app.domain.repo.FocusRepository
import com.focusforce.app.domain.repo.PomodoroSessionRecord
import com.focusforce.app.service.BlockerController
import com.focusforce.app.service.PomodoroAlarmReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Runs the Pomodoro set: persists the run (so it survives process death), arms one alarm for the end of the
 * current phase, records history, posts phase notifications and tells the UI about completions.
 * Blocking itself needs no timer: the block decision reads the persisted run.
 */
@Singleton
class PomodoroController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: FocusPrefs,
    private val repository: FocusRepository,
    private val blocking: BlockingManager,
    private val settings: BlockSettingsRepository,
    private val notifier: Notifier,
    private val environment: FocusEnvironment,
) {
    private val mutex = Mutex()
    private val _events = MutableSharedFlow<PomodoroEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<PomodoroEvent> = _events.asSharedFlow()

    /** Starts a set with the saved lengths. Switches blocking on, because a Pomodoro with nothing blocked is just a timer. */
    suspend fun start() = mutex.withLock {
        val now = System.currentTimeMillis()
        val run = Pomodoro.start(prefs.pomodoroConfig.first(), now)
        blocking.setEnabled(true)
        prefs.setPomodoroRun(run)
        BlockerController.ensureRunning(context)
        arm(run)
        environment.refresh()
    }

    /** Ends the set early. Not allowed during a strict lock. */
    suspend fun stop(): Outcome = mutex.withLock {
        val now = System.currentTimeMillis()
        if (!BlockingRules.canLoosen(settings.settings.first(), now)) return@withLock Outcome.DENIED_STRICT
        val run = prefs.pomodoroRun.first() ?: return@withLock Outcome.OK
        Pomodoro.stop(run, now)?.let { event ->
            if (event is PomodoroEvent.FocusStopped) {
                repository.addSession(PomodoroSessionRecord(0, event.startMs, event.endMs, ((event.endMs - event.startMs) / 60_000L).toInt(), completed = false))
            }
        }
        prefs.setPomodoroRun(null)
        cancel()
        environment.refresh()
        Outcome.OK
    }

    /** Moves the run forward if a phase ended. Safe to call from anywhere, any number of times. */
    suspend fun sync() = mutex.withLock {
        val current = prefs.pomodoroRun.first() ?: return@withLock cancel()
        val now = System.currentTimeMillis()
        val (next, events) = Pomodoro.advance(current, now)
        if (events.isEmpty()) return@withLock
        for (event in events) {
            when (event) {
                is PomodoroEvent.FocusCompleted -> {
                    repository.addSession(PomodoroSessionRecord(0, event.startMs, event.endMs, event.minutes, completed = true))
                    notifier.alert(NotificationIds.POMODORO, context.getString(R.string.notif_pomo_focus_done), context.getString(R.string.notif_pomo_focus_done_text))
                }
                is PomodoroEvent.BreakStarted -> Unit
                PomodoroEvent.SetCompleted ->
                    notifier.alert(NotificationIds.POMODORO, context.getString(R.string.notif_pomo_set_done), context.getString(R.string.notif_pomo_set_done_text))
                is PomodoroEvent.FocusStopped -> Unit
            }
            _events.tryEmit(event)
        }
        val breakStart = events.filterIsInstance<PomodoroEvent.BreakStarted>().lastOrNull()
        if (breakStart != null && next != null && next.phase != PomodoroPhase.FOCUS) {
            notifier.alert(NotificationIds.POMODORO, context.getString(R.string.notif_pomo_break_title), context.getString(if (breakStart.long) R.string.notif_pomo_long_break_text else R.string.notif_pomo_break_text))
        }
        prefs.setPomodoroRun(next)
        environment.refresh()
        if (next != null) arm(next) else cancel()
    }

    /** Alarms do not survive a reboot: re-arm for the saved run (call at boot and app start). */
    suspend fun rearm() = mutex.withLock {
        val current = prefs.pomodoroRun.first() ?: return@withLock cancel()
        arm(current)
    }

    private fun pending(): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST_CODE, Intent(context, PomodoroAlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun arm(run: PomodoroRun) = AlarmScheduler.setAt(context, pending(), run.phaseEndMs + 300L)
    private fun cancel() = AlarmScheduler.cancel(context, pending())

    private companion object {
        const val REQUEST_CODE = 1
    }
}
