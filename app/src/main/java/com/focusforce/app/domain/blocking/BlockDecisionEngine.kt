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
        if (!ctx.settings.enabled || packageName !in ctx.blocked) return BlockDecision.Allow
        if (ctx.pass.activeUntil(packageName, nowMs) != null) return BlockDecision.Allow

        val left = ctx.pass.remaining(today)
        return BlockDecision.Block(
            packageName = packageName,
            reason = BlockReason.BLOCKED_APP,
            canPass = ctx.settings.passesEnabled && !strict && left > 0,
            passesLeft = left,
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
