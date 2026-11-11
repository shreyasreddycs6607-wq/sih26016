package com.focusforce.app.domain.usage

import java.time.LocalDate

data class Comparison(val todayMs: Long, val yesterdayMs: Long?, val weeklyAvgMs: Long?) {
    /** Percent change vs yesterday; null when yesterday is unknown or zero. */
    val vsYesterdayPct: Int? get() = pct(todayMs, yesterdayMs)
    val vsWeeklyAvgPct: Int? get() = pct(todayMs, weeklyAvgMs)

    private fun pct(now: Long, base: Long?): Int? =
        if (base == null || base <= 0L) null else (((now - base) * 100.0) / base).let { Math.round(it).toInt() }
}

enum class MissionStatus { ON_TRACK, AT_RISK, FAILED }

/** The daily mission "Stay under your goal today", evaluated live from today's total. */
data class DailyMission(val totalMs: Long, val goalMs: Long, val progress: Float, val status: MissionStatus, val remainingMs: Long)

data class Villain(val packageName: String, val usageMs: Long, val targetMs: Long) {
    val progress: Float get() = if (targetMs <= 0) 1f else (usageMs.toFloat() / targetMs).coerceIn(0f, 1.5f)
    val status: MissionStatus
        get() = when {
            usageMs >= targetMs -> MissionStatus.FAILED
            usageMs >= targetMs * 8 / 10 -> MissionStatus.AT_RISK
            else -> MissionStatus.ON_TRACK
        }
}

object Insights {
    private const val MINUTE = 60_000L
    const val MIN_VILLAIN_MS = 5 * MINUTE
    const val MIN_TARGET_MS = 30 * MINUTE

    /** Weekly average = mean of the synced days among the 7 days before [today]. */
    fun comparison(summaries: List<DaySummary>, today: LocalDate): Comparison {
        val byDate = summaries.associateBy { it.date }
        val todayMs = byDate[today]?.totalMs ?: 0L
        val yesterday = byDate[today.minusDays(1)]?.totalMs
        val prior = (1L..7L).mapNotNull { byDate[today.minusDays(it)]?.totalMs }
        val avg = if (prior.isEmpty()) null else prior.sum() / prior.size
        return Comparison(todayMs, yesterday, avg)
    }

    fun mission(totalMs: Long, goalMs: Long): DailyMission {
        val goal = goalMs.coerceAtLeast(MINUTE)
        val progress = (totalMs.toFloat() / goal).coerceIn(0f, 1f)
        val status = when {
            totalMs >= goal -> MissionStatus.FAILED
            totalMs >= goal * 8 / 10 -> MissionStatus.AT_RISK
            else -> MissionStatus.ON_TRACK
        }
        return DailyMission(totalMs, goal, progress, status, (goal - totalMs).coerceAtLeast(0L))
    }

    /** Sums rows per package, most used first. */
    fun topApps(rows: List<DayAppUsage>, limit: Int = Int.MAX_VALUE): List<AppUsage> =
        rows.groupBy { it.packageName }
            .map { (pkg, list) -> AppUsage(pkg, list.sumOf { it.foregroundMs }, list.sumOf { it.launches }) }
            .sortedWith(compareByDescending<AppUsage> { it.foregroundMs }.thenBy { it.packageName })
            .take(limit)

    fun categorySplit(apps: List<AppUsage>, categoryOf: (String) -> AppCategory): Map<AppCategory, Long> {
        val out = AppCategory.entries.associateWith { 0L }.toMutableMap()
        for (a in apps) out[categoryOf(a.packageName)] = out.getValue(categoryOf(a.packageName)) + a.foregroundMs
        return out
    }

    /**
     * The week's villain: the most-used DISTRACTING app of the last 7 days. The target to beat is 80%
     * of that app's use in the 7 days before (a 20% cut), or, with no earlier data, 25% of the weekly
     * screen-time goal, never below 30 minutes.
     */
    fun villain(
        thisWeek: List<DayAppUsage>,
        previousWeek: List<DayAppUsage>,
        weeklyGoalMs: Long,
        categoryOf: (String) -> AppCategory,
    ): Villain? {
        val top = topApps(thisWeek).firstOrNull { categoryOf(it.packageName) == AppCategory.DISTRACTING && it.foregroundMs >= MIN_VILLAIN_MS }
            ?: return null
        val before = previousWeek.filter { it.packageName == top.packageName }.sumOf { it.foregroundMs }
        val target = if (before > 0) maxOf(before * 8 / 10, MIN_TARGET_MS) else maxOf(weeklyGoalMs / 4, MIN_TARGET_MS)
        return Villain(top.packageName, top.foregroundMs, target)
    }

    /** Hour (0..23) with the most use across [cells]; null when there is no data. */
    fun peakHour(cells: List<HourCell>): Int? =
        cells.groupBy { it.hour }.mapValues { (_, v) -> v.sumOf { it.foregroundMs } }
            .filterValues { it > 0 }.maxByOrNull { it.value }?.key
}
