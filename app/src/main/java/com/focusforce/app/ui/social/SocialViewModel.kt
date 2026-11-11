package com.focusforce.app.ui.social

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforce.app.R
import com.focusforce.app.data.focus.PomodoroController
import com.focusforce.app.data.social.GameState
import com.focusforce.app.data.social.GameStateRepository
import com.focusforce.app.data.social.SimulatedSocialBackend
import com.focusforce.app.data.social.SocialPrefs
import com.focusforce.app.data.social.SocialSettings
import com.focusforce.app.data.social.SocialSync
import com.focusforce.app.domain.blocking.DetectionMode
import com.focusforce.app.domain.game.WeekKey
import com.focusforce.app.domain.social.AuthService
import com.focusforce.app.domain.social.AuthUser
import com.focusforce.app.domain.social.DisplayName
import com.focusforce.app.domain.social.FailReason
import com.focusforce.app.domain.social.GroupPomodoro
import com.focusforce.app.domain.social.GroupSession
import com.focusforce.app.domain.social.LeaderboardEntry
import com.focusforce.app.domain.social.SocialBackend
import com.focusforce.app.domain.social.SocialProfile
import com.focusforce.app.domain.social.SocialResult
import com.focusforce.app.domain.social.SquadView
import com.focusforce.app.permissions.PermissionChecker
import com.focusforce.app.permissions.PermissionKind
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SocialMode { UNAVAILABLE, SIGNED_OUT, SIGNED_IN }

data class SocialUi(
    val mode: SocialMode,
    /** Debug builds without Firebase use an in-memory simulation with invented players. */
    val simulated: Boolean,
    val me: AuthUser?,
    val settings: SocialSettings,
    val game: GameState?,
    val squad: SquadView?,
    val session: GroupSession?,
    val friends: List<SocialProfile>,
    val global: List<LeaderboardEntry>,
    val friendsBoard: List<LeaderboardEntry>?,
    val weekKey: String,
    val busy: Boolean,
    val notice: Int?,
)

private data class Live(val squad: SquadView? = null, val session: GroupSession? = null, val friends: List<SocialProfile> = emptyList(), val global: List<LeaderboardEntry> = emptyList())
private data class Extras(val friendsBoard: List<LeaderboardEntry>?, val busy: Boolean, val notice: Int?)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class SocialViewModel @Inject constructor(
    private val auth: AuthService,
    private val backend: SocialBackend,
    private val prefs: SocialPrefs,
    game: GameStateRepository,
    private val sync: SocialSync,
    private val pomodoro: PomodoroController,
    private val permissions: PermissionChecker,
    private val entitlement: com.focusforce.app.data.billing.EntitlementRepository,
) : ViewModel() {

    private val week = WeekKey.of(LocalDate.now())
    private val friendsBoard = MutableStateFlow<List<LeaderboardEntry>?>(null)
    private val busy = MutableStateFlow(false)
    private val notice = MutableStateFlow<Int?>(null)

    private val live = auth.user.flatMapLatest { user ->
        if (user == null || !backend.available) flowOf(Live())
        else combine(backend.observeMySquad(), backend.observeGroupSession(), backend.observeFriends(), backend.observeGlobalBoard(week)) { s, g, f, b -> Live(s, g, f, b) }
    }

    private val extras = combine(friendsBoard, busy, notice, ::Extras)

    val state: StateFlow<SocialUi> = combine(auth.user, prefs.settings, game.observe(), live, extras) { user, settings, gs, l, x ->
        SocialUi(
            mode = when { !auth.available -> SocialMode.UNAVAILABLE; user == null -> SocialMode.SIGNED_OUT; else -> SocialMode.SIGNED_IN },
            simulated = backend is SimulatedSocialBackend,
            me = user, settings = settings, game = gs, squad = l.squad, session = l.session, friends = l.friends, global = l.global,
            friendsBoard = x.friendsBoard, weekKey = week, busy = x.busy, notice = x.notice,
        )
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000),
        SocialUi(SocialMode.UNAVAILABLE, false, null, SocialSettings(), null, null, null, emptyList(), emptyList(), null, week, false, null),
    )

    fun clearNotice() { notice.value = null }

    /** Pushes the latest profile/presence/score and refreshes the friends board. Call when the screen opens. */
    fun refresh() {
        viewModelScope.launch {
            sync.tick(force = true)
            if (auth.available) (backend.friendsBoard(week) as? SocialResult.Ok)?.let { friendsBoard.value = it.value }
        }
    }

    private fun failText(reason: FailReason): Int? = when (reason) {
        FailReason.NOT_AVAILABLE -> R.string.social_err_unavailable
        FailReason.NOT_SIGNED_IN -> R.string.social_err_signin
        FailReason.NOT_FOUND -> R.string.social_err_not_found
        FailReason.FULL -> R.string.social_err_full
        FailReason.ALREADY_MEMBER -> R.string.social_err_already
        FailReason.NETWORK -> R.string.social_err_network
        FailReason.INVALID -> R.string.social_err_invalid
        FailReason.DENIED -> R.string.social_err_denied
        FailReason.CANCELLED -> null
    }

    private fun <T> act(block: suspend () -> SocialResult<T>, onOk: suspend (T) -> Unit = {}) {
        viewModelScope.launch {
            busy.value = true
            when (val r = block()) {
                is SocialResult.Ok -> onOk(r.value)
                is SocialResult.Fail -> notice.value = failText(r.reason)
            }
            busy.value = false
        }
    }

    fun signIn(activity: Activity) = act({ auth.signIn(activity) }) { sync.tick(force = true); refresh() }

    fun signOut() {
        viewModelScope.launch { auth.signOut(); sync.reset(); friendsBoard.value = null }
    }

    fun createSquad(name: String) = act({ backend.createSquad(name) }) { sync.tick(force = true) }
    fun joinSquad(code: String) = act({ backend.joinSquad(code) }) { sync.tick(force = true) }
    fun leaveSquad() = act({ backend.leaveSquad() })

    private fun detectionOk() = DetectionMode.from(
        permissions.isGranted(PermissionKind.ACCESSIBILITY), permissions.isGranted(PermissionKind.USAGE_ACCESS), permissions.isGranted(PermissionKind.OVERLAY),
    ) != DetectionMode.NONE

    fun startGroup(minutes: Int) {
        if (!com.focusforce.app.domain.pro.ProPolicy.hasFeature(com.focusforce.app.domain.pro.ProFeature.GROUP_FOCUS, entitlement.isPro())) { notice.value = R.string.notice_pro_required; return }
        if (!detectionOk()) { notice.value = R.string.notice_need_detection; return }
        act({ backend.startGroupSession(minutes) }) { session ->
            if (!pomodoro.startGroup(session)) notice.value = R.string.social_group_too_late
        }
    }

    fun joinGroup(session: GroupSession) {
        if (!detectionOk()) { notice.value = R.string.notice_need_detection; return }
        if (!GroupPomodoro.isJoinable(session, System.currentTimeMillis())) { notice.value = R.string.social_group_too_late; return }
        act({ backend.joinGroupSession() }) {
            if (!pomodoro.startGroup(session)) notice.value = R.string.social_group_too_late
        }
    }

    fun addFriend(code: String) = act({ backend.addFriend(code) }) { refresh() }
    fun removeFriend(uid: String) = act({ backend.removeFriend(uid) }) { refresh() }

    fun saveName(raw: String) {
        val clean = DisplayName.sanitize(raw)
        if (clean == null) { notice.value = R.string.social_err_name; return }
        viewModelScope.launch { prefs.setDisplayName(clean); sync.tick(force = true) }
    }

    fun setSharePresence(on: Boolean) { viewModelScope.launch { prefs.setSharePresence(on); sync.tick(force = true) } }
    fun setHide(on: Boolean) { viewModelScope.launch { prefs.setHideFromLeaderboards(on); sync.tick(force = true); refresh() } }

    /** Removes everything this app stored on the server for you, then the sign-in account, then the local copy of the settings. */
    fun deleteEverything() {
        act({ backend.deleteMyData() }) {
            val deleted = auth.deleteAccount()
            if (deleted is SocialResult.Fail) notice.value = R.string.social_delete_signin_again
            auth.signOut()
            prefs.clear()
            sync.reset()
            friendsBoard.value = null
        }
    }
}
