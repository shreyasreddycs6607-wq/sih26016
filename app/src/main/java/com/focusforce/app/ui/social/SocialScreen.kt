package com.focusforce.app.ui.social

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusforce.app.R
import com.focusforce.app.domain.social.GroupPomodoro
import com.focusforce.app.domain.social.GroupSession
import com.focusforce.app.domain.social.Leaderboard
import com.focusforce.app.domain.social.LeaderboardEntry
import com.focusforce.app.domain.social.PresenceState
import com.focusforce.app.domain.social.SocialProfile
import com.focusforce.app.ui.components.ButtonStyle
import com.focusforce.app.ui.components.ContentColumn
import com.focusforce.app.ui.components.FocusButton
import com.focusforce.app.ui.components.FocusCard
import com.focusforce.app.ui.components.FocusSegmented
import com.focusforce.app.ui.components.FocusSwitch
import com.focusforce.app.ui.components.FocusTextField
import com.focusforce.app.ui.components.RefreshWhileVisible
import com.focusforce.app.ui.components.ScreenTopBar
import com.focusforce.app.ui.components.SectionLabel
import com.focusforce.app.ui.components.ThemedBackground
import com.focusforce.app.ui.theme.FocusTheme
import com.focusforce.app.ui.theme.nameRes
import kotlinx.coroutines.delay

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

private fun share(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Squads, friends, weekly leaderboards and privacy. */
@Composable
fun SocialScreen(onBack: () -> Unit, viewModel: SocialViewModel = hiltViewModel()) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    RefreshWhileVisible(intervalMs = 60_000L) { viewModel.refresh() }
    val c = FocusTheme.colors
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(R.string.social_tab_squad, R.string.social_tab_friends, R.string.social_tab_board, R.string.social_tab_privacy).map { stringResource(it) }

    ThemedBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            ScreenTopBar(stringResource(R.string.social_title), onBack)
            ContentColumn {
                ui.notice?.let { res ->
                    LaunchedEffect(res) { delay(6_000L); viewModel.clearNotice() }
                    FocusCard(Modifier.fillMaxWidth(), selected = true) { Text(stringResource(res), style = FocusTheme.typography.body, color = c.danger) }
                }
                when (ui.mode) {
                    SocialMode.UNAVAILABLE -> {
                        FocusCard(Modifier.fillMaxWidth()) {
                            SectionLabel(stringResource(R.string.social_unavailable_title))
                            Spacer(Modifier.height(FocusTheme.spacing.sm))
                            Text(stringResource(R.string.social_unavailable_body), style = FocusTheme.typography.body, color = c.text)
                        }
                        SharedInfoCard()
                    }
                    SocialMode.SIGNED_OUT -> {
                        if (ui.simulated) SimulationBanner()
                        FocusCard(Modifier.fillMaxWidth()) {
                            SectionLabel(stringResource(R.string.social_signin_title))
                            Spacer(Modifier.height(FocusTheme.spacing.sm))
                            Text(stringResource(R.string.social_signin_body), style = FocusTheme.typography.body, color = c.text)
                            Spacer(Modifier.height(FocusTheme.spacing.md))
                            FocusButton(
                                stringResource(R.string.social_signin),
                                { context.findActivity()?.let(viewModel::signIn) },
                                Modifier.fillMaxWidth(),
                                enabled = !ui.busy,
                            )
                        }
                        SharedInfoCard()
                    }
                    SocialMode.SIGNED_IN -> {
                        if (ui.simulated) SimulationBanner()
                        FocusSegmented(tabs, tab, { tab = it }, Modifier.fillMaxWidth())
                        when (tab) {
                            0 -> SquadTab(ui, viewModel, context)
                            1 -> FriendsTab(ui, viewModel, context)
                            2 -> BoardTab(ui)
                            else -> PrivacyTab(ui, viewModel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SimulationBanner() {
    FocusCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.social_sim_banner), style = FocusTheme.typography.label, color = FocusTheme.colors.secondary)
    }
}

/** Plain-language list of everything multiplayer shares. Shown before sign-in and again in the Privacy tab. */
@Composable
private fun SharedInfoCard() {
    FocusCard(Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.social_shared_title))
        Spacer(Modifier.height(FocusTheme.spacing.sm))
        Text(stringResource(R.string.social_shared_body), style = FocusTheme.typography.body, color = FocusTheme.colors.textMuted)
    }
}

@Composable
private fun SquadTab(ui: SocialUi, vm: SocialViewModel, context: Context) {
    val c = FocusTheme.colors
    var name by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    val view = ui.squad
    if (view == null) {
        FocusCard(Modifier.fillMaxWidth()) {
            SectionLabel(stringResource(R.string.squad_create_title))
            Spacer(Modifier.height(FocusTheme.spacing.sm))
            FocusTextField(name, { name = it }, stringResource(R.string.squad_name_hint))
            Spacer(Modifier.height(FocusTheme.spacing.sm))
            FocusButton(stringResource(R.string.squad_create), { vm.createSquad(name) }, Modifier.fillMaxWidth(), enabled = name.trim().length >= 2 && !ui.busy)
        }
        FocusCard(Modifier.fillMaxWidth()) {
            SectionLabel(stringResource(R.string.squad_join_title))
            Spacer(Modifier.height(FocusTheme.spacing.sm))
            FocusTextField(code, { code = it }, stringResource(R.string.squad_code_hint))
            if (ui.simulated) Text(stringResource(R.string.squad_sim_code), style = FocusTheme.typography.label, color = c.textMuted)
            Spacer(Modifier.height(FocusTheme.spacing.sm))
            FocusButton(stringResource(R.string.squad_join), { vm.joinSquad(code) }, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary, enabled = code.isNotBlank() && !ui.busy)
        }
        return
    }

    FocusCard(Modifier.fillMaxWidth()) {
        SectionLabel(view.squad.name)
        Spacer(Modifier.height(FocusTheme.spacing.sm))
        Text(stringResource(R.string.squad_code_label, view.squad.code), style = FocusTheme.typography.numeral, color = c.primary)
        Text(stringResource(R.string.squad_members_count, view.squad.memberUids.size, com.focusforce.app.domain.social.Squad.MAX_MEMBERS), style = FocusTheme.typography.label, color = c.textMuted)
        Spacer(Modifier.height(FocusTheme.spacing.sm))
        FocusButton(
            stringResource(R.string.squad_share),
            { share(context, context.getString(R.string.squad_share_text, view.squad.name, view.squad.code)) },
            Modifier.fillMaxWidth(), style = ButtonStyle.Secondary,
        )
    }
    FocusCard(Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.squad_members_title))
        Spacer(Modifier.height(FocusTheme.spacing.sm))
        view.members.forEach { m ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (m.profile.uid == view.me) stringResource(R.string.social_you, m.profile.displayName) else m.profile.displayName, style = FocusTheme.typography.body, color = c.text)
                    Text(
                        stringResource(R.string.social_member_line, stringResource(m.profile.rank.nameRes), m.profile.level, stringResource(m.profile.hero.nameRes)),
                        style = FocusTheme.typography.label, color = c.textMuted,
                    )
                }
                Text(
                    stringResource(if (m.presence == PresenceState.FOCUSING) R.string.presence_focusing else R.string.presence_idle),
                    style = FocusTheme.typography.label,
                    color = if (m.presence == PresenceState.FOCUSING) c.success else c.textMuted,
                )
            }
        }
    }
    GroupSessionCard(ui, vm, view.me)
    FocusButton(stringResource(R.string.squad_leave), vm::leaveSquad, Modifier.fillMaxWidth(), style = ButtonStyle.Ghost, enabled = !ui.busy)
}

@Composable
private fun GroupSessionCard(ui: SocialUi, vm: SocialViewModel, me: String) {
    val c = FocusTheme.colors
    val session: GroupSession? = ui.session
    val now = System.currentTimeMillis()
    val active = session != null && GroupPomodoro.endMs(session) > now
    FocusCard(Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.group_session_title))
        Spacer(Modifier.height(FocusTheme.spacing.sm))
        if (session != null && active) {
            val minLeft = ((GroupPomodoro.endMs(session) - now) / 60_000L).toInt()
            Text(stringResource(R.string.group_session_running, session.focusMin, session.participants.size, minLeft), style = FocusTheme.typography.body, color = c.text)
            if (me !in session.participants && GroupPomodoro.isJoinable(session, now)) {
                Spacer(Modifier.height(FocusTheme.spacing.sm))
                FocusButton(stringResource(R.string.group_session_join), { vm.joinGroup(session) }, Modifier.fillMaxWidth(), enabled = !ui.busy)
            } else if (me in session.participants) {
                Text(stringResource(R.string.group_session_joined), style = FocusTheme.typography.label, color = c.success)
            }
        } else {
            Text(stringResource(R.string.group_session_idle), style = FocusTheme.typography.body, color = c.textMuted)
            Spacer(Modifier.height(FocusTheme.spacing.sm))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FocusTheme.spacing.sm)) {
                listOf(25, 50).forEach { min ->
                    FocusButton(stringResource(R.string.group_session_start, min), { vm.startGroup(min) }, Modifier.weight(1f), style = ButtonStyle.Secondary, enabled = !ui.busy)
                }
            }
        }
    }
}

@Composable
private fun FriendsTab(ui: SocialUi, vm: SocialViewModel, context: Context) {
    val c = FocusTheme.colors
    var code by rememberSaveable { mutableStateOf("") }
    FocusCard(Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.friends_my_code))
        Spacer(Modifier.height(FocusTheme.spacing.sm))
        if (ui.settings.friendCode.isBlank()) {
            Text(stringResource(R.string.friends_code_pending), style = FocusTheme.typography.body, color = c.textMuted)
        } else {
            Text(ui.settings.friendCode, style = FocusTheme.typography.numeralLarge, color = c.primary)
            Spacer(Modifier.height(FocusTheme.spacing.sm))
            FocusButton(
                stringResource(R.string.squad_share),
                { share(context, context.getString(R.string.friends_share_text, ui.settings.friendCode)) },
                Modifier.fillMaxWidth(), style = ButtonStyle.Secondary,
            )
        }
    }
    FocusCard(Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.friends_add_title))
        Spacer(Modifier.height(FocusTheme.spacing.sm))
        FocusTextField(code, { code = it }, stringResource(R.string.squad_code_hint))
        Spacer(Modifier.height(FocusTheme.spacing.sm))
        FocusButton(stringResource(R.string.friends_add), { vm.addFriend(code); code = "" }, Modifier.fillMaxWidth(), enabled = code.isNotBlank() && !ui.busy)
    }
    FocusCard(Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.friends_title, ui.friends.size))
        Spacer(Modifier.height(FocusTheme.spacing.sm))
        if (ui.friends.isEmpty()) Text(stringResource(R.string.friends_empty), style = FocusTheme.typography.body, color = c.textMuted)
        ui.friends.forEach { f: SocialProfile ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(f.displayName, style = FocusTheme.typography.body, color = c.text)
                    Text(stringResource(R.string.social_member_line, stringResource(f.rank.nameRes), f.level, stringResource(f.hero.nameRes)), style = FocusTheme.typography.label, color = c.textMuted)
                }
                FocusButton(stringResource(R.string.blocks_remove), { vm.removeFriend(f.uid) }, style = ButtonStyle.Ghost)
            }
        }
    }
}

@Composable
private fun BoardTab(ui: SocialUi) {
    val c = FocusTheme.colors
    var scope by rememberSaveable { mutableIntStateOf(0) }
    FocusSegmented(listOf(stringResource(R.string.board_friends), stringResource(R.string.board_global)), scope, { scope = it }, Modifier.fillMaxWidth())
    val entries: List<LeaderboardEntry>? = if (scope == 0) ui.friendsBoard else ui.global
    val me = ui.me?.uid.orEmpty()
    FocusCard(Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.board_title, ui.weekKey))
        Spacer(Modifier.height(FocusTheme.spacing.sm))
        when {
            ui.settings.hideFromLeaderboards -> Text(stringResource(R.string.board_hidden), style = FocusTheme.typography.body, color = c.secondary)
            entries == null -> Text(stringResource(R.string.board_loading), style = FocusTheme.typography.body, color = c.textMuted)
            entries.isEmpty() -> Text(stringResource(R.string.board_empty), style = FocusTheme.typography.body, color = c.textMuted)
        }
        entries?.let { list ->
            Leaderboard.positionOf(list, me)?.let {
                Text(stringResource(R.string.board_you_are, it), style = FocusTheme.typography.title, color = c.primary)
                Spacer(Modifier.height(FocusTheme.spacing.sm))
            }
            list.forEach { e ->
                val position = Leaderboard.positionOf(list, e.uid) ?: 0
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("#$position", style = FocusTheme.typography.numeral, color = if (e.uid == me) c.primary else c.textMuted, modifier = Modifier.padding(end = FocusTheme.spacing.md))
                    Column(Modifier.weight(1f)) {
                        Text(if (e.uid == me) stringResource(R.string.social_you, e.displayName) else e.displayName, style = FocusTheme.typography.body, color = c.text)
                        Text(stringResource(R.string.social_board_line, stringResource(e.rank.nameRes), stringResource(e.hero.nameRes)), style = FocusTheme.typography.label, color = c.textMuted)
                    }
                    Text(stringResource(R.string.board_score, e.weeklyScore), style = FocusTheme.typography.numeral, color = c.text)
                }
            }
        }
        Spacer(Modifier.height(FocusTheme.spacing.sm))
        Text(stringResource(R.string.board_trust_note), style = FocusTheme.typography.label, color = c.textMuted)
    }
}

@Composable
private fun PrivacyTab(ui: SocialUi, vm: SocialViewModel) {
    val c = FocusTheme.colors
    var name by rememberSaveable(ui.settings.displayName, ui.me?.displayName) { mutableStateOf(ui.settings.displayName ?: ui.me?.displayName.orEmpty()) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    FocusCard(Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.privacy_name_title))
        Spacer(Modifier.height(FocusTheme.spacing.sm))
        FocusTextField(name, { name = it }, stringResource(R.string.editor_name))
        Spacer(Modifier.height(FocusTheme.spacing.sm))
        FocusButton(stringResource(R.string.action_save), { vm.saveName(name) }, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary)
    }
    FocusCard(Modifier.fillMaxWidth()) {
        FocusSwitch(ui.settings.sharePresence, vm::setSharePresence, stringResource(R.string.privacy_presence), description = stringResource(R.string.privacy_presence_desc))
        Spacer(Modifier.height(FocusTheme.spacing.sm))
        FocusSwitch(ui.settings.hideFromLeaderboards, vm::setHide, stringResource(R.string.privacy_hide), description = stringResource(R.string.privacy_hide_desc))
    }
    SharedInfoCard()
    FocusButton(stringResource(R.string.social_signout), vm::signOut, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary)
    FocusCard(Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.privacy_delete_title))
        Spacer(Modifier.height(FocusTheme.spacing.sm))
        Text(stringResource(R.string.privacy_delete_body), style = FocusTheme.typography.body, color = c.textMuted)
        Spacer(Modifier.height(FocusTheme.spacing.md))
        if (!confirmDelete) {
            FocusButton(stringResource(R.string.privacy_delete), { confirmDelete = true }, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary)
        } else {
            Text(stringResource(R.string.privacy_delete_confirm), style = FocusTheme.typography.body, color = c.danger)
            Spacer(Modifier.height(FocusTheme.spacing.sm))
            FocusButton(stringResource(R.string.privacy_delete_yes), { vm.deleteEverything(); confirmDelete = false }, Modifier.fillMaxWidth(), enabled = !ui.busy)
            Spacer(Modifier.height(FocusTheme.spacing.sm))
            FocusButton(stringResource(R.string.strict_cancel), { confirmDelete = false }, Modifier.fillMaxWidth(), style = ButtonStyle.Ghost)
        }
    }
}
