package com.focusforce.app.data.focus

import com.focusforce.app.R
import com.focusforce.app.data.usage.ExcludedPackages
import com.focusforce.app.data.usage.UsageStatsReader
import com.focusforce.app.domain.nudge.BreakReminder
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * "You have been on your phone for a while" reminders. Looks at when apps were in the foreground (never at
 * what was in them) and nudges once per unbroken stretch of use.
 */
@Singleton
class BreakNudger @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reader: UsageStatsReader,
    private val excluded: ExcludedPackages,
    private val prefs: FocusPrefs,
    private val notifier: Notifier,
) {
    suspend fun tick(nowMs: Long = System.currentTimeMillis()) = withContext(Dispatchers.Default) {
        val settings = prefs.nudge.first()
        if (!settings.breakRemindersEnabled) return@withContext
        val skip = excluded.current()
        val events = reader.read(nowMs - LOOKBACK_MS, nowMs).events.filter { it.packageName !in skip }
        val intervals = BreakReminder.intervals(events, nowMs)
        val streak = BreakReminder.streakMs(intervals, nowMs)
        val streakStart = intervals.lastOrNull()?.startMs ?: return@withContext
        if (BreakReminder.shouldRemind(streak, settings.breakAfterMin, streakStart, prefs.lastBreakReminder())) {
            notifier.alert(NotificationIds.BREAK, context.getString(R.string.notif_break_title), context.getString(R.string.notif_break_text, (streak / 60_000L).toInt()))
            prefs.setLastBreakReminder(nowMs)
        }
    }

    private companion object {
        const val LOOKBACK_MS = 6 * 60 * 60_000L
    }
}
