package com.focusforce.app.domain.social

import kotlinx.coroutines.flow.Flow

enum class FailReason { NOT_AVAILABLE, NOT_SIGNED_IN, NOT_FOUND, FULL, ALREADY_MEMBER, NETWORK, INVALID, DENIED, CANCELLED }

sealed interface SocialResult<out T> {
    data class Ok<T>(val value: T) : SocialResult<T>
    data class Fail(val reason: FailReason) : SocialResult<Nothing>
}

/** Sign-in. [signIn] needs an Activity because Google's account picker is a system sheet. */
interface AuthService {
    /** False when this build has no backend configured (no Firebase project). Nothing is ever sent then. */
    val available: Boolean
    val user: Flow<AuthUser?>
    suspend fun signIn(activity: android.app.Activity): SocialResult<AuthUser>
    suspend fun signOut()

    /** Deletes the sign-in account itself (call after [SocialBackend.deleteMyData]). DENIED means "sign in again first". */
    suspend fun deleteAccount(): SocialResult<Unit>
}

/**
 * Everything multiplayer needs. Implementations: Firebase (real), Simulated (debug builds only, in memory, fake players clearly
 * labelled) and Unavailable (release builds without a Firebase project: every call fails with NOT_AVAILABLE).
 * What is shared is limited by design to the fields of [SocialProfile], [Presence], [GroupSession] and a weekly score.
 * Usage data (apps, times, sites) never goes through this interface.
 */
interface SocialBackend {
    val available: Boolean

    /** Creates or updates my public profile; returns it with the final friend code (a code collision is resolved here). */
    suspend fun upsertProfile(profile: SocialProfile): SocialResult<SocialProfile>

    fun observeMySquad(): Flow<SquadView?>
    suspend fun createSquad(name: String): SocialResult<Squad>
    suspend fun joinSquad(code: String): SocialResult<Squad>
    suspend fun leaveSquad(): SocialResult<Unit>

    suspend fun setPresence(presence: Presence): SocialResult<Unit>

    fun observeGroupSession(): Flow<GroupSession?>
    suspend fun startGroupSession(focusMin: Int): SocialResult<GroupSession>
    suspend fun joinGroupSession(): SocialResult<Unit>

    fun observeFriends(): Flow<List<SocialProfile>>
    suspend fun addFriend(code: String): SocialResult<SocialProfile>
    suspend fun removeFriend(uid: String): SocialResult<Unit>

    suspend fun submitWeeklyScore(weekKey: String, score: Int, profile: SocialProfile): SocialResult<Unit>
    suspend fun removeFromLeaderboards(weekKey: String): SocialResult<Unit>
    fun observeGlobalBoard(weekKey: String): Flow<List<LeaderboardEntry>>
    suspend fun friendsBoard(weekKey: String): SocialResult<List<LeaderboardEntry>>

    /** Deletes my profile, friend code, friends, squad membership, presence and leaderboard entries. */
    suspend fun deleteMyData(): SocialResult<Unit>
}
