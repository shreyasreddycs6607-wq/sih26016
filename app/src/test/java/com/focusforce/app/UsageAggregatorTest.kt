package com.focusforce.app

import com.focusforce.app.domain.usage.DayWindow
import com.focusforce.app.domain.usage.UsageAggregator
import com.focusforce.app.domain.usage.UsageEventRecord
import com.focusforce.app.domain.usage.UsageEventType.PAUSED
import com.focusforce.app.domain.usage.UsageEventType.RESUMED
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class UsageAggregatorTest {
    private val zone = ZoneId.of("UTC")
    private val date = LocalDate.of(2026, 3, 10)
    private val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
    private val min = 60_000L
    private val hour = 60 * min
    private val fullDay = DayWindow.of(date, zone, nowMs = dayStart + 48 * hour)

    private fun ev(atMs: Long, pkg: String, type: com.focusforce.app.domain.usage.UsageEventType, cls: String = "Main") =
        UsageEventRecord(dayStart + atMs, pkg, cls, type)

    @Test fun sumsForegroundIntervalsAndCountsLaunches() {
        val r = UsageAggregator.aggregate(
            listOf(
                ev(9 * hour, "a", RESUMED), ev(9 * hour + 10 * min, "a", PAUSED),
                ev(10 * hour, "a", RESUMED), ev(10 * hour + 5 * min, "a", PAUSED),
            ),
            fullDay, zone,
        )
        assertEquals(15 * min, r.perApp.getValue("a").foregroundMs)
        assertEquals(2, r.perApp.getValue("a").launches)
        assertEquals(10 * min, r.hourlyMs[9])
        assertEquals(5 * min, r.hourlyMs[10])
    }

    @Test fun switchingActivitiesInOneAppIsOneLaunch() {
        val r = UsageAggregator.aggregate(
            listOf(
                ev(hour, "a", RESUMED, "One"),
                ev(hour + 5 * min, "a", PAUSED, "One"), ev(hour + 5 * min + 300, "a", RESUMED, "Two"),
                ev(hour + 9 * min, "a", PAUSED, "Two"),
            ),
            fullDay, zone,
        )
        assertEquals(1, r.perApp.getValue("a").launches)
        // 5 min in the first activity + (9 min - 5 min - 300 ms) in the second
        assertEquals(9 * min - 300, r.perApp.getValue("a").foregroundMs)
    }

    @Test fun sessionSplitsAcrossHourBoundary() {
        val r = UsageAggregator.aggregate(
            listOf(ev(9 * hour + 50 * min, "a", RESUMED), ev(10 * hour + 20 * min, "a", PAUSED)),
            fullDay, zone,
        )
        assertEquals(10 * min, r.hourlyMs[9])
        assertEquals(20 * min, r.hourlyMs[10])
        assertEquals(30 * min, r.totalMs)
    }

    @Test fun openSessionIsClosedAtWindowEnd() {
        val now = DayWindow.of(date, zone, nowMs = dayStart + 12 * hour)
        val r = UsageAggregator.aggregate(listOf(ev(11 * hour + 30 * min, "a", RESUMED)), now, zone)
        assertEquals(30 * min, r.perApp.getValue("a").foregroundMs)
    }

    @Test fun orphanPauseIsCreditedFromWindowStart() {
        val r = UsageAggregator.aggregate(listOf(ev(20 * min, "a", PAUSED)), fullDay, zone)
        assertEquals(20 * min, r.perApp.getValue("a").foregroundMs)
        assertEquals(20 * min, r.hourlyMs[0])
    }

    @Test fun excludedPackagesAreIgnored() {
        val r = UsageAggregator.aggregate(
            listOf(ev(hour, "launcher", RESUMED), ev(hour + min, "launcher", PAUSED)),
            fullDay, zone, include = { it != "launcher" },
        )
        assertNull(r.perApp["launcher"])
        assertEquals(0L, r.totalMs)
    }

    @Test fun reopeningAfterAGapIsANewLaunch() {
        val r = UsageAggregator.aggregate(
            listOf(
                ev(hour, "a", RESUMED), ev(hour + min, "a", PAUSED),
                ev(hour + min + 10_000, "a", RESUMED), ev(hour + 2 * min, "a", PAUSED),
            ),
            fullDay, zone,
        )
        assertEquals(2, r.perApp.getValue("a").launches)
    }

    @Test fun todayWindowEndsAtNow() {
        val w = DayWindow.of(date, zone, nowMs = dayStart + 5 * hour)
        assertEquals(dayStart, w.startMs)
        assertEquals(dayStart + 5 * hour, w.endMs)
        assertFalse(w.endMs > dayStart + 24 * hour)
    }
}
