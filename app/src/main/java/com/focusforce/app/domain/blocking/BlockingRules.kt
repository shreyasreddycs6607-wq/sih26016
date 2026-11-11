package com.focusforce.app.domain.blocking

/** What the user may change while a strict lock is running. Pure, so the "no override" promise is tested. */
object BlockingRules {
    /** Blocking can be switched off, apps removed and the Settings guard turned off only when no strict lock runs. */
    fun canLoosen(settings: BlockSettings, nowMs: Long): Boolean = !settings.strictActive(nowMs)

    fun canGrantPass(settings: BlockSettings, pass: PassState, nowMs: Long, today: Long): Boolean =
        settings.enabled && settings.passesEnabled && !settings.strictActive(nowMs) && pass.remaining(today) > 0

    /** A strict lock can be extended but never shortened. */
    fun strictUntil(settings: BlockSettings, nowMs: Long, minutes: Int): Long =
        maxOf(settings.strictUntilMs, StrictLock.until(nowMs, minutes))
}
