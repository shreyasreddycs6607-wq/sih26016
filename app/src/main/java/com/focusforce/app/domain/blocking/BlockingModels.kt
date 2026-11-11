package com.focusforce.app.domain.blocking

/** User-controlled blocking switches. `strictUntilMs` > now means a strict lock is running. */
data class BlockSettings(
    val enabled: Boolean = false,
    val passesEnabled: Boolean = true,
    /** While a strict lock runs, also keep the user out of Settings and the uninstall dialog. */
    val guardSettings: Boolean = true,
    val strictUntilMs: Long = 0L,
) {
    fun strictActive(nowMs: Long): Boolean = strictUntilMs > nowMs
}

object PassPolicy {
    const val PER_DAY = 3
    const val DURATION_MS = 5 * 60_000L
}

/** "5-minute pass" bookkeeping: how many were used today and which apps currently hold one. */
data class PassState(
    val epochDay: Long = 0L,
    val usedToday: Int = 0,
    val grants: Map<String, Long> = emptyMap(),
) {
    fun remaining(today: Long): Int = (PassPolicy.PER_DAY - if (epochDay == today) usedToday else 0).coerceAtLeast(0)

    fun activeUntil(packageName: String, nowMs: Long): Long? = grants[packageName]?.takeIf { it > nowMs }

    fun grant(packageName: String, nowMs: Long, today: Long): PassState {
        val used = if (epochDay == today) usedToday else 0
        val live = grants.filterValues { it > nowMs }
        return PassState(today, used + 1, live + (packageName to nowMs + PassPolicy.DURATION_MS))
    }
}

enum class BlockReason { BLOCKED_APP, LIMIT_REACHED, SCHEDULE, POMODORO, SHORT_VIDEO, WEBSITE, STRICT_SETTINGS, STRICT_UNINSTALL }

sealed interface BlockDecision {
    data object Allow : BlockDecision
    data class Block(
        val packageName: String,
        val reason: BlockReason,
        val canPass: Boolean,
        val passesLeft: Int,
        /** Extra text for the screen: the blocked site name for WEBSITE. Shown to the user, never stored. */
        val detail: String? = null,
        /** What a 5-minute pass unlocks: the app, or "feed:<pkg>" / "site:<host>" for just that section or site. */
        val passKey: String = packageName,
    ) : BlockDecision
}

/** Which mechanism can currently tell us the foreground app. */
enum class DetectionMode {
    /** Accessibility service: instant and the most reliable. */
    ACCESSIBILITY,

    /** Polling UsageStats once a second from the foreground service (needs overlay to launch the block screen). */
    USAGE_POLLING,

    NONE;

    companion object {
        fun from(accessibility: Boolean, usageAccess: Boolean, overlay: Boolean): DetectionMode = when {
            accessibility -> ACCESSIBILITY
            usageAccess && overlay -> USAGE_POLLING
            else -> NONE
        }
    }
}

/** Apps that must never be blocked or offered in the picker: calls, emergency, system UI. */
object SafetyPackages {
    val neverBlock: Set<String> = setOf(
        "android", "com.android.systemui", "com.android.phone", "com.android.server.telecom",
        "com.android.dialer", "com.google.android.dialer", "com.samsung.android.dialer",
        "com.android.emergency", "com.google.android.apps.safetyhub",
    )

    /** Settings-style apps that could disable or uninstall FocusForce; guarded during a strict lock. */
    val settingsApps: Set<String> = setOf(
        "com.android.settings", "com.miui.securitycenter", "com.coloros.safecenter",
        "com.oplus.battery", "com.iqoo.secure", "com.vivo.permissionmanager", "com.samsung.android.lool",
    )

    val installers: Set<String> = setOf(
        "com.google.android.packageinstaller", "com.android.packageinstaller", "com.samsung.android.packageinstaller",
    )
}

object StrictLock {
    val optionsMinutes: List<Int> = listOf(30, 60, 120, 240)
    fun until(nowMs: Long, minutes: Int): Long = nowMs + minutes * 60_000L
}
