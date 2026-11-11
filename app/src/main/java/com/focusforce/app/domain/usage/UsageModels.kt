package com.focusforce.app.domain.usage

import java.time.LocalDate

enum class UsageEventType { RESUMED, PAUSED }

/** One foreground/background transition reported by UsageStatsManager, reduced to what we need. */
data class UsageEventRecord(
    val timestampMs: Long,
    val packageName: String,
    val className: String,
    val type: UsageEventType,
)

data class AppDayStats(val foregroundMs: Long, val launches: Int)

/** Aggregated result for one day window. [hourlyMs] always has 24 entries (local hour of day). */
data class DayUsage(
    val perApp: Map<String, AppDayStats>,
    val hourlyMs: List<Long>,
) {
    val totalMs: Long get() = perApp.values.sumOf { it.foregroundMs }
}

/** One app's usage on one day, as stored. */
data class DayAppUsage(
    val date: LocalDate,
    val packageName: String,
    val foregroundMs: Long,
    val launches: Int,
)

/** An app's usage summed over a period. */
data class AppUsage(val packageName: String, val foregroundMs: Long, val launches: Int)

/** A synced day. [unlocks] is null where the platform cannot report it (Android 8). */
data class DaySummary(val date: LocalDate, val totalMs: Long, val unlocks: Int?)

data class HourCell(val date: LocalDate, val hour: Int, val foregroundMs: Long)

enum class AppCategory { PRODUCTIVE, DISTRACTING, NEUTRAL }

enum class SyncResult { SYNCED, PERMISSION_MISSING }
