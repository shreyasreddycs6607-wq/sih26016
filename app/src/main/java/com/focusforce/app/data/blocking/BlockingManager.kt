package com.focusforce.app.data.blocking

import com.focusforce.app.data.billing.EntitlementRepository
import com.focusforce.app.domain.blocking.BlockingRules
import com.focusforce.app.domain.pro.Countable
import com.focusforce.app.domain.pro.ProFeature
import com.focusforce.app.domain.pro.ProPolicy
import com.focusforce.app.domain.repo.BlockSettingsRepository
import com.focusforce.app.domain.repo.BlockedAppsRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** UPGRADE = needs FocusForce Pro (or the free limit is reached). */
enum class Outcome { OK, DENIED_STRICT, NO_PASS, INVALID, UPGRADE }

/**
 * The only writer of blocking state. Every action that would loosen blocking goes through
 * [BlockingRules], so strict mode really has no override from inside the app.
 */
@Singleton
class BlockingManager @Inject constructor(
    private val settings: BlockSettingsRepository,
    private val apps: BlockedAppsRepository,
    private val entitlement: EntitlementRepository,
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

    /** Adds as many as the free limit allows (all of them for Pro); UPGRADE if some were left out. */
    suspend fun addApps(packages: Collection<String>): Outcome {
        val current = apps.observe().first()
        val fresh = packages.filter { it !in current }
        val allowed = ProPolicy.allowedToAdd(Countable.GUARDED_APPS, current.size, fresh.size, entitlement.isPro())
        if (allowed > 0) apps.add(fresh.take(allowed))
        return if (allowed < fresh.size) Outcome.UPGRADE else Outcome.OK
    }

    suspend fun removeApps(packages: Collection<String>): Outcome {
        val s = settings.settings.first()
        if (!BlockingRules.canLoosen(s, System.currentTimeMillis())) return Outcome.DENIED_STRICT
        apps.remove(packages)
        return Outcome.OK
    }

    /** Starts (or extends, never shortens) a strict lock and makes sure blocking is on. */
    suspend fun startStrict(minutes: Int): Outcome {
        if (!ProPolicy.hasFeature(ProFeature.STRICT_LOCK, entitlement.isPro())) return Outcome.UPGRADE
        val now = System.currentTimeMillis()
        settings.update { it.copy(enabled = true, strictUntilMs = BlockingRules.strictUntil(it, now, minutes)) }
        return Outcome.OK
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
