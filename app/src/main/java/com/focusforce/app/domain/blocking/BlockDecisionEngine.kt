package com.focusforce.app.domain.blocking

import com.focusforce.app.domain.usage.UsageEventRecord
import com.focusforce.app.domain.usage.UsageEventType

/** Everything the decision needs; built by the coordinator from the database and settings. */
data class BlockContext(
    val settings: BlockSettings,
    val blocked: Set<String>,
    val pass: PassState,
    val ownPackage: String,
    /** Launchers, keyboards and [SafetyPackages.neverBlock]. */
    val neverBlock: Set<String>,
    val guardedSettings: Set<String> = SafetyPackages.settingsApps,
    val installers: Set<String> = SafetyPackages.installers,
    /** Apps over a daily limit right now. */
    val limitReached: Set<String> = emptySet(),
    /** Apps blocked by a focus schedule that is active now. */
    val scheduleBlocked: Set<String> = emptySet(),
    /** Apps blocked by a running Pomodoro FOCUS phase (empty during breaks or when none runs). */
    val pomodoroBlocked: Set<String> = emptySet(),
    /** Apps whose short-video feed is switched on for blocking AND currently on screen. */
    val feedOnScreen: Set<String> = emptySet(),
    /** Site name of the blocked page currently open in the browser being decided about, or null. */
    val blockedSite: String? = null,
)

object BlockDecisionEngine {
    /**
     * Decides what to do when [packageName] (window class [className], may be empty) comes to the front.
     * Order matters: safety exclusions, then strict-lock guards, then the user's blocked list.
     */
    fun decide(packageName: String, className: String, ctx: BlockContext, nowMs: Long, today: Long): BlockDecision {
        if (packageName == ctx.ownPackage || packageName in ctx.neverBlock) return BlockDecision.Allow
        val strict = ctx.settings.strictActive(nowMs)

        if (strict && ctx.settings.guardSettings) {
            if (packageName in ctx.guardedSettings) {
                return BlockDecision.Block(packageName, BlockReason.STRICT_SETTINGS, canPass = false, passesLeft = 0)
            }
            if (packageName in ctx.installers && className.contains("uninstall", ignoreCase = true)) {
                return BlockDecision.Block(packageName, BlockReason.STRICT_UNINSTALL, canPass = false, passesLeft = 0)
            }
        }
        if (!ctx.settings.enabled) return BlockDecision.Allow

        val left = ctx.pass.remaining(today)
        // A running Pomodoro focus block cannot be bought off with a pass: stop the timer instead.
        if (packageName in ctx.pomodoroBlocked) {
            return BlockDecision.Block(packageName, BlockReason.POMODORO, canPass = false, passesLeft = left)
        }
        var detail: String? = null
        var passKey = packageName
        val reason = when {
            packageName in ctx.limitReached -> BlockReason.LIMIT_REACHED
            packageName in ctx.scheduleBlocked -> BlockReason.SCHEDULE
            packageName in ctx.blocked -> BlockReason.BLOCKED_APP
            ctx.blockedSite != null -> {
                detail = ctx.blockedSite
                passKey = "site:${ctx.blockedSite}"
                BlockReason.WEBSITE
            }
            packageName in ctx.feedOnScreen -> {
                passKey = "feed:$packageName"
                BlockReason.SHORT_VIDEO
            }
            else -> return BlockDecision.Allow
        }
        if (ctx.pass.activeUntil(passKey, nowMs) != null) return BlockDecision.Allow
        return BlockDecision.Block(
            packageName = packageName,
            reason = reason,
            canPass = ctx.settings.passesEnabled && !strict && left > 0,
            passesLeft = left,
            detail = detail,
            passKey = passKey,
        )
    }
}

/** Polling fallback: which activity is in the foreground at the end of a batch of events. */
object ForegroundResolver {
    fun latest(events: List<UsageEventRecord>): UsageEventRecord? {
        var current: UsageEventRecord? = null
        for (e in events.sortedBy { it.timestampMs }) {
            when (e.type) {
                UsageEventType.RESUMED -> current = e
                UsageEventType.PAUSED ->
                    if (current != null && current.packageName == e.packageName && current.className == e.className) current = null
            }
        }
        return current
    }
}
