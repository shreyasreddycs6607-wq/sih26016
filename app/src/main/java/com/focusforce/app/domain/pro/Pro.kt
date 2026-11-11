package com.focusforce.app.domain.pro

/**
 * What the free version includes and what needs FocusForce Pro. This is a PRODUCT decision kept in one place so it is easy to change.
 * Principles: tracking, insights for the recent past, basic blocking and the whole hero experience are free; heavier
 * customisation and the strongest tools are Pro. Losing Pro never deletes anything you made: it only stops you adding more
 * beyond the free limits, and strict locks already running finish normally.
 */
object FreeLimits {
    const val GUARDED_APPS = 5
    const val SCHEDULES = 1
    const val USAGE_LIMITS = 2
}

enum class Countable { GUARDED_APPS, SCHEDULES, USAGE_LIMITS }

enum class ProFeature { STRICT_LOCK, SHORT_VIDEO_BLOCK, WEBSITE_BLOCK, CSV_EXPORT, MONTHLY_INSIGHTS, GROUP_FOCUS }

object ProPolicy {
    private fun limit(kind: Countable) = when (kind) {
        Countable.GUARDED_APPS -> FreeLimits.GUARDED_APPS
        Countable.SCHEDULES -> FreeLimits.SCHEDULES
        Countable.USAGE_LIMITS -> FreeLimits.USAGE_LIMITS
    }

    /** Can one more [kind] be added when [current] already exist? */
    fun canAdd(kind: Countable, current: Int, pro: Boolean): Boolean = pro || current < limit(kind)

    /** How many more may be added; null = unlimited. */
    fun remaining(kind: Countable, current: Int, pro: Boolean): Int? = if (pro) null else (limit(kind) - current).coerceAtLeast(0)

    /** How many of [adding] new items fit; used when "select all" adds several apps at once. */
    fun allowedToAdd(kind: Countable, current: Int, adding: Int, pro: Boolean): Int =
        if (pro) adding else adding.coerceAtMost((limit(kind) - current).coerceAtLeast(0))

    fun hasFeature(feature: ProFeature, pro: Boolean): Boolean = pro
}

/** Decides whether the user is Pro from what the store said and what we cached, tolerating being offline for a while. */
object EntitlementResolver {
    /** If the store cannot be reached, a previously verified Pro status is honoured for this long. */
    const val OFFLINE_GRACE_MS = 30L * 24 * 60 * 60 * 1000

    /**
     * @param storeSaysPro null when the store could not be reached.
     * @param cachedPro last verified value; @param lastVerifiedMs when it was verified.
     */
    fun resolve(storeSaysPro: Boolean?, cachedPro: Boolean, lastVerifiedMs: Long, nowMs: Long): Boolean =
        storeSaysPro ?: (cachedPro && nowMs - lastVerifiedMs in 0..OFFLINE_GRACE_MS)
}
