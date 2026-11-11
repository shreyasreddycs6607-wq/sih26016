package com.focusforce.app

import com.focusforce.app.domain.game.FocusMinutes
import com.focusforce.app.domain.game.Levels
import com.focusforce.app.domain.game.Rank
import com.focusforce.app.domain.game.WeekKey
import com.focusforce.app.domain.game.Xp
import com.focusforce.app.domain.model.HeroTheme
import com.focusforce.app.domain.pomodoro.Pomodoro
import com.focusforce.app.domain.pomodoro.PomodoroConfig
import com.focusforce.app.domain.pomodoro.PomodoroEvent
import com.focusforce.app.domain.pomodoro.PomodoroPhase
import com.focusforce.app.domain.social.DisplayName
import com.focusforce.app.domain.social.GroupPomodoro
import com.focusforce.app.domain.social.GroupSession
import com.focusforce.app.domain.social.Leaderboard
import com.focusforce.app.domain.social.LeaderboardEntry
import com.focusforce.app.domain.social.Presence
import com.focusforce.app.domain.social.PresenceMath
import com.focusforce.app.domain.social.PresenceState
import com.focusforce.app.domain.social.ShareCode
import com.focusforce.app.domain.usage.DaySummary
import java.time.LocalDate
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XpTest {
    private val hour = 3_600_000L
    private val goal = 3 * hour
    private val today = LocalDate.of(2026, 3, 10)
    private fun day(daysAgo: Long, hours: Double) = DaySummary(today.minusDays(daysAgo), (hours * hour).toLong(), null)

    @Test fun underGoalDaysEarnXpWithGrowingStreakBonus() {
        // 3 finished days under goal: 50 + 50+5 + 50+10 = 165
        val r = Xp.compute(listOf(day(3, 2.0), day(2, 2.0), day(1, 2.5)), emptyList(), goal, today)
        assertEquals(165, r.totalXp)
        assertEquals(3, r.currentStreak)
        assertEquals(3, r.longestStreak)
    }

    @Test fun anOverGoalDayBreaksTheStreakButKeepsTheLongest() {
        val r = Xp.compute(listOf(day(4, 1.0), day(3, 1.0), day(2, 5.0), day(1, 1.0)), emptyList(), goal, today)
        assertEquals(1, r.currentStreak)
        assertEquals(2, r.longestStreak)
        assertEquals((50 + 55) + 0 + 50, r.totalXp)
    }

    @Test fun exactlyAtGoalIsNotUnderGoal() {
        assertEquals(0, Xp.compute(listOf(day(1, 3.0)), emptyList(), goal, today).totalXp)
    }

    @Test fun aMissingDayBreaksTheStreakBecauseItCannotBeProven() {
        val r = Xp.compute(listOf(day(3, 1.0), day(1, 1.0)), emptyList(), goal, today) // day 2 missing
        assertEquals(1, r.currentStreak)
        assertEquals(1, r.longestStreak)
    }

    @Test fun todayNeverLosesOrEarnsTheDayBonusButFocusCounts() {
        val r = Xp.compute(listOf(day(0, 9.0)), listOf(FocusMinutes(today, 25)), goal, today)
        assertEquals(25, r.totalXp)
        assertFalse(r.todayOnTrack)
        assertTrue(Xp.compute(listOf(day(0, 1.0)), emptyList(), goal, today).todayOnTrack)
    }

    @Test fun focusMinutesEarnOnePointEach() {
        val r = Xp.compute(emptyList(), listOf(FocusMinutes(today.minusDays(2), 25), FocusMinutes(today.minusDays(2), 50)), goal, today)
        assertEquals(75, r.totalXp)
    }

    @Test fun streakBonusIsCappedAtSevenDays() {
        val days = (1L..12L).map { day(it, 1.0) }
        val r = Xp.compute(days, emptyList(), goal, today)
        assertEquals(12, r.currentStreak)
        // 12 * 50 + bonus(0..11 capped at 7) = 600 + (0+5+10+15+20+25+30+35)+4*35 = 600 + 140 + 140 = 880
        assertEquals(880, r.totalXp)
    }

    @Test fun noHistoryMeansZero() {
        val r = Xp.compute(emptyList(), emptyList(), goal, today)
        assertEquals(0, r.totalXp); assertEquals(0, r.currentStreak)
    }

    @Test fun ranksAndLevels() {
        assertEquals(Rank.RECRUIT, Rank.of(0)); assertEquals(Rank.RECRUIT, Rank.of(299))
        assertEquals(Rank.CADET, Rank.of(300)); assertEquals(Rank.LEGEND, Rank.of(50_000))
        assertEquals(Rank.CADET, Rank.RECRUIT.next)
        assertNull(Rank.LEGEND.next)
        assertEquals(1, Levels.of(0)); assertEquals(1, Levels.of(24)); assertEquals(2, Levels.of(25)); assertEquals(3, Levels.of(100))
        assertEquals(0.5f, Levels.progress(62), 0.02f) // level 2 runs 25..100 XP
        assertEquals(1f, Levels.rankProgress(99_999), 0f)
        assertEquals(0.5f, Levels.rankProgress(150), 0.001f)
    }

    @Test fun weeklyXpOnlyCountsThisIsoWeek() {
        // 2026-03-10 is a Tuesday; the week started Monday 2026-03-09
        val days = listOf(day(9, 1.0), day(8, 1.0), day(1, 1.0)) // 03-01, 03-02, 03-09(Mon)
        val focus = listOf(FocusMinutes(today.minusDays(9), 100), FocusMinutes(today.minusDays(1), 30))
        // this week's finished days: only Monday 03-09 (streak restarts after the gap): 50 + 30 focus = 80
        assertEquals(80, WeekKey.weeklyXp(days, focus, goal, today))
    }

    @Test fun isoWeekKeysAcrossYearBoundaries() {
        assertEquals("2026-W11", WeekKey.of(LocalDate.of(2026, 3, 10)))
        assertEquals("2025-W01", WeekKey.of(LocalDate.of(2024, 12, 30)))
        assertEquals("2026-W01", WeekKey.of(LocalDate.of(2026, 1, 1)))
        assertEquals("2020-W53", WeekKey.of(LocalDate.of(2021, 1, 3)))
        assertEquals(LocalDate.of(2026, 3, 9), WeekKey.weekStart(LocalDate.of(2026, 3, 15)))
    }
}

class SocialTest {
    @Test fun displayNames() {
        assertEquals("Ada Lovelace", DisplayName.sanitize("  Ada   Lovelace \n"))
        assertNull(DisplayName.sanitize("A"))
        assertNull(DisplayName.sanitize("visit www.spam.com"))
        assertNull(DisplayName.sanitize("https://x.y"))
        assertEquals(24, DisplayName.sanitize("x".repeat(60))!!.length)
    }

    @Test fun shareCodesAreReadableAndRoundTrip() {
        repeat(200) {
            val code = ShareCode.generate(Random(it))
            assertEquals(6, code.length)
            assertTrue(code.all { c -> c in ShareCode.ALPHABET })
            assertEquals(code, ShareCode.normalize(code.lowercase()))
        }
        assertEquals("ABC234", ShareCode.normalize(" abc-234 "))
        assertNull(ShareCode.normalize("ABC23"))      // too short
        assertNull(ShareCode.normalize("ABCO23"))     // O is not in the alphabet
        assertNull(ShareCode.normalize("ABC1234"))
    }

    @Test fun presenceNeedsAFreshHeartbeatAndAnUnfinishedBlock() {
        val now = 10_000_000L
        val fresh = Presence("u", PresenceState.FOCUSING, now + 600_000, now - 30_000)
        assertEquals(PresenceState.FOCUSING, PresenceMath.effective(fresh, now))
        assertEquals(PresenceState.IDLE, PresenceMath.effective(fresh.copy(updatedAtMs = now - PresenceMath.STALE_MS - 1), now)) // phone died
        assertEquals(PresenceState.IDLE, PresenceMath.effective(fresh.copy(focusEndsAtMs = now - 1), now))                       // block over
        assertEquals(PresenceState.IDLE, PresenceMath.effective(fresh.copy(state = PresenceState.IDLE), now))
        assertEquals(PresenceState.IDLE, PresenceMath.effective(null, now))
    }

    private val min = 60_000L
    private val session = GroupSession("g", "s", "u", startedAtMs = 0, focusMin = 25, participants = setOf("u"))

    @Test fun groupSessionJoinWindow() {
        assertTrue(GroupPomodoro.isJoinable(session, 10 * min))
        assertTrue(GroupPomodoro.isJoinable(session, 24 * min - 1))
        assertFalse(GroupPomodoro.isJoinable(session, 24 * min))
        assertEquals(25 * min, GroupPomodoro.endMs(session))
    }

    @Test fun joiningMirrorsTheSharedBlockThenOneBreakThenEnds() {
        val run = GroupPomodoro.runFor(session, PomodoroConfig(focusMin = 50, shortBreakMin = 7), nowMs = 10 * min)!!
        assertEquals(PomodoroPhase.FOCUS, run.phase)
        assertEquals(25, run.config.focusMin)               // the shared length wins over the local setting
        assertEquals(25 * min, run.phaseEndMs)              // same end for everybody
        assertEquals(10 * min, Pomodoro.remainingMs(run, 15 * min))
        val (afterFocus, e1) = Pomodoro.advance(run, 25 * min)
        assertTrue(e1.any { it is PomodoroEvent.FocusCompleted })
        assertEquals(PomodoroPhase.LONG_BREAK, afterFocus!!.phase)
        val (done, e2) = Pomodoro.advance(afterFocus, afterFocus.phaseEndMs)
        assertNull(done)
        assertTrue(e2.any { it is PomodoroEvent.SetCompleted })
        assertNull(GroupPomodoro.runFor(session, PomodoroConfig(), nowMs = 24 * min))
    }

    private fun entry(uid: String, name: String, score: Int) = LeaderboardEntry(uid, name, HeroTheme.DASH, Rank.CADET, score)

    @Test fun leaderboardOrderingTiesHiddenPlayersAndPositions() {
        val all = listOf(entry("a", "Zed", 100), entry("b", "amy", 300), entry("c", "Bob", 300), entry("d", "Cat", 50))
        val ranked = Leaderboard.ranked(all)
        assertEquals(listOf("b", "c", "a", "d"), ranked.map { it.uid })          // tie broken by name, case-insensitive
        assertEquals(1, Leaderboard.positionOf(ranked, "c"))                       // equal scores share a position
        assertEquals(3, Leaderboard.positionOf(ranked, "a"))
        assertNull(Leaderboard.positionOf(ranked, "nobody"))
        assertEquals(listOf("b", "a", "d"), Leaderboard.ranked(all, hidden = setOf("c")).map { it.uid })
    }

    @Test fun plausibleScoreBounds() {
        assertTrue(Leaderboard.isPlausible(0)); assertTrue(Leaderboard.isPlausible(Leaderboard.MAX_WEEKLY_SCORE))
        assertFalse(Leaderboard.isPlausible(-1)); assertFalse(Leaderboard.isPlausible(Leaderboard.MAX_WEEKLY_SCORE + 1))
    }
}
