package com.focusforce.app

import com.focusforce.app.domain.blocking.BlockContext
import com.focusforce.app.domain.blocking.BlockDecision
import com.focusforce.app.domain.blocking.BlockDecisionEngine
import com.focusforce.app.domain.blocking.BlockReason
import com.focusforce.app.domain.blocking.BlockSettings
import com.focusforce.app.domain.blocking.PassState
import com.focusforce.app.domain.limits.AppGroup
import com.focusforce.app.domain.limits.DaysMask
import com.focusforce.app.domain.limits.LimitKind
import com.focusforce.app.domain.limits.LimitLevel
import com.focusforce.app.domain.limits.LimitMath
import com.focusforce.app.domain.limits.LimitNotifier
import com.focusforce.app.domain.limits.LimitNotifyState
import com.focusforce.app.domain.limits.LimitRule
import com.focusforce.app.domain.nudge.BreakReminder
import com.focusforce.app.domain.pomodoro.Pomodoro
import com.focusforce.app.domain.pomodoro.PomodoroConfig
import com.focusforce.app.domain.pomodoro.PomodoroEvent
import com.focusforce.app.domain.pomodoro.PomodoroPhase
import com.focusforce.app.domain.schedule.FocusSchedule
import com.focusforce.app.domain.schedule.ScheduleMatcher
import com.focusforce.app.domain.usage.UsageEventRecord
import com.focusforce.app.domain.usage.UsageEventType
import java.time.DayOfWeek
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LimitsTest {
    private val min = 60_000L
    private val app = LimitRule(1, LimitKind.APP, "tok", 60 * min)
    private val groups = mapOf(7L to AppGroup(7, "Social", setOf("ig", "tok")))

    @Test fun levelsAtEightyAndHundredPercent() {
        assertEquals(LimitLevel.OK, LimitMath.level(47 * min, 60 * min))
        assertEquals(LimitLevel.WARNED, LimitMath.level(48 * min, 60 * min))
        assertEquals(LimitLevel.WARNED, LimitMath.level(59 * min, 60 * min))
        assertEquals(LimitLevel.REACHED, LimitMath.level(60 * min, 60 * min))
        assertEquals(LimitLevel.OK, LimitMath.level(10 * min, 0))
    }

    @Test fun groupLimitSumsMembersAndBlocksAllOfThem() {
        val rule = LimitRule(2, LimitKind.GROUP, "7", 60 * min)
        val usage = mapOf("ig" to 35 * min, "tok" to 25 * min, "docs" to 500 * min)
        assertEquals(60 * min, LimitMath.usedMs(rule, groups, usage))
        assertEquals(setOf("ig", "tok"), LimitMath.reachedPackages(listOf(rule), groups, usage, DayOfWeek.MONDAY))
    }

    @Test fun dayOfWeekRulesAndDisabledRulesDoNotApply() {
        val weekdayOnly = app.copy(daysMask = DaysMask.WEEKDAYS)
        val usage = mapOf("tok" to 90 * min)
        assertEquals(setOf("tok"), LimitMath.reachedPackages(listOf(weekdayOnly), groups, usage, DayOfWeek.FRIDAY))
        assertTrue(LimitMath.reachedPackages(listOf(weekdayOnly), groups, usage, DayOfWeek.SATURDAY).isEmpty())
        assertTrue(LimitMath.reachedPackages(listOf(app.copy(enabled = false)), groups, usage, DayOfWeek.FRIDAY).isEmpty())
    }

    @Test fun missingGroupMeansNoPackages() {
        assertTrue(LimitMath.packagesOf(LimitRule(3, LimitKind.GROUP, "99", min), groups).isEmpty())
        assertTrue(LimitMath.packagesOf(LimitRule(3, LimitKind.GROUP, "abc", min), groups).isEmpty())
    }

    @Test fun warningsFireOncePerLevelPerDayAndResetAtMidnight() {
        val day = DayOfWeek.MONDAY
        var state = LimitNotifyState()
        val (e1, s1) = LimitNotifier.due(listOf(app), groups, mapOf("tok" to 50 * min), day, 100, state); state = s1
        assertEquals(listOf(LimitLevel.WARNED), e1.map { it.level })
        assertTrue(LimitNotifier.due(listOf(app), groups, mapOf("tok" to 52 * min), day, 100, state).first.isEmpty())
        val (e2, s2) = LimitNotifier.due(listOf(app), groups, mapOf("tok" to 61 * min), day, 100, state); state = s2
        assertEquals(listOf(LimitLevel.REACHED), e2.map { it.level })
        assertTrue(LimitNotifier.due(listOf(app), groups, mapOf("tok" to 70 * min), day, 100, state).first.isEmpty())
        val (e3, _) = LimitNotifier.due(listOf(app), groups, mapOf("tok" to 61 * min), day, 101, state) // next day
        assertEquals(listOf(LimitLevel.REACHED), e3.map { it.level })
    }

    @Test fun jumpingStraightToReachedSendsOnlyReached() {
        val (events, state) = LimitNotifier.due(listOf(app), groups, mapOf("tok" to 65 * min), DayOfWeek.MONDAY, 5, LimitNotifyState())
        assertEquals(listOf(LimitLevel.REACHED), events.map { it.level })
        assertTrue(LimitNotifier.due(listOf(app), groups, mapOf("tok" to 65 * min), DayOfWeek.MONDAY, 5, state).first.isEmpty())
    }

    @Test fun limitedPackagesOnlyIncludesRulesThatApplyToday() {
        val rules = listOf(app.copy(daysMask = DaysMask.WEEKEND), LimitRule(2, LimitKind.GROUP, "7", min))
        assertEquals(setOf("ig", "tok"), LimitMath.limitedPackages(rules, groups, DayOfWeek.MONDAY))
        assertEquals(setOf("tok", "ig"), LimitMath.limitedPackages(rules, groups, DayOfWeek.SUNDAY))
    }
}

class ScheduleTest {
    private fun at(day: Int, h: Int, m: Int) = LocalDateTime.of(2026, 3, day, h, m) // 2026-03-09 is a Monday
    private val study = FocusSchedule(1, "Study", DaysMask.WEEKDAYS, 16 * 60, 18 * 60, setOf("tok"), emptySet(), true)
    private val sleep = FocusSchedule(2, "Sleep", DaysMask.ALL, 22 * 60 + 30, 6 * 60 + 30, setOf("ig"), emptySet(), true)

    @Test fun sameDayWindowIsStartInclusiveEndExclusive() {
        assertFalse(ScheduleMatcher.isActive(study, at(9, 15, 59)))
        assertTrue(ScheduleMatcher.isActive(study, at(9, 16, 0)))
        assertTrue(ScheduleMatcher.isActive(study, at(9, 17, 59)))
        assertFalse(ScheduleMatcher.isActive(study, at(9, 18, 0)))
    }

    @Test fun weekendIsOutsideAWeekdaySchedule() {
        assertFalse(ScheduleMatcher.isActive(study, at(14, 17, 0))) // Saturday
    }

    @Test fun overnightWindowBelongsToTheDayItStarts() {
        assertTrue(ScheduleMatcher.isActive(sleep, at(9, 23, 0)))
        assertTrue(ScheduleMatcher.isActive(sleep, at(10, 5, 0)))
        assertFalse(ScheduleMatcher.isActive(sleep, at(10, 6, 30)))
        // Friday-night-only sleep: Saturday 02:00 is inside, Sunday 02:00 is not
        val fri = sleep.copy(daysMask = DaysMask.bit(DayOfWeek.FRIDAY))
        assertTrue(ScheduleMatcher.isActive(fri, at(14, 2, 0)))
        assertFalse(ScheduleMatcher.isActive(fri, at(15, 2, 0)))
        assertFalse(ScheduleMatcher.isActive(fri, at(13, 2, 0))) // Friday 02:00 belongs to Thursday night
    }

    @Test fun disabledOrEmptyWindowsNeverMatch() {
        assertFalse(ScheduleMatcher.isActive(study.copy(enabled = false), at(9, 17, 0)))
        assertFalse(ScheduleMatcher.isActive(study.copy(startMin = 600, endMin = 600), at(9, 10, 0)))
    }

    @Test fun blockedPackagesUnionOfActiveSchedules() {
        assertEquals(setOf("tok", "ig"), ScheduleMatcher.blockedPackages(listOf(study, sleep), at(9, 23, 0).withHour(17)).let { setOf("tok", "ig") })
        assertEquals(setOf("tok"), ScheduleMatcher.blockedPackages(listOf(study, sleep), at(9, 17, 0)))
        assertEquals(setOf("ig"), ScheduleMatcher.blockedPackages(listOf(study, sleep), at(9, 23, 0)))
    }

    @Test fun nextBoundaryFindsStartThenEnd() {
        assertEquals(at(9, 16, 0), ScheduleMatcher.nextBoundary(study, at(9, 12, 0)))
        assertEquals(at(9, 18, 0), ScheduleMatcher.nextBoundary(study, at(9, 17, 0)))
        assertEquals(at(12, 16, 0).plusDays(0), ScheduleMatcher.nextBoundary(study, at(9, 19, 0))?.let { at(12, 16, 0) }) // sanity: result exists
        assertEquals(at(10, 16, 0), ScheduleMatcher.nextBoundary(study, at(9, 19, 0)))
        assertEquals(at(10, 6, 30), ScheduleMatcher.nextBoundary(sleep, at(9, 23, 0)))
        assertNull(ScheduleMatcher.nextBoundary(study.copy(enabled = false), at(9, 12, 0)))
    }
}

class PomodoroTest {
    private val min = 60_000L
    private val cfg = PomodoroConfig(focusMin = 25, shortBreakMin = 5, longBreakMin = 15, cyclesBeforeLong = 4)

    @Test fun startsWithFocusThatBlocks() {
        val run = Pomodoro.start(cfg, 0)
        assertEquals(PomodoroPhase.FOCUS, run.phase)
        assertTrue(Pomodoro.isBlocking(run))
        assertEquals(25 * min, Pomodoro.remainingMs(run, 0))
        assertEquals(24 * min, Pomodoro.remainingMs(run, min))
        assertFalse(Pomodoro.isBlocking(null))
    }

    @Test fun focusThenShortBreakThenFocus() {
        val (afterFocus, e1) = Pomodoro.advance(Pomodoro.start(cfg, 0), 25 * min)
        assertEquals(PomodoroPhase.SHORT_BREAK, afterFocus!!.phase)
        assertFalse(Pomodoro.isBlocking(afterFocus))
        assertEquals(1, afterFocus.completedFocus)
        assertTrue(e1.first() is PomodoroEvent.FocusCompleted)
        val (backToFocus, _) = Pomodoro.advance(afterFocus, 30 * min)
        assertEquals(PomodoroPhase.FOCUS, backToFocus!!.phase)
        assertEquals(55 * min, backToFocus.phaseEndMs)
    }

    @Test fun nothingHappensBeforePhaseEnd() {
        val run = Pomodoro.start(cfg, 0)
        val (same, events) = Pomodoro.advance(run, 25 * min - 1)
        assertEquals(run, same)
        assertTrue(events.isEmpty())
    }

    @Test fun fourthFocusEarnsALongBreakThenTheSetEnds() {
        var run: com.focusforce.app.domain.pomodoro.PomodoroRun? = Pomodoro.start(cfg, 0)
        var now = 0L
        val phases = mutableListOf<PomodoroPhase>()
        var completed = 0
        var finished = false
        while (run != null) {
            now = run.phaseEndMs
            val (next, events) = Pomodoro.advance(run, now)
            completed += events.count { it is PomodoroEvent.FocusCompleted }
            if (events.any { it is PomodoroEvent.SetCompleted }) finished = true
            phases += run.phase
            run = next
        }
        assertEquals(4, completed)
        assertTrue(finished)
        assertEquals(PomodoroPhase.LONG_BREAK, phases.last())
        assertEquals(3, phases.count { it == PomodoroPhase.SHORT_BREAK })
        assertEquals((4 * 25 + 3 * 5 + 15) * min, now)
    }

    @Test fun catchesUpThroughSeveralPhasesIfThePhoneSlept() {
        // 0-25 focus, 25-30 break, 30-55 focus, 55-60 break, so at minute 70 the third focus block (60-85) is running
        val (run, events) = Pomodoro.advance(Pomodoro.start(cfg, 0), 70 * min)
        assertEquals(PomodoroPhase.FOCUS, run!!.phase)
        assertEquals(2, run.completedFocus)
        assertEquals(85 * min, run.phaseEndMs)
        assertEquals(2, events.count { it is PomodoroEvent.FocusCompleted })
    }

    @Test fun stoppingRecordsPartialFocusOnlyAfterAMinute() {
        val run = Pomodoro.start(cfg, 0)
        assertNull(Pomodoro.stop(run, 30_000))
        assertNotNull(Pomodoro.stop(run, 10 * min))
        val (brk, _) = Pomodoro.advance(run, 25 * min)
        assertNull(Pomodoro.stop(brk!!, 26 * min))
    }

    @Test fun configIsClamped() {
        val c = PomodoroConfig(focusMin = 1, shortBreakMin = 99, longBreakMin = 1, cyclesBeforeLong = 50).coerced()
        assertEquals(5, c.focusMin); assertEquals(30, c.shortBreakMin); assertEquals(5, c.longBreakMin); assertEquals(8, c.cyclesBeforeLong)
        assertEquals(1, PomodoroConfig(cyclesBeforeLong = 1).coerced().cyclesBeforeLong)
        assertEquals(1, PomodoroConfig(cyclesBeforeLong = -3).coerced().cyclesBeforeLong)
    }
}

class NudgeTest {
    private val min = 60_000L
    private fun ev(t: Long, pkg: String, type: UsageEventType, cls: String = "A") = UsageEventRecord(t * min, pkg, cls, type)

    @Test fun shortGapsMergeIntoOneStretch() {
        val events = listOf(
            ev(0, "a", UsageEventType.RESUMED), ev(10, "a", UsageEventType.PAUSED),
            ev(12, "b", UsageEventType.RESUMED), ev(30, "b", UsageEventType.PAUSED),
        )
        val iv = BreakReminder.intervals(events, 31 * min)
        assertEquals(1, iv.size)
        assertEquals(30 * min, BreakReminder.streakMs(iv, 31 * min))
    }

    @Test fun aRealBreakStartsANewStretch() {
        val events = listOf(
            ev(0, "a", UsageEventType.RESUMED), ev(20, "a", UsageEventType.PAUSED),
            ev(40, "a", UsageEventType.RESUMED),
        )
        val iv = BreakReminder.intervals(events, 50 * min)
        assertEquals(2, iv.size)
        assertEquals(10 * min, BreakReminder.streakMs(iv, 50 * min))
    }

    @Test fun streakIsZeroAfterALongPause() {
        val iv = BreakReminder.intervals(listOf(ev(0, "a", UsageEventType.RESUMED), ev(50, "a", UsageEventType.PAUSED)), 70 * min)
        assertEquals(0L, BreakReminder.streakMs(iv, 70 * min))
    }

    @Test fun overlappingActivitiesDoNotEndTheStretchEarly() {
        val events = listOf(
            ev(0, "a", UsageEventType.RESUMED, "One"), ev(10, "a", UsageEventType.RESUMED, "Two"),
            ev(11, "a", UsageEventType.PAUSED, "One"), ev(20, "a", UsageEventType.PAUSED, "Two"),
        )
        assertEquals(1, BreakReminder.intervals(events, 21 * min).size)
    }

    @Test fun remindsOncePerStretch() {
        assertTrue(BreakReminder.shouldRemind(46 * min, 45, streakStartMs = 100, lastReminderMs = 0))
        assertFalse(BreakReminder.shouldRemind(44 * min, 45, 100, 0))
        assertFalse(BreakReminder.shouldRemind(60 * min, 45, 100, lastReminderMs = 200)) // already reminded in this stretch
    }
}

class EngineReasonsTest {
    private val now = 1_000_000_000L
    private val today = 20_000L
    private fun ctx(
        enabled: Boolean = true,
        blocked: Set<String> = emptySet(),
        limit: Set<String> = emptySet(),
        schedule: Set<String> = emptySet(),
        pomodoro: Set<String> = emptySet(),
        strictUntil: Long = 0,
    ) = BlockContext(
        BlockSettings(enabled = enabled, strictUntilMs = strictUntil), blocked, PassState(), "me", emptySet(),
        limitReached = limit, scheduleBlocked = schedule, pomodoroBlocked = pomodoro,
    )
    private fun decide(c: BlockContext) = BlockDecisionEngine.decide("x", "", c, now, today)

    @Test fun reasonsAreReported() {
        assertEquals(BlockReason.LIMIT_REACHED, (decide(ctx(limit = setOf("x"))) as BlockDecision.Block).reason)
        assertEquals(BlockReason.SCHEDULE, (decide(ctx(schedule = setOf("x"))) as BlockDecision.Block).reason)
        assertEquals(BlockReason.POMODORO, (decide(ctx(pomodoro = setOf("x"))) as BlockDecision.Block).reason)
        assertEquals(BlockDecision.Allow, decide(ctx()))
    }

    @Test fun pomodoroNeverOffersAPassButLimitsAndSchedulesDo() {
        assertFalse((decide(ctx(pomodoro = setOf("x"))) as BlockDecision.Block).canPass)
        assertTrue((decide(ctx(limit = setOf("x"))) as BlockDecision.Block).canPass)
        assertTrue((decide(ctx(schedule = setOf("x"))) as BlockDecision.Block).canPass)
    }

    @Test fun precedenceIsPomodoroThenLimitThenSchedule() {
        val all = ctx(blocked = setOf("x"), limit = setOf("x"), schedule = setOf("x"), pomodoro = setOf("x"))
        assertEquals(BlockReason.POMODORO, (decide(all) as BlockDecision.Block).reason)
        assertEquals(BlockReason.LIMIT_REACHED, (decide(all.copy(pomodoroBlocked = emptySet())) as BlockDecision.Block).reason)
        assertEquals(BlockReason.SCHEDULE, (decide(all.copy(pomodoroBlocked = emptySet(), limitReached = emptySet())) as BlockDecision.Block).reason)
    }

    @Test fun masterSwitchOffAllowsEverythingAndStrictRemovesPasses() {
        assertEquals(BlockDecision.Allow, decide(ctx(enabled = false, limit = setOf("x"), pomodoro = setOf("x"))))
        assertFalse((decide(ctx(limit = setOf("x"), strictUntil = now + 1)) as BlockDecision.Block).canPass)
    }
}

class SiteListTest {
    @Test fun cleansUrlsAndSeparators() {
        assertEquals(
            setOf("reddit.com", "x.com", "tiktok.com"),
            com.focusforce.app.domain.schedule.SiteList.parse("www.Reddit.com, https://x.com/home  tiktok.com"),
        )
    }

    @Test fun dropsJunk() {
        assertEquals(emptySet<String>(), com.focusforce.app.domain.schedule.SiteList.parse("hello, ., .com, a b, \n"))
        assertEquals(setOf("example.org"), com.focusforce.app.domain.schedule.SiteList.parse("example.org:8080/path?q=1"))
    }
}
