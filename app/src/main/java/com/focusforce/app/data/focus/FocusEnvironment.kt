package com.focusforce.app.data.focus

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.focusforce.app.R
import com.focusforce.app.domain.pomodoro.Pomodoro
import com.focusforce.app.domain.repo.FocusRepository
import com.focusforce.app.domain.schedule.ScheduleMatcher
import com.focusforce.app.permissions.PermissionChecker
import com.focusforce.app.permissions.PermissionKind
import com.focusforce.app.service.ScheduleAlarmReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Applies the "outside world" side of focus profiles and Pomodoro: Do Not Disturb on/off (only if the user
 * granted Do Not Disturb access, otherwise it quietly does nothing), "profile started/ended" notifications,
 * and arming the alarm for the next schedule boundary. Call [refresh] whenever any of those inputs change.
 */
@Singleton
class FocusEnvironment @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: FocusRepository,
    private val prefs: FocusPrefs,
    private val permissions: PermissionChecker,
    private val notifier: Notifier,
) {
    private val mutex = Mutex()

    suspend fun refresh() = mutex.withLock {
        val nowMs = System.currentTimeMillis()
        val now = LocalDateTime.now()
        val schedules = repository.observeSchedules().first()
        val active = ScheduleMatcher.active(schedules, now)
        val run = prefs.pomodoroRun.first()?.let { Pomodoro.advance(it, nowMs).first }
        val wantSilence = active.any { it.silenceNotifications } ||
            (Pomodoro.isBlocking(run) && prefs.silenceDuringPomodoro.first())
        applyDnd(wantSilence)
        announceChanges(schedules.associateBy { it.id }, active.map { it.id }.toSet())
        armNextBoundary(schedules, now)
    }

    private suspend fun applyDnd(want: Boolean) {
        if (!permissions.isGranted(PermissionKind.DND_ACCESS)) return
        val nm = context.getSystemService(NotificationManager::class.java)
        val record = prefs.dnd()
        if (want && !record.applied) {
            val previous = nm.currentInterruptionFilter
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            prefs.setDnd(DndRecord(true, previous, NotificationManager.INTERRUPTION_FILTER_PRIORITY))
        } else if (!want && record.applied) {
            // Only restore if nobody changed it since: never override the user's own choice.
            if (nm.currentInterruptionFilter == record.setFilter) {
                nm.setInterruptionFilter(
                    if (record.previousFilter == NotificationManager.INTERRUPTION_FILTER_UNKNOWN) NotificationManager.INTERRUPTION_FILTER_ALL
                    else record.previousFilter,
                )
            }
            prefs.setDnd(DndRecord())
        }
    }

    private suspend fun announceChanges(byId: Map<Long, com.focusforce.app.domain.schedule.FocusSchedule>, activeNow: Set<Long>) {
        val before = prefs.activeScheduleIds()
        for (id in activeNow - before) {
            byId[id]?.let { notifier.alert(NotificationIds.SCHEDULE_BASE + (id % 500).toInt(), context.getString(R.string.notif_schedule_started, it.name), context.getString(R.string.notif_schedule_started_text, it.packages.size)) }
        }
        for (id in before - activeNow) {
            byId[id]?.let { notifier.alert(NotificationIds.SCHEDULE_BASE + (id % 500).toInt(), context.getString(R.string.notif_schedule_ended, it.name), context.getString(R.string.notif_schedule_ended_text)) }
        }
        if (before != activeNow) prefs.setActiveScheduleIds(activeNow)
    }

    private fun armNextBoundary(schedules: List<com.focusforce.app.domain.schedule.FocusSchedule>, now: LocalDateTime) {
        val pending = PendingIntent.getBroadcast(
            context, REQUEST_CODE, Intent(context, ScheduleAlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val next = ScheduleMatcher.nextBoundary(schedules, now)
        if (next == null) AlarmScheduler.cancel(context, pending)
        else AlarmScheduler.setAt(context, pending, next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() + 500L)
    }

    private companion object {
        const val REQUEST_CODE = 2
    }
}
