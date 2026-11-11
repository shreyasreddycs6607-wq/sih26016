package com.focusforce.app.domain.schedule

import com.focusforce.app.domain.limits.DaysMask
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * A named focus profile: on the chosen weekdays, between [startMin] and [endMin] (minutes since midnight),
 * the listed apps are blocked. If [endMin] is earlier than [startMin] the window crosses midnight
 * (e.g. Sleep 22:30 to 06:30) and belongs to the weekday it STARTS on. [sites] are stored now and enforced
 * when website blocking ships.
 */
data class FocusSchedule(
    val id: Long,
    val name: String,
    val daysMask: Int,
    val startMin: Int,
    val endMin: Int,
    val packages: Set<String>,
    val sites: Set<String>,
    val silenceNotifications: Boolean,
    val enabled: Boolean = true,
)

object ScheduleMatcher {
    fun isActive(s: FocusSchedule, now: LocalDateTime): Boolean {
        if (!s.enabled || s.startMin == s.endMin) return false
        val minute = now.hour * 60 + now.minute
        val today = now.dayOfWeek
        return if (s.startMin < s.endMin) {
            DaysMask.has(s.daysMask, today) && minute in s.startMin until s.endMin
        } else {
            (DaysMask.has(s.daysMask, today) && minute >= s.startMin) ||
                (DaysMask.has(s.daysMask, today.minus(1)) && minute < s.endMin)
        }
    }

    fun active(list: List<FocusSchedule>, now: LocalDateTime): List<FocusSchedule> = list.filter { isActive(it, now) }

    /** Apps blocked by any schedule that is active now. */
    fun blockedPackages(list: List<FocusSchedule>, now: LocalDateTime): Set<String> =
        active(list, now).flatMapTo(mutableSetOf()) { it.packages }

    /** Next moment (after [now]) at which this schedule starts or ends, to arm an alarm. Null if it never fires. */
    fun nextBoundary(s: FocusSchedule, now: LocalDateTime): LocalDateTime? {
        if (!s.enabled || s.startMin == s.endMin || s.daysMask == 0) return null
        val candidates = mutableListOf<LocalDateTime>()
        for (offset in 0L..8L) {
            val date: LocalDate = now.toLocalDate().plusDays(offset)
            if (!DaysMask.has(s.daysMask, date.dayOfWeek)) continue
            candidates += date.atTime(LocalTime.of(s.startMin / 60, s.startMin % 60))
            val endDate = if (s.startMin < s.endMin) date else date.plusDays(1)
            candidates += endDate.atTime(LocalTime.of(s.endMin / 60, s.endMin % 60))
        }
        return candidates.filter { it.isAfter(now) }.minOrNull()
    }

    fun nextBoundary(list: List<FocusSchedule>, now: LocalDateTime): LocalDateTime? =
        list.mapNotNull { nextBoundary(it, now) }.minOrNull()
}

/** Starting points for the four built-in profiles; names come from string resources in the UI. */
enum class SchedulePreset(val daysMask: Int, val startMin: Int, val endMin: Int, val silence: Boolean) {
    STUDY(DaysMask.WEEKDAYS, 16 * 60, 18 * 60, true),
    SLEEP(DaysMask.ALL, 22 * 60 + 30, 6 * 60 + 30, true),
    WORK(DaysMask.WEEKDAYS, 9 * 60, 17 * 60, true),
    FAMILY(DaysMask.ALL, 18 * 60 + 30, 20 * 60, true),
}

/** Turns what a person types ("www.reddit.com, https://x.com/home  tiktok.com") into clean lowercase domains. */
object SiteList {
    fun parse(text: String): Set<String> =
        text.split(',', ' ', '\n', ';', '\t')
            .map { it.trim().lowercase() }
            .map { it.removePrefix("https://").removePrefix("http://").removePrefix("www.") }
            .map { it.substringBefore('/').substringBefore('?').substringBefore(':') }
            .filter { it.contains('.') && it.all { ch -> ch.isLetterOrDigit() || ch == '.' || ch == '-' } && !it.startsWith('.') && !it.endsWith('.') }
            .toSet()
}
