package com.focusforce.app.domain.social

import com.focusforce.app.domain.game.Rank
import com.focusforce.app.domain.model.HeroTheme
import com.focusforce.app.domain.pomodoro.PomodoroConfig
import com.focusforce.app.domain.pomodoro.PomodoroPhase
import com.focusforce.app.domain.pomodoro.PomodoroRun
import kotlin.random.Random

data class AuthUser(val uid: String, val displayName: String?)

/** What other people can see of you. Contains no usage data at all: only name, hero, rank, level and a weekly score. */
data class SocialProfile(
    val uid: String,
    val displayName: String,
    val hero: HeroTheme,
    val rank: Rank,
    val level: Int,
    val friendCode: String,
    val hideFromLeaderboards: Boolean,
)

object DisplayName {
    const val MIN = 2
    const val MAX = 24

    /** Trims, collapses spaces, drops control characters and links; null if what is left is too short. */
    fun sanitize(raw: String): String? {
        val cleaned = raw.filter { !it.isISOControl() }.trim().replace(Regex("\\s+"), " ").take(MAX).trim()
        if (cleaned.length < MIN) return null
        if (cleaned.contains("http", ignoreCase = true) || cleaned.contains("://") || cleaned.contains("www.", ignoreCase = true)) return null
        return cleaned
    }
}

/** Short codes people can read out loud: no 0/O, 1/I/L. Used for squads and friends. */
object ShareCode {
    const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    const val LENGTH = 6

    fun generate(random: Random = Random.Default): String = buildString { repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }

    /** Accepts "abc-234", " ABC 234 " etc. Returns null if it cannot be a code. */
    fun normalize(raw: String): String? {
        val s = raw.uppercase().filter { it.isLetterOrDigit() }
        return if (s.length == LENGTH && s.all { it in ALPHABET }) s else null
    }
}

data class Squad(val id: String, val name: String, val code: String, val ownerUid: String, val memberUids: List<String>) {
    companion object {
        const val MAX_MEMBERS = 12
        const val MAX_NAME = 30
    }
}

enum class PresenceState { IDLE, FOCUSING }

data class Presence(val uid: String, val state: PresenceState, val focusEndsAtMs: Long, val updatedAtMs: Long)

object PresenceMath {
    /** A FOCUSING presence only counts while the focus block has not ended and a heartbeat arrived recently (phone died = not focusing). */
    const val STALE_MS = 3 * 60_000L
    const val HEARTBEAT_MS = 60_000L

    fun effective(p: Presence?, nowMs: Long): PresenceState =
        if (p != null && p.state == PresenceState.FOCUSING && p.focusEndsAtMs > nowMs && nowMs - p.updatedAtMs <= STALE_MS) PresenceState.FOCUSING
        else PresenceState.IDLE
}

data class MemberView(val profile: SocialProfile, val presence: PresenceState)
data class SquadView(val squad: Squad, val members: List<MemberView>, val me: String)

/** A shared focus block anyone in the squad can join. */
data class GroupSession(val id: String, val squadId: String, val startedByUid: String, val startedAtMs: Long, val focusMin: Int, val participants: Set<String>)

object GroupPomodoro {
    private const val MIN_JOIN_WINDOW_MS = 60_000L

    fun endMs(s: GroupSession): Long = s.startedAtMs + s.focusMin * 60_000L

    /** Late joiners are welcome until a minute before the end. */
    fun isJoinable(s: GroupSession, nowMs: Long): Boolean = nowMs < endMs(s) - MIN_JOIN_WINDOW_MS

    /**
     * The local Pomodoro run that mirrors the shared block: it ends at the same moment for everybody, is followed by one break,
     * and then the set is over (one block, so cycles before the long break is 1).
     */
    fun runFor(s: GroupSession, config: PomodoroConfig, nowMs: Long): PomodoroRun? {
        if (!isJoinable(s, nowMs)) return null
        val breakMin = config.shortBreakMin.coerceAtLeast(5)
        return PomodoroRun(
            config = config.copy(focusMin = s.focusMin, longBreakMin = breakMin, cyclesBeforeLong = 1).coerced(),
            phase = PomodoroPhase.FOCUS,
            completedFocus = 0,
            phaseStartMs = s.startedAtMs,
            phaseEndMs = endMs(s),
        )
    }
}

data class LeaderboardEntry(val uid: String, val displayName: String, val hero: HeroTheme, val rank: Rank, val weeklyScore: Int)

object Leaderboard {
    /** Highest believable week: 7 days x (16 h of focus + day bonus with a full streak bonus). Used to clamp and, in the rules, to reject. */
    const val MAX_WEEKLY_SCORE = 8_000

    fun isPlausible(score: Int): Boolean = score in 0..MAX_WEEKLY_SCORE

    /** Highest score first; ties by name so the order never flickers. Hidden players are removed. */
    fun ranked(entries: List<LeaderboardEntry>, hidden: Set<String> = emptySet()): List<LeaderboardEntry> =
        entries.filter { it.uid !in hidden }.sortedWith(compareByDescending<LeaderboardEntry> { it.weeklyScore }.thenBy { it.displayName.lowercase() })

    /** 1-based position of [uid], or null if absent. Equal scores share a position. */
    fun positionOf(ranked: List<LeaderboardEntry>, uid: String): Int? {
        val me = ranked.firstOrNull { it.uid == uid } ?: return null
        return ranked.count { it.weeklyScore > me.weeklyScore } + 1
    }
}
