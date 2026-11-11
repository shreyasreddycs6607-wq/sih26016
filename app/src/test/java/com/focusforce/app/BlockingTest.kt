package com.focusforce.app

import com.focusforce.app.domain.blocking.BlockContext
import com.focusforce.app.domain.blocking.BlockDecision
import com.focusforce.app.domain.blocking.BlockDecisionEngine
import com.focusforce.app.domain.blocking.BlockReason
import com.focusforce.app.domain.blocking.BlockingRules
import com.focusforce.app.domain.blocking.BlockSettings
import com.focusforce.app.domain.blocking.DetectionMode
import com.focusforce.app.domain.blocking.ForegroundResolver
import com.focusforce.app.domain.blocking.PassPolicy
import com.focusforce.app.domain.blocking.PassState
import com.focusforce.app.domain.blocking.StrictLock
import com.focusforce.app.domain.usage.UsageEventRecord
import com.focusforce.app.domain.usage.UsageEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockingTest {
    private val now = 1_000_000_000L
    private val today = 20_000L
    private fun ctx(
        settings: BlockSettings = BlockSettings(enabled = true),
        blocked: Set<String> = setOf("tok"),
        pass: PassState = PassState(),
    ) = BlockContext(settings, blocked, pass, ownPackage = "me", neverBlock = setOf("launcher", "com.android.systemui"))

    private fun decide(pkg: String, cls: String = "", c: BlockContext = ctx(), at: Long = now) =
        BlockDecisionEngine.decide(pkg, cls, c, at, today)

    @Test fun blocksListedAppAndOffersPass() {
        val d = decide("tok") as BlockDecision.Block
        assertEquals(BlockReason.BLOCKED_APP, d.reason)
        assertTrue(d.canPass)
        assertEquals(PassPolicy.PER_DAY, d.passesLeft)
    }

    @Test fun allowsUnlistedOwnAndNeverBlockApps() {
        assertEquals(BlockDecision.Allow, decide("docs"))
        assertEquals(BlockDecision.Allow, decide("me"))
        assertEquals(BlockDecision.Allow, decide("launcher", c = ctx(blocked = setOf("launcher"))))
    }

    @Test fun disabledBlockingAllowsEverything() {
        assertEquals(BlockDecision.Allow, decide("tok", c = ctx(BlockSettings(enabled = false))))
    }

    @Test fun activePassAllowsUntilExpiry() {
        val pass = PassState().grant("tok", now, today)
        assertEquals(BlockDecision.Allow, decide("tok", c = ctx(pass = pass), at = now + PassPolicy.DURATION_MS - 1))
        val after = decide("tok", c = ctx(pass = pass), at = now + PassPolicy.DURATION_MS) as BlockDecision.Block
        assertEquals(PassPolicy.PER_DAY - 1, after.passesLeft)
    }

    @Test fun passesRunOutAndDisableOffer() {
        var pass = PassState()
        repeat(PassPolicy.PER_DAY) { pass = pass.grant("a$it", now - PassPolicy.DURATION_MS * 2, today) }
        assertEquals(0, pass.remaining(today))
        assertFalse((decide("tok", c = ctx(pass = pass)) as BlockDecision.Block).canPass)
    }

    @Test fun passesResetOnNewDay() {
        val pass = PassState().grant("tok", now, today)
        assertEquals(PassPolicy.PER_DAY - 1, pass.remaining(today))
        assertEquals(PassPolicy.PER_DAY, pass.remaining(today + 1))
        assertEquals(1, pass.grant("x", now, today + 1).usedToday)
    }

    @Test fun passSettingOffRemovesOffer() {
        val d = decide("tok", c = ctx(BlockSettings(enabled = true, passesEnabled = false))) as BlockDecision.Block
        assertFalse(d.canPass)
    }

    @Test fun strictModeRemovesPassEvenWithPassesLeft() {
        val strict = BlockSettings(enabled = true, strictUntilMs = now + 60_000)
        val d = decide("tok", c = ctx(strict)) as BlockDecision.Block
        assertFalse(d.canPass)
        assertEquals(PassPolicy.PER_DAY, d.passesLeft)
    }

    @Test fun strictGuardsSettingsAndUninstallOnly() {
        val strict = ctx(BlockSettings(enabled = true, strictUntilMs = now + 60_000), blocked = emptySet())
        assertEquals(BlockReason.STRICT_SETTINGS, (decide("com.android.settings", c = strict) as BlockDecision.Block).reason)
        val un = decide("com.google.android.packageinstaller", "com.android.packageinstaller.UninstallerActivity", strict) as BlockDecision.Block
        assertEquals(BlockReason.STRICT_UNINSTALL, un.reason)
        assertEquals(BlockDecision.Allow, decide("com.google.android.packageinstaller", "com.android.packageinstaller.InstallStart", strict))
        assertEquals(BlockDecision.Allow, decide("com.android.systemui", c = strict))
    }

    @Test fun strictGuardCanBeTurnedOffAndExpires() {
        val noGuard = ctx(BlockSettings(enabled = true, guardSettings = false, strictUntilMs = now + 60_000), blocked = emptySet())
        assertEquals(BlockDecision.Allow, decide("com.android.settings", c = noGuard))
        val expired = ctx(BlockSettings(enabled = true, strictUntilMs = now - 1), blocked = emptySet())
        assertEquals(BlockDecision.Allow, decide("com.android.settings", c = expired))
    }

    private fun ev(t: Long, pkg: String, type: UsageEventType, cls: String = "Main") = UsageEventRecord(t, pkg, cls, type)

    @Test fun foregroundResolverFollowsLastResumedAndClearsOnPause() {
        val events = listOf(ev(1, "a", UsageEventType.RESUMED), ev(2, "a", UsageEventType.PAUSED), ev(3, "b", UsageEventType.RESUMED))
        assertEquals("b", ForegroundResolver.latest(events)?.packageName)
        assertNull(ForegroundResolver.latest(events + ev(4, "b", UsageEventType.PAUSED)))
        assertNull(ForegroundResolver.latest(emptyList()))
    }

    @Test fun detectionModePrefersAccessibility() {
        assertEquals(DetectionMode.ACCESSIBILITY, DetectionMode.from(true, false, false))
        assertEquals(DetectionMode.USAGE_POLLING, DetectionMode.from(false, true, true))
        assertEquals(DetectionMode.NONE, DetectionMode.from(false, true, false))
        assertEquals(DetectionMode.NONE, DetectionMode.from(false, false, true))
    }

    @Test fun strictLockEndsAfterChosenMinutes() {
        assertEquals(now + 3_600_000L, StrictLock.until(now, 60))
        assertTrue(BlockSettings(strictUntilMs = StrictLock.until(now, 30)).strictActive(now))
        assertFalse(BlockSettings(strictUntilMs = StrictLock.until(now, 30)).strictActive(now + 30 * 60_000L))
    }

    @Test fun strictLockCannotBeLoosenedButCanBeExtended() {
        val strict = BlockSettings(enabled = true, strictUntilMs = now + 60 * 60_000L)
        assertFalse(BlockingRules.canLoosen(strict, now))
        assertTrue(BlockingRules.canLoosen(strict, now + 60 * 60_000L))
        assertEquals(now + 60 * 60_000L, BlockingRules.strictUntil(strict, now, 30)) // shorter request does not shorten
        assertEquals(now + 240 * 60_000L, BlockingRules.strictUntil(strict, now, 240))
    }

    @Test fun passGrantRules() {
        val s = BlockSettings(enabled = true)
        assertTrue(BlockingRules.canGrantPass(s, PassState(), now, today))
        assertFalse(BlockingRules.canGrantPass(s.copy(strictUntilMs = now + 1), PassState(), now, today))
        assertFalse(BlockingRules.canGrantPass(s.copy(passesEnabled = false), PassState(), now, today))
        assertFalse(BlockingRules.canGrantPass(s, PassState(today, PassPolicy.PER_DAY), now, today))
    }
}
