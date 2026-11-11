package com.focusforce.app.data.social

import android.app.Activity
import com.focusforce.app.domain.game.Rank
import com.focusforce.app.domain.model.HeroTheme
import com.focusforce.app.domain.social.AuthService
import com.focusforce.app.domain.social.AuthUser
import com.focusforce.app.domain.social.FailReason
import com.focusforce.app.domain.social.GroupPomodoro
import com.focusforce.app.domain.social.GroupSession
import com.focusforce.app.domain.social.Leaderboard
import com.focusforce.app.domain.social.LeaderboardEntry
import com.focusforce.app.domain.social.MemberView
import com.focusforce.app.domain.social.Presence
import com.focusforce.app.domain.social.PresenceMath
import com.focusforce.app.domain.social.PresenceState
import com.focusforce.app.domain.social.ShareCode
import com.focusforce.app.domain.social.SocialBackend
import com.focusforce.app.domain.social.SocialProfile
import com.focusforce.app.domain.social.SocialResult
import com.focusforce.app.domain.social.Squad
import com.focusforce.app.domain.social.SquadView
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * DEBUG BUILDS ONLY (the DI module never selects it in release). An in-memory stand-in so the multiplayer screens can be
 * developed and looked at without a Firebase project. Every other player is invented and carries "(sim)" in the name.
 * It stores nothing and sends nothing.
 */
@Singleton
class SimulatedAuthService @Inject constructor() : AuthService {
    override val available = true
    private val current = MutableStateFlow<AuthUser?>(null)
    override val user = current
    override suspend fun signIn(activity: Activity): SocialResult<AuthUser> =
        SocialResult.Ok(AuthUser(ME, "Debug Hero")).also { current.value = it.value }
    override suspend fun signOut() { current.value = null }
    override suspend fun deleteAccount(): SocialResult<Unit> { current.value = null; return SocialResult.Ok(Unit) }

    companion object { const val ME = "sim-me" }
}

@Singleton
class SimulatedSocialBackend @Inject constructor() : SocialBackend {
    override val available = true

    private val others = listOf(
        SocialProfile("sim-1", "Nova (sim)", HeroTheme.VECTOR, Rank.GUARDIAN, 9, "NOVA22", false),
        SocialProfile("sim-2", "Echo (sim)", HeroTheme.NOCTIS, Rank.CADET, 5, "ECHO33", false),
        SocialProfile("sim-3", "Blitz (sim)", HeroTheme.DASH, Rank.SENTINEL, 14, "BLTZ44", false),
        SocialProfile("sim-4", "Quill (sim)", HeroTheme.NOCTIS, Rank.RECRUIT, 2, "QUIL55", false),
    )
    private val scores = mapOf("sim-1" to 1240, "sim-2" to 610, "sim-3" to 2230, "sim-4" to 90)

    private val myProfile = MutableStateFlow<SocialProfile?>(null)
    private val squad = MutableStateFlow<Squad?>(null)
    private val myPresence = MutableStateFlow<Presence?>(null)
    private val session = MutableStateFlow<GroupSession?>(null)
    private val friends = MutableStateFlow<List<SocialProfile>>(emptyList())
    private val myScore = MutableStateFlow<Pair<Int, SocialProfile>?>(null)

    override suspend fun upsertProfile(profile: SocialProfile): SocialResult<SocialProfile> {
        val kept = profile.copy(friendCode = myProfile.value?.friendCode?.takeIf { it.isNotBlank() } ?: profile.friendCode.ifBlank { ShareCode.generate() })
        myProfile.value = kept
        return SocialResult.Ok(kept)
    }

    override fun observeMySquad() = combine(squad, myProfile, myPresence) { s, me, pres ->
        if (s == null || me == null) null else {
            val now = System.currentTimeMillis()
            val members = listOf(MemberView(me, PresenceMath.effective(pres, now))) +
                others.filter { it.uid in s.memberUids }.mapIndexed { i, p ->
                    MemberView(p, if (i == 0) PresenceState.FOCUSING else PresenceState.IDLE) // first simulated member is always "focusing"
                }
            SquadView(s, members, me.uid)
        }
    }

    override suspend fun createSquad(name: String): SocialResult<Squad> {
        val me = myProfile.value ?: return SocialResult.Fail(FailReason.NOT_SIGNED_IN)
        return SocialResult.Ok(Squad("sim-squad", name, ShareCode.generate(), me.uid, listOf(me.uid))).also { squad.value = it.value }
    }

    override suspend fun joinSquad(code: String): SocialResult<Squad> {
        val me = myProfile.value ?: return SocialResult.Fail(FailReason.NOT_SIGNED_IN)
        if (ShareCode.normalize(code) != DEBUG_SQUAD_CODE) return SocialResult.Fail(FailReason.NOT_FOUND)
        return SocialResult.Ok(Squad("sim-squad", "Debug Squad (sim)", DEBUG_SQUAD_CODE, "sim-1", listOf("sim-1", "sim-2", "sim-3", me.uid)))
            .also { squad.value = it.value }
    }

    override suspend fun leaveSquad(): SocialResult<Unit> { squad.value = null; session.value = null; myPresence.value = null; return SocialResult.Ok(Unit) }
    override suspend fun setPresence(presence: Presence): SocialResult<Unit> { myPresence.value = presence; return SocialResult.Ok(Unit) }

    override fun observeGroupSession() = session
    override suspend fun startGroupSession(focusMin: Int): SocialResult<GroupSession> {
        val s = squad.value ?: return SocialResult.Fail(FailReason.NOT_FOUND)
        val me = myProfile.value ?: return SocialResult.Fail(FailReason.NOT_SIGNED_IN)
        return SocialResult.Ok(GroupSession("sim-session", s.id, me.uid, System.currentTimeMillis(), focusMin, setOf(me.uid, "sim-3"))).also { session.value = it.value }
    }
    override suspend fun joinGroupSession(): SocialResult<Unit> {
        val s = session.value ?: return SocialResult.Fail(FailReason.NOT_FOUND)
        val me = myProfile.value ?: return SocialResult.Fail(FailReason.NOT_SIGNED_IN)
        session.value = s.copy(participants = s.participants + me.uid)
        return SocialResult.Ok(Unit)
    }

    override fun observeFriends() = friends
    override suspend fun addFriend(code: String): SocialResult<SocialProfile> {
        val c = ShareCode.normalize(code) ?: return SocialResult.Fail(FailReason.INVALID)
        val found = others.firstOrNull { it.friendCode == c } ?: return SocialResult.Fail(FailReason.NOT_FOUND)
        if (friends.value.any { it.uid == found.uid }) return SocialResult.Fail(FailReason.ALREADY_MEMBER)
        friends.value = friends.value + found
        return SocialResult.Ok(found)
    }
    override suspend fun removeFriend(uid: String): SocialResult<Unit> { friends.value = friends.value.filterNot { it.uid == uid }; return SocialResult.Ok(Unit) }

    override suspend fun submitWeeklyScore(weekKey: String, score: Int, profile: SocialProfile): SocialResult<Unit> {
        myScore.value = score to profile
        return SocialResult.Ok(Unit)
    }
    override suspend fun removeFromLeaderboards(weekKey: String): SocialResult<Unit> { myScore.value = null; return SocialResult.Ok(Unit) }

    private fun board(): List<LeaderboardEntry> {
        val entries = others.map { LeaderboardEntry(it.uid, it.displayName, it.hero, it.rank, scores.getValue(it.uid)) }
        val mine = myScore.value?.let { (s, p) -> LeaderboardEntry(p.uid, p.displayName, p.hero, p.rank, s) }
        return entries + listOfNotNull(mine)
    }
    override fun observeGlobalBoard(weekKey: String) = myScore.map { Leaderboard.ranked(board()) }
    override suspend fun friendsBoard(weekKey: String): SocialResult<List<LeaderboardEntry>> {
        val ids = friends.value.map { it.uid }.toSet() + listOfNotNull(myProfile.value?.uid)
        return SocialResult.Ok(Leaderboard.ranked(board().filter { it.uid in ids }))
    }

    override suspend fun deleteMyData(): SocialResult<Unit> {
        myProfile.value = null; squad.value = null; session.value = null; friends.value = emptyList(); myScore.value = null; myPresence.value = null
        return SocialResult.Ok(Unit)
    }

    companion object { const val DEBUG_SQUAD_CODE = "DEBUG2" }
}
