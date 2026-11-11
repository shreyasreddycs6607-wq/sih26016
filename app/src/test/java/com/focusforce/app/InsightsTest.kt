package com.focusforce.app

import com.focusforce.app.domain.usage.AppCategory
import com.focusforce.app.domain.usage.CategoryDefaults
import com.focusforce.app.domain.usage.DayAppUsage
import com.focusforce.app.domain.usage.DaySummary
import com.focusforce.app.domain.usage.HourCell
import com.focusforce.app.domain.usage.Insights
import com.focusforce.app.domain.usage.MissionStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InsightsTest {
    private val min = 60_000L
    private val today = LocalDate.of(2026, 3, 10)

    @Test fun comparisonUsesYesterdayAndWeeklyAverageOfSyncedDays() {
        val s = listOf(
            DaySummary(today, 120 * min, 10),
            DaySummary(today.minusDays(1), 100 * min, 8),
            DaySummary(today.minusDays(2), 200 * min, 8),
            DaySummary(today.minusDays(9), 999 * min, 8), // outside the 7-day window
        )
        val c = Insights.comparison(s, today)
        assertEquals(120 * min, c.todayMs)
        assertEquals(100 * min, c.yesterdayMs)
        assertEquals(150 * min, c.weeklyAvgMs)
        assertEquals(20, c.vsYesterdayPct)
        assertEquals(-20, c.vsWeeklyAvgPct)
    }

    @Test fun comparisonHandlesMissingHistory() {
        val c = Insights.comparison(listOf(DaySummary(today, 30 * min, null)), today)
        assertNull(c.yesterdayMs)
        assertNull(c.weeklyAvgMs)
        assertNull(c.vsYesterdayPct)
    }

    @Test fun missionStatusThresholds() {
        assertEquals(MissionStatus.ON_TRACK, Insights.mission(60 * min, 180 * min).status)
        assertEquals(MissionStatus.AT_RISK, Insights.mission(150 * min, 180 * min).status)
        assertEquals(MissionStatus.FAILED, Insights.mission(180 * min, 180 * min).status)
        assertEquals(30 * min, Insights.mission(150 * min, 180 * min).remainingMs)
    }

    private fun row(d: Int, pkg: String, mins: Long) = DayAppUsage(today.minusDays(d.toLong()), pkg, mins * min, 1)

    @Test fun topAppsAggregateAcrossDays() {
        val top = Insights.topApps(listOf(row(0, "a", 10), row(1, "a", 20), row(0, "b", 25)))
        assertEquals(listOf("a", "b"), top.map { it.packageName })
        assertEquals(30 * min, top[0].foregroundMs)
    }

    @Test fun villainIsTopDistractingAppWithReductionTarget() {
        val cat = { p: String -> if (p == "tok") AppCategory.DISTRACTING else AppCategory.PRODUCTIVE }
        val week = listOf(row(0, "doc", 300), row(1, "tok", 100), row(2, "tok", 50))
        val prev = listOf(row(8, "tok", 250))
        val v = Insights.villain(week, prev, weeklyGoalMs = 1_000 * min, categoryOf = cat)!!
        assertEquals("tok", v.packageName)
        assertEquals(150 * min, v.usageMs)
        assertEquals(200 * min, v.targetMs) // 80% of 250
        assertEquals(MissionStatus.ON_TRACK, v.status) // 150 of 200 min = 75%
        assertEquals(MissionStatus.AT_RISK, v.copy(usageMs = 170 * min).status)
        assertEquals(MissionStatus.FAILED, v.copy(usageMs = 200 * min).status)
    }

    @Test fun villainFallsBackToGoalShareAndHasFloor() {
        val cat = { _: String -> AppCategory.DISTRACTING }
        val v = Insights.villain(listOf(row(0, "x", 10)), emptyList(), weeklyGoalMs = 60 * min, categoryOf = cat)!!
        assertEquals(30 * min, v.targetMs) // 25% of 60 = 15 -> floor 30
    }

    @Test fun noVillainWhenNothingDistractingOrTooLittle() {
        assertNull(Insights.villain(listOf(row(0, "x", 100)), emptyList(), 100 * min) { AppCategory.PRODUCTIVE })
        assertNull(Insights.villain(listOf(row(0, "x", 2)), emptyList(), 100 * min) { AppCategory.DISTRACTING })
    }

    @Test fun categorySplitSumsByCategory() {
        val apps = Insights.topApps(listOf(row(0, "a", 10), row(0, "b", 20), row(0, "c", 5)))
        val split = Insights.categorySplit(apps) { when (it) { "a" -> AppCategory.PRODUCTIVE; "b" -> AppCategory.DISTRACTING; else -> AppCategory.NEUTRAL } }
        assertEquals(10 * min, split[AppCategory.PRODUCTIVE])
        assertEquals(20 * min, split[AppCategory.DISTRACTING])
        assertEquals(5 * min, split[AppCategory.NEUTRAL])
    }

    @Test fun peakHourAcrossDays() {
        val cells = listOf(HourCell(today, 21, 10 * min), HourCell(today.minusDays(1), 21, 20 * min), HourCell(today, 9, 25 * min))
        assertEquals(21, Insights.peakHour(cells))
        assertNull(Insights.peakHour(emptyList()))
    }

    @Test fun categoryDefaultsAndOverrides() {
        assertEquals(AppCategory.DISTRACTING, CategoryDefaults.classify("com.instagram.android", -1))
        assertEquals(AppCategory.DISTRACTING, CategoryDefaults.classify("some.game", CategoryDefaults.ANDROID_GAME))
        assertEquals(AppCategory.PRODUCTIVE, CategoryDefaults.classify("some.app", CategoryDefaults.ANDROID_PRODUCTIVITY))
        assertEquals(AppCategory.NEUTRAL, CategoryDefaults.classify("some.app", -1))
        val overrides = mapOf("com.instagram.android" to AppCategory.NEUTRAL)
        assertEquals(AppCategory.NEUTRAL, CategoryDefaults.resolve("com.instagram.android", -1, overrides))
    }
}
