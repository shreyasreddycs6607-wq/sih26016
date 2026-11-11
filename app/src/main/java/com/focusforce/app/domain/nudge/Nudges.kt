package com.focusforce.app.domain.nudge

import com.focusforce.app.domain.usage.UsageEventRecord
import com.focusforce.app.domain.usage.UsageEventType

data class NudgeSettings(
    val breakRemindersEnabled: Boolean = true,
    val breakAfterMin: Int = 45,
) {
    fun coerced() = copy(breakAfterMin = breakAfterMin.coerceIn(15, 180))
}

data class Interval(val startMs: Long, val endMs: Long)

object BreakReminder {
    const val MIN = 60_000L
    /** A pause shorter than this does not count as a real break. */
    const val BREAK_GAP_MS = 5 * MIN

    /** Merged "phone in use" intervals (any app in the foreground), joining ones closer than [gapMs]. */
    fun intervals(events: List<UsageEventRecord>, nowMs: Long, gapMs: Long = BREAK_GAP_MS): List<Interval> {
        val raw = mutableListOf<Interval>()
        val open = HashMap<String, Long>()
        var openedAt: Long? = null
        for (e in events.sortedBy { it.timestampMs }) {
            val key = e.packageName + "/" + e.className
            when (e.type) {
                UsageEventType.RESUMED -> {
                    if (open.isEmpty()) openedAt = e.timestampMs
                    open.putIfAbsent(key, e.timestampMs)
                }
                UsageEventType.PAUSED -> if (open.remove(key) != null && open.isEmpty()) {
                    openedAt?.let { raw += Interval(it, e.timestampMs) }
                    openedAt = null
                }
            }
        }
        openedAt?.let { raw += Interval(it, nowMs) }
        val merged = mutableListOf<Interval>()
        for (i in raw.sortedBy { it.startMs }) {
            val last = merged.lastOrNull()
            if (last != null && i.startMs - last.endMs < gapMs) merged[merged.lastIndex] = Interval(last.startMs, maxOf(last.endMs, i.endMs))
            else merged += i
        }
        return merged
    }

    /** Length of the current unbroken stretch of use, 0 if the last use ended more than [gapMs] ago. */
    fun streakMs(intervals: List<Interval>, nowMs: Long, gapMs: Long = BREAK_GAP_MS): Long {
        val last = intervals.lastOrNull() ?: return 0L
        return if (nowMs - last.endMs >= gapMs) 0L else last.endMs - last.startMs
    }

    /** Remind once per stretch: only when no reminder was already sent during this same stretch. */
    fun shouldRemind(streakMs: Long, afterMin: Int, streakStartMs: Long, lastReminderMs: Long): Boolean =
        streakMs >= afterMin * MIN && lastReminderMs < streakStartMs
}
