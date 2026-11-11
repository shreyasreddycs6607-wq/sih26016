package com.focusforce.app.data.blocking

import com.focusforce.app.domain.blocking.BlockingRules
import com.focusforce.app.domain.repo.BlockSettingsRepository
import com.focusforce.app.domain.repo.BlockedAppsRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

enum class Outcome { OK, DENIED_STRICT, NO_PASS }

/**
 * The only writer of blocking state. Every action that would loosen blocking goes through
 * [BlockingRules], so strict mode really has no override from inside the app.
 */
@Singleton
class BlockingManager @Inject constructor(
    private val settings: BlockSettingsRepository,
    private val apps: BlockedAppsRepository,
) {
    suspend fun setEnabled(on: Boolean): Outcome {
        val s = settings.settings.first()
        if (!on && !BlockingRules.canLoosen(s, System.currentTimeMillis())) return Outcome.DENIED_STRICT
        settings.update { it.copy(enabled = on) }
        return Outcome.OK
    }

    suspend fun setPassesEnabled(on: Boolean): Outcome {
        val s = settings.settings.first()
        if (on && !BlockingRules.canLoosen(s, System.currentTimeMillis())) return Outcome.DENIED_STRICT
        settings.update { it.copy(passesEnabled = on) }
        return Outcome.OK
    }

    suspend fun setGuardSettings(on: Boolean): Outcome {
        val s = settings.settings.first()
        if (!on && !BlockingRules.canLoosen(s, System.currentTimeMillis())) return Outcome.DENIED_STRICT
        settings.update { it.copy(guardSettings = on) }
        return Outcome.OK
    }

    suspend fun addApps(packages: Collection<String>) = apps.add(packages)

    suspend fun removeApps(packages: Collection<String>): Outcome {
        val s = settings.settings.first()
        if (!BlockingRules.canLoosen(s, System.currentTimeMillis())) return Outcome.DENIED_STRICT
        apps.remove(packages)
        return Outcome.OK
    }

    /** Starts (or extends, never shortens) a strict lock and makes sure blocking is on. */
    suspend fun startStrict(minutes: Int) {
        val now = System.currentTimeMillis()
        settings.update { it.copy(enabled = true, strictUntilMs = BlockingRules.strictUntil(it, now, minutes)) }
    }

    suspend fun grantPass(packageName: String): Outcome {
        val now = System.currentTimeMillis()
        val today = LocalDate.now().toEpochDay()
        val s = settings.settings.first()
        val pass = settings.passState.first()
        if (!BlockingRules.canGrantPass(s, pass, now, today)) {
            return if (s.strictActive(now)) Outcome.DENIED_STRICT else Outcome.NO_PASS
        }
        settings.updatePass { it.grant(packageName, now, today) }
        return Outcome.OK
    }
}
