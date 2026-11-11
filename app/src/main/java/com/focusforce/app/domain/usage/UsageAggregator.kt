package com.focusforce.app.domain.usage

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** [startMs, endMs) of a local calendar day; for today the end is clamped to "now". */
data class DayWindow(val startMs: Long, val endMs: Long) {
    companion object {
        fun of(date: LocalDate, zone: ZoneId, nowMs: Long): DayWindow {
            val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val nextStart = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            return DayWindow(start, minOf(nextStart, maxOf(nowMs, start)))
        }
    }
}

/**
 * Turns raw foreground/background events into per-app time, launches and an hour-of-day histogram.
 * Pure Kotlin so it is unit-tested without a device.
 *
 * Rules:
 * - Time is the sum of [RESUMED, PAUSED] intervals per activity; sessions still open at the window
 *   end are closed there.
 * - A PAUSED with no matching RESUMED, seen before any RESUMED in the window, means the app was
 *   already in the foreground when the window opened: it is credited from the window start.
 * - A launch is a RESUMED when the package had no open activity and was not just paused
 *   (within [RELAUNCH_GAP_MS]), so moving between two activities of one app is not a new launch.
 */
object UsageAggregator {
    const val RELAUNCH_GAP_MS = 2_000L

    private class Mutable(var ms: Long = 0, var launches: Int = 0)

    fun aggregate(
        events: List<UsageEventRecord>,
        window: DayWindow,
        zone: ZoneId,
        include: (String) -> Boolean = { true },
    ): DayUsage {
        val stats = LinkedHashMap<String, Mutable>()
        val hourly = LongArray(24)
        val openStart = HashMap<String, Long>()
        val openCount = HashMap<String, Int>()
        var lastPausePkg: String? = null
        var lastPauseTs = Long.MIN_VALUE
        var seenResume = false

        fun credit(pkg: String, from: Long, to: Long) {
            val a = maxOf(from, window.startMs)
            val b = minOf(to, window.endMs)
            if (b <= a) return
            stats.getOrPut(pkg) { Mutable() }.ms += b - a
            var cursor = a
            while (cursor < b) {
                val zdt = Instant.ofEpochMilli(cursor).atZone(zone)
                val nextHour = zdt.truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant().toEpochMilli()
                val segmentEnd = minOf(b, nextHour)
                hourly[zdt.hour] += segmentEnd - cursor
                cursor = segmentEnd
            }
        }

        val sorted = events
            .filter { it.timestampMs >= window.startMs && it.timestampMs < window.endMs && include(it.packageName) }
            .sortedBy { it.timestampMs }

        for (e in sorted) {
            val key = e.packageName + "/" + e.className
            when (e.type) {
                UsageEventType.RESUMED -> {
                    seenResume = true
                    val already = openStart[key]
                    if (already != null) {
                        credit(e.packageName, already, e.timestampMs)
                        openStart[key] = e.timestampMs
                    } else {
                        val count = openCount[e.packageName] ?: 0
                        val justPaused = lastPausePkg == e.packageName && e.timestampMs - lastPauseTs <= RELAUNCH_GAP_MS
                        if (count == 0 && !justPaused) stats.getOrPut(e.packageName) { Mutable() }.launches += 1
                        openStart[key] = e.timestampMs
                        openCount[e.packageName] = count + 1
                    }
                }
                UsageEventType.PAUSED -> {
                    val start = openStart.remove(key)
                    if (start != null) {
                        credit(e.packageName, start, e.timestampMs)
                        openCount[e.packageName] = (openCount[e.packageName] ?: 1) - 1
                    } else if (!seenResume) {
                        credit(e.packageName, window.startMs, e.timestampMs)
                    }
                    lastPausePkg = e.packageName
                    lastPauseTs = e.timestampMs
                }
            }
        }
        for ((key, start) in openStart) credit(key.substringBefore('/'), start, window.endMs)

        return DayUsage(
            perApp = stats.filterValues { it.ms > 0 || it.launches > 0 }.mapValues { AppDayStats(it.value.ms, it.value.launches) },
            hourlyMs = hourly.toList(),
        )
    }
}
