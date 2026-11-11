package com.focusforce.app.domain.limits

import java.time.DayOfWeek

/** Weekday bitmask: bit 0 = Monday ... bit 6 = Sunday. */
object DaysMask {
    const val ALL = 0b1111111
    const val WEEKDAYS = 0b0011111
    const val WEEKEND = 0b1100000

    fun bit(day: DayOfWeek): Int = 1 shl (day.value - 1)
    fun has(mask: Int, day: DayOfWeek): Boolean = mask and bit(day) != 0
    fun toggle(mask: Int, day: DayOfWeek): Int = mask xor bit(day)
}

enum class LimitKind { APP, GROUP }

data class AppGroup(val id: Long, val name: String, val packages: Set<String>)

/**
 * A daily time limit. [target] is a package name (APP) or a group id as text (GROUP).
 * It only applies on the weekdays in [daysMask].
 */
data class LimitRule(
    val id: Long,
    val kind: LimitKind,
    val target: String,
    val dailyLimitMs: Long,
    val daysMask: Int = DaysMask.ALL,
    val enabled: Boolean = true,
)

enum class LimitLevel { OK, WARNED, REACHED }

object LimitMath {
    const val WARN_PERCENT = 80
    const val MIN_LIMIT_MS = 5 * 60_000L
    const val MAX_LIMIT_MS = 12 * 60 * 60_000L
    const val STEP_MS = 5 * 60_000L

    fun appliesToday(rule: LimitRule, day: DayOfWeek): Boolean = rule.enabled && DaysMask.has(rule.daysMask, day)

    fun packagesOf(rule: LimitRule, groups: Map<Long, AppGroup>): Set<String> = when (rule.kind) {
        LimitKind.APP -> setOf(rule.target)
        LimitKind.GROUP -> rule.target.toLongOrNull()?.let { groups[it]?.packages } ?: emptySet()
    }

    fun usedMs(rule: LimitRule, groups: Map<Long, AppGroup>, usage: Map<String, Long>): Long =
        packagesOf(rule, groups).sumOf { usage[it] ?: 0L }

    /** Under 80% is OK, 80% up to the limit is WARNED, at or over the limit is REACHED. */
    fun level(usedMs: Long, limitMs: Long): LimitLevel = when {
        limitMs <= 0L -> LimitLevel.OK
        usedMs >= limitMs -> LimitLevel.REACHED
        usedMs * 100 >= limitMs * WARN_PERCENT -> LimitLevel.WARNED
        else -> LimitLevel.OK
    }

    /** Every package that is over its limit right now, across all rules that apply today. */
    fun reachedPackages(rules: List<LimitRule>, groups: Map<Long, AppGroup>, usage: Map<String, Long>, day: DayOfWeek): Set<String> =
        rules.filter { appliesToday(it, day) && level(usedMs(it, groups, usage), it.dailyLimitMs) == LimitLevel.REACHED }
            .flatMapTo(mutableSetOf()) { packagesOf(it, groups) }

    /** Packages that have at least one limit rule today (used to skip the usage query for everything else). */
    fun limitedPackages(rules: List<LimitRule>, groups: Map<Long, AppGroup>, day: DayOfWeek): Set<String> =
        rules.filter { appliesToday(it, day) }.flatMapTo(mutableSetOf()) { packagesOf(it, groups) }
}

/** Which warnings were already sent today, so each (rule, level) fires once and resets at midnight. */
data class LimitNotifyState(val epochDay: Long = 0L, val sent: Set<String> = emptySet())

data class LimitEvent(val rule: LimitRule, val level: LimitLevel, val usedMs: Long)

object LimitNotifier {
    private fun key(rule: LimitRule, level: LimitLevel) = "${rule.id}:${level.name}"

    /**
     * Returns the warnings that are newly due and the updated state. Crossing straight past 80% to
     * 100% sends only the "reached" message (the 80% one is marked as sent too).
     */
    fun due(
        rules: List<LimitRule>,
        groups: Map<Long, AppGroup>,
        usage: Map<String, Long>,
        day: DayOfWeek,
        epochDay: Long,
        state: LimitNotifyState,
    ): Pair<List<LimitEvent>, LimitNotifyState> {
        val sent = (if (state.epochDay == epochDay) state.sent else emptySet()).toMutableSet()
        val events = mutableListOf<LimitEvent>()
        for (rule in rules.filter { LimitMath.appliesToday(it, day) }) {
            val used = LimitMath.usedMs(rule, groups, usage)
            when (LimitMath.level(used, rule.dailyLimitMs)) {
                LimitLevel.REACHED -> if (sent.add(key(rule, LimitLevel.REACHED))) {
                    sent += key(rule, LimitLevel.WARNED)
                    events += LimitEvent(rule, LimitLevel.REACHED, used)
                }
                LimitLevel.WARNED -> if (sent.add(key(rule, LimitLevel.WARNED))) events += LimitEvent(rule, LimitLevel.WARNED, used)
                LimitLevel.OK -> Unit
            }
        }
        return events to LimitNotifyState(epochDay, sent)
    }
}
