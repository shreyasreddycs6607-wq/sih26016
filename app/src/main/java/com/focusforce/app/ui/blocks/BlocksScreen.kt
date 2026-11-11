package com.focusforce.app.ui.blocks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusforce.app.R
import com.focusforce.app.domain.blocking.DetectionMode
import com.focusforce.app.domain.blocking.StrictLock
import com.focusforce.app.ui.components.AppIcon
import com.focusforce.app.ui.components.ButtonStyle
import com.focusforce.app.ui.components.ContentColumn
import com.focusforce.app.ui.components.FocusButton
import com.focusforce.app.ui.components.FocusCard
import com.focusforce.app.ui.components.FocusSegmented
import com.focusforce.app.ui.components.FocusSwitch
import com.focusforce.app.ui.components.RefreshWhileVisible
import com.focusforce.app.ui.components.ScreenTitle
import com.focusforce.app.ui.components.SectionLabel
import com.focusforce.app.ui.components.ShimmerBox
import com.focusforce.app.ui.theme.FocusTheme
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Blocks tab: master switch + detection status, guarded apps, passes, and the strict lock. */
@Composable
fun BlocksScreen(
    onOpenPicker: () -> Unit,
    onOpenWeb: () -> Unit,
    onOpenPermissions: () -> Unit,
    viewModel: BlocksViewModel = hiltViewModel(),
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    RefreshWhileVisible(intervalMs = 15_000L) { viewModel.refresh() }
    val c = FocusTheme.colors
    val now by produceState(System.currentTimeMillis()) {
        while (true) { delay(15_000L); value = System.currentTimeMillis() }
    }

    ContentColumn {
        ScreenTitle(stringResource(R.string.tab_blocks))
        val s = ui
        if (s == null) {
            ShimmerBox(Modifier.fillMaxWidth().height(140.dp))
            ShimmerBox(Modifier.fillMaxWidth().height(200.dp))
            return@ContentColumn
        }
        val strictActive = s.strictUntilMs > now

        s.notice?.let { res ->
            LaunchedEffect(res) { delay(5_000L); viewModel.clearNotice() }
            FocusCard(Modifier.fillMaxWidth(), selected = true) {
                Text(stringResource(res), style = FocusTheme.typography.body, color = c.danger)
            }
        }

        // master switch + how we detect the foreground app
        FocusCard(Modifier.fillMaxWidth()) {
            FocusSwitch(
                checked = s.enabled,
                onCheckedChange = viewModel::setEnabled,
                label = stringResource(R.string.blocks_master),
                description = stringResource(R.string.blocks_master_desc, s.blocked.size),
            )
            Spacer(Modifier.height(FocusTheme.spacing.sm))
            SectionLabel(stringResource(R.string.blocks_detection))
            Text(
                stringResource(
                    when (s.mode) {
                        DetectionMode.ACCESSIBILITY -> R.string.blocks_mode_accessibility
                        DetectionMode.USAGE_POLLING -> R.string.blocks_mode_polling
                        DetectionMode.NONE -> R.string.blocks_mode_none
                    },
                ),
                style = FocusTheme.typography.body,
                color = if (s.mode == DetectionMode.NONE) c.danger else c.textMuted,
            )
            if (s.mode != DetectionMode.ACCESSIBILITY) {
                Spacer(Modifier.height(FocusTheme.spacing.md))
                FocusButton(stringResource(R.string.blocks_fix_detection), onOpenPermissions, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary)
            }
        }

        // guarded apps
        FocusCard(Modifier.fillMaxWidth()) {
            SectionLabel(stringResource(R.string.blocks_apps_title, s.blocked.size))
            Spacer(Modifier.height(FocusTheme.spacing.sm))
            if (s.blocked.isEmpty()) {
                Text(stringResource(R.string.blocks_apps_empty), style = FocusTheme.typography.body, color = c.textMuted)
            } else {
                s.blocked.forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(row.packageName)
                        Text(row.label, style = FocusTheme.typography.body, color = c.text, modifier = Modifier.weight(1f).padding(start = FocusTheme.spacing.md))
                        FocusButton(stringResource(R.string.blocks_remove), { viewModel.remove(row.packageName) }, style = ButtonStyle.Ghost)
                    }
                }
            }
            Spacer(Modifier.height(FocusTheme.spacing.md))
            FocusButton(stringResource(R.string.blocks_add_apps), onOpenPicker, Modifier.fillMaxWidth())
        }

        // short videos + websites
        FocusCard(Modifier.fillMaxWidth()) {
            SectionLabel(stringResource(R.string.web_title))
            Spacer(Modifier.height(FocusTheme.spacing.sm))
            Text(stringResource(R.string.web_card_body), style = FocusTheme.typography.body, color = c.textMuted)
            Spacer(Modifier.height(FocusTheme.spacing.md))
            FocusButton(stringResource(R.string.web_open), onOpenWeb, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary)
        }

        // passes
        FocusCard(Modifier.fillMaxWidth()) {
            FocusSwitch(
                checked = s.passesEnabled,
                onCheckedChange = viewModel::setPasses,
                label = stringResource(R.string.blocks_passes),
                description = stringResource(R.string.blocks_passes_desc),
            )
        }

        StrictCard(strictActive, s.strictUntilMs, s.guardSettings, viewModel)
    }
}

@Composable
private fun StrictCard(active: Boolean, untilMs: Long, guard: Boolean, viewModel: BlocksViewModel) {
    val c = FocusTheme.colors
    var durationIndex by rememberSaveable { mutableStateOf(1) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    val labels = StrictLock.optionsMinutes.map { if (it < 60) stringResource(R.string.strict_minutes, it) else stringResource(R.string.strict_hours, it / 60) }
    val endsAt = Instant.ofEpochMilli(untilMs).atZone(ZoneId.systemDefault()).toLocalTime()
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

    FocusCard(Modifier.fillMaxWidth(), selected = active) {
        SectionLabel(stringResource(R.string.strict_title))
        Spacer(Modifier.height(FocusTheme.spacing.sm))
        if (active) {
            Text(stringResource(R.string.strict_active, endsAt), style = FocusTheme.typography.title, color = c.primary)
            Spacer(Modifier.height(FocusTheme.spacing.sm))
            Text(stringResource(R.string.strict_active_body), style = FocusTheme.typography.body, color = c.textMuted)
        } else {
            Text(stringResource(R.string.strict_explain), style = FocusTheme.typography.body, color = c.text)
        }
        Spacer(Modifier.height(FocusTheme.spacing.md))
        FocusSwitch(
            checked = guard,
            onCheckedChange = viewModel::setGuard,
            label = stringResource(R.string.strict_guard),
            description = stringResource(R.string.strict_guard_desc),
        )
        Spacer(Modifier.height(FocusTheme.spacing.md))
        if (!confirming) {
            FocusSegmented(labels, durationIndex, { durationIndex = it }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(FocusTheme.spacing.md))
            FocusButton(
                stringResource(if (active) R.string.strict_extend else R.string.strict_start),
                { confirming = true },
                Modifier.fillMaxWidth(),
                style = ButtonStyle.Secondary,
            )
        } else {
            Text(
                stringResource(R.string.strict_confirm_body, labels[durationIndex]),
                style = FocusTheme.typography.body,
                color = c.danger,
            )
            Spacer(Modifier.height(FocusTheme.spacing.md))
            FocusButton(
                stringResource(R.string.strict_confirm, labels[durationIndex]),
                { viewModel.startStrict(StrictLock.optionsMinutes[durationIndex]); confirming = false },
                Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(FocusTheme.spacing.sm))
            FocusButton(stringResource(R.string.strict_cancel), { confirming = false }, Modifier.fillMaxWidth(), style = ButtonStyle.Ghost)
        }
    }
}
