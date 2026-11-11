package com.focusforce.app.domain.game

import com.focusforce.app.domain.usage.DaySummary
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.IsoFields
import kotlin.math.sqrt

/** A finished focus block (completed Pomodoro), reduced to what scoring needs. */
data class FocusMinutes(val date: LocalDate, val minutes: Int)

/**
 * XP is DERIVED from history (usage summaries + completed focus blocks), never stored or editable, so it cannot drift or be
 * typed in. Rules: 1 XP per focused minute; 50 XP for each finished day under the daily goal, plus 5 XP per day already in the
 * streak (up to +35). Only days that have ended count, so today can never be "lost" while it is still in progress.
 */
object Xp {
    const val PER_FOCUS_MINUTE = 1
    const val DAY_UNDER_GOAL = 50
    const val STREAK_BONUS_PER_DAY = 5
    const val MAX_STREAK_BONUS_DAYS = 7

    fun isUnderGoal(totalMs: Long, goalMs: Long): Boolean = totalMs < goalMs

    data class Result(val totalXp: Int, val currentStreak: Int, val longestStreak: Int, val todayOnTrack: Boolean)

    /**
     * @param days one entry per SYNCED day; a day with no entry is unknown and breaks a streak (we cannot prove it was under goal).
     */
    fun compute(days: List<DaySummary>, focus: List<FocusMinutes>, goalMs: Long, today: LocalDate): Result {
        val byDate = days.associateBy { it.date }
        val focusByDate = focus.groupBy { it.date }.mapValues { (_, v) -> v.sumOf { it.minutes } }
        val first = (byDate.keys + focusByDate.keys).minOrNull() ?: return Result(0, 0, 0, byDate[today]?.let { isUnderGoal(it.totalMs, goalMs) } ?: false)

        var xp = 0
        var streak = 0
        var longest = 0
        var date = first
        while (date.isBefore(today)) {
            xp += (focusByDate[date] ?: 0) * PER_FOCUS_MINUTE
            val summary = byDate[date]
            if (summary != null && isUnderGoal(summary.totalMs, goalMs)) {
                streak += 1
                longest = maxOf(longest, streak)
                xp += DAY_UNDER_GOAL + minOf(streak - 1, MAX_STREAK_BONUS_DAYS) * STREAK_BONUS_PER_DAY
            } else {
                streak = 0
            }
            date = date.plusDays(1)
        }
        // today: focus minutes already count (they were earned); the day bonus waits until the day is over
        xp += (focusByDate[today] ?: 0) * PER_FOCUS_MINUTE
        val todayOk = byDate[today]?.let { isUnderGoal(it.totalMs, goalMs) } ?: false
        return Result(xp, streak, longest, todayOk)
    }
}

/** Ranks: original names, increasing XP thresholds. */
enum class Rank(val minXp: Int) {
    RECRUIT(0), CADET(300), GUARDIAN(1_200), SENTINEL(3_500), CHAMPION(8_000), LEGEND(20_000);

    val next: Rank? get() = entries.getOrNull(ordinal + 1)

    companion object {
        fun of(xp: Int): Rank = entries.last { xp >= it.minXp }
    }
}

object Levels {
    /** Level L starts at 25 * (L-1)^2 XP: quick early levels, slower later. */
    fun of(xp: Int): Int = 1 + sqrt(xp.coerceAtLeast(0) / 25.0).toInt()

    fun startXp(level: Int): Int = 25 * (level - 1) * (level - 1)

    /** 0f..1f progress inside the current level. */
    fun progress(xp: Int): Float {
        val level = of(xp)
        val start = startXp(level)
        val end = startXp(level + 1)
        return ((xp - start).toFloat() / (end - start)).coerceIn(0f, 1f)
    }

    /** 0f..1f progress inside the current rank; 1f at the top rank. */
    fun rankProgress(xp: Int): Float {
        val rank = Rank.of(xp)
        val next = rank.next ?: return 1f
        return ((xp - rank.minXp).toFloat() / (next.minXp - rank.minXp)).coerceIn(0f, 1f)
    }
}

/** ISO weeks (Monday start), written like "2026-W46", used to key weekly leaderboards. */
object WeekKey {
    fun of(date: LocalDate): String =
        "%d-W%02d".format(date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))

    fun weekStart(date: LocalDate): LocalDate = date.with(DayOfWeek.MONDAY)

    /** XP earned within the ISO week containing [today] = total XP now minus total XP at the end of last Sunday. */
    fun weeklyXp(days: List<DaySummary>, focus: List<FocusMinutes>, goalMs: Long, today: LocalDate): Int {
        val monday = weekStart(today)
        val nowTotal = Xp.compute(days, focus, goalMs, today).totalXp
        val before = Xp.compute(days.filter { it.date < monday }, focus.filter { it.date < monday }, goalMs, monday).totalXp
        return (nowTotal - before).coerceAtLeast(0)
    }
}
