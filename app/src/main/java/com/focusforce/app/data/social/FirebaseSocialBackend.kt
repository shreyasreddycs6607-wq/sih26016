package com.focusforce.app.data.social

import com.focusforce.app.domain.game.Rank
import com.focusforce.app.domain.game.WeekKey
import com.focusforce.app.domain.model.HeroTheme
import com.focusforce.app.domain.social.FailReason
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
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.ServerTimestampBehavior
import com.google.firebase.firestore.firestore
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

private class Failure(val reason: FailReason) : Exception()

/**
 * Firestore implementation. Data layout and the security rules that protect it are in firestore.rules / docs/FIRESTORE.md.
 * Shared on purpose: public profile (name, hero, rank, level, friend code, hide flag), squad membership, presence,
 * group sessions and a weekly score. Never shared: any usage data, app lists, sites or screen content.
 */
@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseSocialBackend @Inject constructor() : SocialBackend {

    override val available = true

    private val db: FirebaseFirestore get() = Firebase.firestore
    private val auth: FirebaseAuth get() = Firebase.auth
    private val uid: String? get() = auth.currentUser?.uid
    private val profileCache = ConcurrentHashMap<String, Pair<Long, SocialProfile>>()

    @Volatile private var knownSquadId: String? = null

    // ---- plumbing --------------------------------------------------------------------------------------

    private fun fail(reason: FailReason): Nothing = throw Failure(reason)
    private fun me(): String = uid ?: fail(FailReason.NOT_SIGNED_IN)

    private suspend fun <T> guarded(block: suspend () -> T): SocialResult<T> = try {
        SocialResult.Ok(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Failure) {
        SocialResult.Fail(e.reason)
    } catch (e: FirebaseFirestoreException) {
        SocialResult.Fail(
            when (e.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED -> FailReason.DENIED
                FirebaseFirestoreException.Code.NOT_FOUND -> FailReason.NOT_FOUND
                else -> FailReason.NETWORK
            },
        )
    } catch (_: Exception) {
        SocialResult.Fail(FailReason.NETWORK)
    }

    private fun currentUid(): Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    private fun docFlow(ref: DocumentReference): Flow<DocumentSnapshot?> = callbackFlow {
        val reg = ref.addSnapshotListener { snap, error -> if (error != null) close(error) else trySend(snap) }
        awaitClose { reg.remove() }
    }

    private fun queryFlow(query: Query): Flow<QuerySnapshot> = callbackFlow {
        val reg = query.addSnapshotListener { snap, error -> if (error != null) close(error) else if (snap != null) trySend(snap) }
        awaitClose { reg.remove() }
    }

    private fun ticker(everyMs: Long): Flow<Unit> = flow { while (true) { emit(Unit); delay(everyMs) } }

    private fun users(uid: String) = db.collection("users").document(uid)
    private fun profiles() = db.collection("profiles")
    private fun squads() = db.collection("squads")
    private fun entries(week: String) = db.collection("leaderboards").document(week).collection("entries")

    // ---- mapping ---------------------------------------------------------------------------------------

    private fun SocialProfile.toMap(): Map<String, Any> = mapOf(
        "displayName" to displayName, "hero" to hero.name, "rank" to rank.name, "level" to level.toLong(),
        "friendCode" to friendCode, "hideFromLeaderboards" to hideFromLeaderboards,
    )

    private fun DocumentSnapshot.toProfile(): SocialProfile? {
        if (!exists()) return null
        return SocialProfile(
            uid = id,
            displayName = getString("displayName") ?: return null,
            hero = HeroTheme.entries.firstOrNull { it.name == getString("hero") } ?: HeroTheme.VECTOR,
            rank = Rank.entries.firstOrNull { it.name == getString("rank") } ?: Rank.RECRUIT,
            level = (getLong("level") ?: 1L).toInt(),
            friendCode = getString("friendCode").orEmpty(),
            hideFromLeaderboards = getBoolean("hideFromLeaderboards") ?: false,
        )
    }

    private fun DocumentSnapshot.toSquad(): Squad = Squad(
        id = id,
        name = getString("name").orEmpty(),
        code = getString("code").orEmpty(),
        ownerUid = getString("ownerUid").orEmpty(),
        memberUids = (get("memberUids") as? List<*>)?.filterIsInstance<String>().orEmpty(),
    )

    private fun DocumentSnapshot.toPresence(): Presence = Presence(
        uid = id,
        state = PresenceState.entries.firstOrNull { it.name == getString("state") } ?: PresenceState.IDLE,
        focusEndsAtMs = getLong("focusEndsAtMs") ?: 0L,
        updatedAtMs = getLong("updatedAtMs") ?: 0L,
    )

    private fun DocumentSnapshot.toSession(squadId: String): GroupSession? {
        if (!exists()) return null
        val startedAt = getTimestamp("startedAt", ServerTimestampBehavior.ESTIMATE)?.toDate()?.time ?: return null
        return GroupSession(
            id = id, squadId = squadId, startedByUid = getString("startedBy").orEmpty(), startedAtMs = startedAt,
            focusMin = (getLong("focusMin") ?: 25L).toInt(),
            participants = (get("participants") as? List<*>)?.filterIsInstance<String>().orEmpty().toSet(),
        )
    }

    private suspend fun profile(uid: String): SocialProfile? {
        val cached = profileCache[uid]
        if (cached != null && System.currentTimeMillis() - cached.first < PROFILE_TTL_MS) return cached.second
        val p = profiles().document(uid).get().await().toProfile()
        if (p != null) profileCache[uid] = System.currentTimeMillis() to p
        return p
    }

    // ---- profile ---------------------------------------------------------------------------------------

    override suspend fun upsertProfile(profile: SocialProfile): SocialResult<SocialProfile> = guarded {
        val me = me()
        var code: String? = ShareCode.normalize(profile.friendCode)
        var attempts = 0
        while (true) {
            val candidate = code ?: ShareCode.generate()
            val ref = db.collection("friendCodes").document(candidate)
            val snap = ref.get().await()
            if (!snap.exists()) { ref.set(mapOf("uid" to me)).await(); code = candidate; break }
            if (snap.getString("uid") == me) { code = candidate; break }
            code = null
            if (++attempts >= 5) fail(FailReason.NETWORK)
        }
        val saved = profile.copy(uid = me, friendCode = code!!)
        profiles().document(me).set(saved.toMap()).await()
        profileCache[me] = System.currentTimeMillis() to saved
        saved
    }

    // ---- squads ----------------------------------------------------------------------------------------

    override fun observeMySquad(): Flow<SquadView?> = currentUid().flatMapLatest { me ->
        if (me == null) {
            flowOf(null)
        } else {
            docFlow(users(me)).map { it?.getString("squadId") }.distinctUntilChanged().flatMapLatest { sid ->
                knownSquadId = sid
                if (sid == null) flowOf(null) else squadView(me, sid)
            }
        }
    }.catch { emit(null) }

    private fun squadView(me: String, sid: String): Flow<SquadView?> =
        combine(docFlow(squads().document(sid)), queryFlow(squads().document(sid).collection("presence")), ticker(30_000L)) { sq, pres, _ ->
            if (sq == null || !sq.exists()) {
                null
            } else {
                val squad = sq.toSquad()
                val presence = pres.documents.associate { it.id to it.toPresence() }
                val now = System.currentTimeMillis()
                val members = squad.memberUids.mapNotNull { id -> profile(id)?.let { MemberView(it, PresenceMath.effective(presence[id], now)) } }
                SquadView(squad, members, me)
            }
        }

    override suspend fun createSquad(name: String): SocialResult<Squad> = guarded {
        val me = me()
        val clean = name.trim().take(Squad.MAX_NAME)
        if (clean.length < 2) fail(FailReason.INVALID)
        repeat(5) {
            val code = ShareCode.generate()
            if (db.collection("squadCodes").document(code).get().await().exists()) return@repeat // taken: try another
            val ref = squads().document()
            val batch = db.batch()
            batch.set(ref, mapOf("name" to clean, "code" to code, "ownerUid" to me, "memberUids" to listOf(me)))
            batch.set(db.collection("squadCodes").document(code), mapOf("squadId" to ref.id))
            batch.set(users(me), mapOf("squadId" to ref.id), com.google.firebase.firestore.SetOptions.merge())
            batch.commit().await()
            knownSquadId = ref.id
            return@guarded Squad(ref.id, clean, code, me, listOf(me))
        }
        fail(FailReason.NETWORK)
    }

    override suspend fun joinSquad(code: String): SocialResult<Squad> = guarded {
        val me = me()
        val normal = ShareCode.normalize(code) ?: fail(FailReason.INVALID)
        val sid = db.collection("squadCodes").document(normal).get().await().getString("squadId") ?: fail(FailReason.NOT_FOUND)
        val ref = squads().document(sid)
        val squad = ref.get().await().takeIf { it.exists() }?.toSquad() ?: fail(FailReason.NOT_FOUND)
        if (me in squad.memberUids) fail(FailReason.ALREADY_MEMBER)
        if (squad.memberUids.size >= Squad.MAX_MEMBERS) fail(FailReason.FULL)
        ref.update("memberUids", FieldValue.arrayUnion(me)).await()
        users(me).set(mapOf("squadId" to sid), com.google.firebase.firestore.SetOptions.merge()).await()
        knownSquadId = sid
        squad.copy(memberUids = squad.memberUids + me)
    }

    override suspend fun leaveSquad(): SocialResult<Unit> = guarded {
        val me = me()
        val sid = knownSquadId ?: users(me).get().await().getString("squadId") ?: fail(FailReason.NOT_FOUND)
        val ref = squads().document(sid)
        val squad = ref.get().await().takeIf { it.exists() }?.toSquad()
        ref.collection("presence").document(me).delete().await()
        if (squad != null) {
            val remaining = squad.memberUids - me
            if (remaining.isEmpty()) {
                ref.collection("sessions").document("current").delete().await()
                db.collection("squadCodes").document(squad.code).delete().await()
                ref.delete().await()
            } else {
                val updates = mutableMapOf<String, Any>("memberUids" to FieldValue.arrayRemove(me))
                if (squad.ownerUid == me) updates["ownerUid"] = remaining.first() // hand the squad on
                ref.update(updates).await()
            }
        }
        users(me).set(mapOf("squadId" to FieldValue.delete()), com.google.firebase.firestore.SetOptions.merge()).await()
        knownSquadId = null
    }

    override suspend fun setPresence(presence: Presence): SocialResult<Unit> = guarded {
        val me = me()
        val sid = knownSquadId ?: users(me).get().await().getString("squadId") ?: fail(FailReason.NOT_FOUND)
        squads().document(sid).collection("presence").document(me)
            .set(mapOf("state" to presence.state.name, "focusEndsAtMs" to presence.focusEndsAtMs, "updatedAtMs" to presence.updatedAtMs)).await()
    }

    // ---- group sessions --------------------------------------------------------------------------------

    override fun observeGroupSession(): Flow<GroupSession?> = currentUid().flatMapLatest { me ->
        if (me == null) flowOf(null)
        else docFlow(users(me)).map { it?.getString("squadId") }.distinctUntilChanged().flatMapLatest { sid ->
            if (sid == null) flowOf(null)
            else docFlow(squads().document(sid).collection("sessions").document("current")).map { it?.toSession(sid) }
        }
    }.catch { emit(null) }

    override suspend fun startGroupSession(focusMin: Int): SocialResult<GroupSession> = guarded {
        val me = me()
        val sid = knownSquadId ?: users(me).get().await().getString("squadId") ?: fail(FailReason.NOT_FOUND)
        if (focusMin !in 5..120) fail(FailReason.INVALID)
        val ref = squads().document(sid).collection("sessions").document("current")
        ref.set(mapOf("startedBy" to me, "startedAt" to FieldValue.serverTimestamp(), "focusMin" to focusMin.toLong(), "participants" to listOf(me))).await()
        ref.get().await().toSession(sid) ?: fail(FailReason.NETWORK)
    }

    override suspend fun joinGroupSession(): SocialResult<Unit> = guarded {
        val me = me()
        val sid = knownSquadId ?: users(me).get().await().getString("squadId") ?: fail(FailReason.NOT_FOUND)
        squads().document(sid).collection("sessions").document("current").update("participants", FieldValue.arrayUnion(me)).await()
    }

    // ---- friends ---------------------------------------------------------------------------------------

    override fun observeFriends(): Flow<List<SocialProfile>> = currentUid().flatMapLatest { me ->
        if (me == null) flowOf(emptyList())
        else queryFlow(users(me).collection("friends")).map { snap -> snap.documents.mapNotNull { profile(it.id) }.sortedBy { it.displayName.lowercase() } }
    }.catch { emit(emptyList()) }

    override suspend fun addFriend(code: String): SocialResult<SocialProfile> = guarded {
        val me = me()
        val normal = ShareCode.normalize(code) ?: fail(FailReason.INVALID)
        val friendUid = db.collection("friendCodes").document(normal).get().await().getString("uid") ?: fail(FailReason.NOT_FOUND)
        if (friendUid == me) fail(FailReason.INVALID)
        val friend = profile(friendUid) ?: fail(FailReason.NOT_FOUND)
        val ref = users(me).collection("friends").document(friendUid)
        if (ref.get().await().exists()) fail(FailReason.ALREADY_MEMBER)
        ref.set(mapOf("addedAt" to FieldValue.serverTimestamp())).await()
        friend
    }

    override suspend fun removeFriend(uid: String): SocialResult<Unit> = guarded {
        users(me()).collection("friends").document(uid).delete().await()
    }

    // ---- leaderboards ----------------------------------------------------------------------------------

    override suspend fun submitWeeklyScore(weekKey: String, score: Int, profile: SocialProfile): SocialResult<Unit> = guarded {
        val me = me()
        if (!Leaderboard.isPlausible(score) || profile.hideFromLeaderboards) fail(FailReason.INVALID)
        val expire = Timestamp(java.util.Date(System.currentTimeMillis() + RETENTION_MS)) // set a Firestore TTL policy on "expireAt"
        entries(weekKey).document(me).set(
            mapOf(
                "displayName" to profile.displayName, "hero" to profile.hero.name, "rank" to profile.rank.name,
                "score" to score.toLong(), "updatedAt" to FieldValue.serverTimestamp(), "expireAt" to expire,
            ),
        ).await()
    }

    override suspend fun removeFromLeaderboards(weekKey: String): SocialResult<Unit> = guarded {
        entries(weekKey).document(me()).delete().await()
    }

    private fun DocumentSnapshot.toEntry(): LeaderboardEntry? {
        if (!exists()) return null
        val score = (getLong("score") ?: return null).toInt()
        if (!Leaderboard.isPlausible(score)) return null
        return LeaderboardEntry(
            uid = id, displayName = getString("displayName") ?: return null,
            hero = HeroTheme.entries.firstOrNull { it.name == getString("hero") } ?: HeroTheme.VECTOR,
            rank = Rank.entries.firstOrNull { it.name == getString("rank") } ?: Rank.RECRUIT, weeklyScore = score,
        )
    }

    override fun observeGlobalBoard(weekKey: String): Flow<List<LeaderboardEntry>> =
        queryFlow(entries(weekKey).orderBy("score", Query.Direction.DESCENDING).limit(GLOBAL_LIMIT))
            .map { snap -> Leaderboard.ranked(snap.documents.mapNotNull { it.toEntry() }) }
            .catch { emit(emptyList()) }

    override suspend fun friendsBoard(weekKey: String): SocialResult<List<LeaderboardEntry>> = guarded {
        val me = me()
        val friendIds = users(me).collection("friends").get().await().documents.map { it.id }
        val all = coroutineScope { (friendIds + me).map { id -> async { entries(weekKey).document(id).get().await().toEntry() } }.awaitAll() }
        Leaderboard.ranked(all.filterNotNull())
    }

    // ---- deletion --------------------------------------------------------------------------------------

    override suspend fun deleteMyData(): SocialResult<Unit> = guarded {
        val me = me()
        runCatching { leaveSquad() }
        val code = profiles().document(me).get().await().getString("friendCode")
        users(me).collection("friends").get().await().documents.forEach { it.reference.delete().await() }
        val today = LocalDate.now()
        for (weeks in 0L..8L) entries(WeekKey.of(today.minusWeeks(weeks))).document(me).delete().await()
        if (!code.isNullOrBlank()) db.collection("friendCodes").document(code).delete().await()
        profiles().document(me).delete().await()
        users(me).delete().await()
        profileCache.remove(me)
        knownSquadId = null
    }

    private companion object {
        const val PROFILE_TTL_MS = 60_000L
        const val GLOBAL_LIMIT = 50L
        const val RETENTION_MS = 60L * 24 * 60 * 60 * 1000
    }
}
