package com.focusforce.app.data.social

import android.app.Activity
import com.focusforce.app.domain.social.AuthService
import com.focusforce.app.domain.social.AuthUser
import com.focusforce.app.domain.social.FailReason
import com.focusforce.app.domain.social.GroupSession
import com.focusforce.app.domain.social.LeaderboardEntry
import com.focusforce.app.domain.social.Presence
import com.focusforce.app.domain.social.SocialBackend
import com.focusforce.app.domain.social.SocialProfile
import com.focusforce.app.domain.social.SocialResult
import com.focusforce.app.domain.social.Squad
import com.focusforce.app.domain.social.SquadView
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

private val unavailable = SocialResult.Fail(FailReason.NOT_AVAILABLE)

/** Used when the build has no Firebase project: nothing is available, nothing is sent anywhere. */
@Singleton
class UnavailableAuthService @Inject constructor() : AuthService {
    override val available = false
    override val user: Flow<AuthUser?> = flowOf(null)
    override suspend fun signIn(activity: Activity): SocialResult<AuthUser> = unavailable
    override suspend fun signOut() = Unit
    override suspend fun deleteAccount(): SocialResult<Unit> = unavailable
}

@Singleton
class UnavailableSocialBackend @Inject constructor() : SocialBackend {
    override val available = false
    override suspend fun upsertProfile(profile: SocialProfile): SocialResult<SocialProfile> = unavailable
    override fun observeMySquad(): Flow<SquadView?> = flowOf(null)
    override suspend fun createSquad(name: String): SocialResult<Squad> = unavailable
    override suspend fun joinSquad(code: String): SocialResult<Squad> = unavailable
    override suspend fun leaveSquad(): SocialResult<Unit> = unavailable
    override suspend fun setPresence(presence: Presence): SocialResult<Unit> = unavailable
    override fun observeGroupSession(): Flow<GroupSession?> = flowOf(null)
    override suspend fun startGroupSession(focusMin: Int): SocialResult<GroupSession> = unavailable
    override suspend fun joinGroupSession(): SocialResult<Unit> = unavailable
    override fun observeFriends(): Flow<List<SocialProfile>> = flowOf(emptyList())
    override suspend fun addFriend(code: String): SocialResult<SocialProfile> = unavailable
    override suspend fun removeFriend(uid: String): SocialResult<Unit> = unavailable
    override suspend fun submitWeeklyScore(weekKey: String, score: Int, profile: SocialProfile): SocialResult<Unit> = unavailable
    override suspend fun removeFromLeaderboards(weekKey: String): SocialResult<Unit> = unavailable
    override fun observeGlobalBoard(weekKey: String): Flow<List<LeaderboardEntry>> = flowOf(emptyList())
    override suspend fun friendsBoard(weekKey: String): SocialResult<List<LeaderboardEntry>> = unavailable
    override suspend fun deleteMyData(): SocialResult<Unit> = unavailable
}
