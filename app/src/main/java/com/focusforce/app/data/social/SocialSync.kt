package com.focusforce.app.data.social

import com.focusforce.app.data.focus.FocusPrefs
import com.focusforce.app.domain.pomodoro.Pomodoro
import com.focusforce.app.domain.repo.SettingsRepository
import com.focusforce.app.domain.social.AuthService
import com.focusforce.app.domain.social.DisplayName
import com.focusforce.app.domain.social.Leaderboard
import com.focusforce.app.domain.social.Presence
import com.focusforce.app.domain.social.PresenceMath
import com.focusforce.app.domain.social.PresenceState
import com.focusforce.app.domain.social.SocialBackend
import com.focusforce.app.domain.social.SocialProfile
import com.focusforce.app.domain.social.SocialResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps what other people see in step with reality: your public profile, whether you are in a focus block (presence, with a
 * heartbeat) and your weekly score. Does nothing at all unless a backend is configured AND you are signed in; every
 * call is skipped when nothing changed. Called by the foreground service and by the multiplayer screen.
 */
@Singleton
class SocialSync @Inject constructor(
    private val auth: AuthService,
    private val backend: SocialBackend,
    private val prefs: SocialPrefs,
    private val game: GameStateRepository,
    private val appSettings: SettingsRepository,
    private val focusPrefs: FocusPrefs,
) {
    private val mutex = Mutex()
    private var lastProfileKey: String? = null
    private var lastPresence: Presence? = null
    private var lastPresenceAt = 0L
    private var lastFullAt = 0L
    private var lastScore = -1
    private var lastScoreAt = 0L
    private var hiddenWeek: String? = null

    suspend fun tick(nowMs: Long = System.currentTimeMillis(), force: Boolean = false) = mutex.withLock {
        if (!backend.available) return@withLock
        val me = auth.user.first() ?: return@withLock
        val settings = prefs.current()

        // presence: cheap, runs every tick, sent only on change or as a heartbeat while focusing
        val run = focusPrefs.pomodoroRun.first()?.let { Pomodoro.advance(it, nowMs).first }
        val focusing = settings.sharePresence && Pomodoro.isBlocking(run) && run != null
        val desiredState = if (focusing) PresenceState.FOCUSING else PresenceState.IDLE
        val desiredEnd = if (focusing && run != null) run.phaseEndMs else 0L
        val previous = lastPresence
        val changed = previous == null || previous.state != desiredState || previous.focusEndsAtMs != desiredEnd
        val heartbeatDue = desiredState == PresenceState.FOCUSING && nowMs - lastPresenceAt >= PresenceMath.HEARTBEAT_MS
        if (changed || heartbeatDue || force) {
            val presence = Presence(me.uid, desiredState, desiredEnd, nowMs)
            if (backend.setPresence(presence) is SocialResult.Ok) {
                lastPresence = presence
                lastPresenceAt = nowMs
            }
        }

        // profile + score: heavier, at most once a minute unless forced
        if (!force && nowMs - lastFullAt < FULL_INTERVAL_MS) return@withLock
        lastFullAt = nowMs
        val state = game.current()
        val name = settings.displayName ?: DisplayName.sanitize(me.displayName.orEmpty()) ?: DEFAULT_NAME
        val hero = appSettings.settings.first().hero
        var profile = SocialProfile(me.uid, name, hero, state.rank, state.level, settings.friendCode, settings.hideFromLeaderboards)
        val key = profile.toString()
        if (force || key != lastProfileKey) {
            when (val saved = backend.upsertProfile(profile)) {
                is SocialResult.Ok -> {
                    profile = saved.value
                    if (profile.friendCode != settings.friendCode) prefs.setFriendCode(profile.friendCode)
                    lastProfileKey = profile.toString()
                }
                is SocialResult.Fail -> return@withLock
            }
        }
        if (settings.hideFromLeaderboards) {
            if (hiddenWeek != state.weekKey) {
                if (backend.removeFromLeaderboards(state.weekKey) is SocialResult.Ok) hiddenWeek = state.weekKey
            }
        } else {
            hiddenWeek = null
            val score = state.weeklyXp.coerceAtMost(Leaderboard.MAX_WEEKLY_SCORE)
            if (force || score != lastScore || nowMs - lastScoreAt >= SCORE_REFRESH_MS) {
                if (backend.submitWeeklyScore(state.weekKey, score, profile) is SocialResult.Ok) {
                    lastScore = score
                    lastScoreAt = nowMs
                }
            }
        }
    }

    /** Forget cached "already sent" state, e.g. after signing out or deleting data. */
    suspend fun reset() = mutex.withLock {
        lastProfileKey = null; lastPresence = null; lastPresenceAt = 0L; lastFullAt = 0L; lastScore = -1; lastScoreAt = 0L; hiddenWeek = null
    }

    private companion object {
        const val FULL_INTERVAL_MS = 60_000L
        const val SCORE_REFRESH_MS = 10 * 60_000L
        const val DEFAULT_NAME = "Hero"
    }
}
