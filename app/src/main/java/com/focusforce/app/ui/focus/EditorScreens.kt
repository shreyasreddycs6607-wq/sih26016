package com.focusforce.app.ui.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusforce.app.R
import com.focusforce.app.domain.limits.LimitKind
import com.focusforce.app.domain.limits.LimitMath
import com.focusforce.app.domain.schedule.SchedulePreset
import com.focusforce.app.data.apps.AppInfoProvider
import com.focusforce.app.ui.components.AppIcon
import com.focusforce.app.ui.components.ButtonStyle
import com.focusforce.app.ui.components.ContentColumn
import com.focusforce.app.ui.components.DayPicker
import com.focusforce.app.ui.components.FocusButton
import com.focusforce.app.ui.components.FocusCard
import com.focusforce.app.ui.components.FocusSegmented
import com.focusforce.app.ui.components.FocusSwitch
import com.focusforce.app.ui.components.FocusTextField
import com.focusforce.app.ui.components.LocalAppInfoProvider
import com.focusforce.app.ui.components.ScreenTopBar
import com.focusforce.app.ui.components.SectionLabel
import com.focusforce.app.ui.components.ShimmerBox
import com.focusforce.app.ui.components.StepperRow
import com.focusforce.app.ui.components.ThemedBackground
import com.focusforce.app.ui.theme.FocusTheme
import com.focusforce.app.ui.util.durationText
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.delay

@Composable
private fun EditorShell(
    title: String,
    onBack: () -> Unit,
    vm: EditorViewModel,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val notice by vm.notice.collectAsStateWithLifecycle()
    val finished by vm.finished.collectAsStateWithLifecycle()
    LaunchedEffect(finished) { if (finished) onBack() }
    ThemedBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            ScreenTopBar(title, onBack)
            ContentColumn {
                notice?.let { res ->
                    LaunchedEffect(res) { delay(5_000L); vm.clearNotice() }
                    FocusCard(Modifier.fillMaxWidth(), selected = true) { Text(stringResource(res), style = FocusTheme.typography.body, color = FocusTheme.colors.danger) }
                }
                content()
            }
        }
    }
}

private fun clock(min: Int): String = LocalTime.of(min / 60, min % 60).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

@Composable
fun ScheduleEditorScreen(onBack: () -> Unit, onPickApps: () -> Unit, vm: ScheduleEditorViewModel = hiltViewModel()) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val packages by vm.packages.collectAsStateWithLifecycle()
    val c = FocusTheme.colors
    val presetNames = mapOf(
        SchedulePreset.STUDY to stringResource(R.string.preset_study),
        SchedulePreset.SLEEP to stringResource(R.string.preset_sleep),
        SchedulePreset.WORK to stringResource(R.string.preset_work),
        SchedulePreset.FAMILY to stringResource(R.string.preset_family),
    )
    EditorShell(stringResource(if (vm.isNew) R.string.sched_new else R.string.sched_edit), onBack, vm) {
        val d = draft
        if (d == null) { ShimmerBox(Modifier.fillMaxWidth().height(240.dp)); return@EditorShell }
        FocusCard(Modifier.fillMaxWidth()) {
            SectionLabel(stringResource(R.string.sched_presets))
            Spacer(Modifier.height(FocusTheme.spacing.sm))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FocusTheme.spacing.xs)) {
                SchedulePreset.entries.forEach { p ->
                    FocusButton(presetNames.getValue(p), { vm.applyPreset(p, presetNames.getValue(p)) }, Modifier.weight(1f), style = ButtonStyle.Secondary)
                }
            }
            Spacer(Modifier.height(FocusTheme.spacing.md))
            FocusTextField(d.name, vm::setName, stringResource(R.string.editor_name))
            Spacer(Modifier.height(FocusTheme.spacing.md))
            SectionLabel(stringResource(R.string.editor_days))
            Spacer(Modifier.height(FocusTheme.spacing.sm))
            DayPicker(d.daysMask, vm::setDays)
            Spacer(Modifier.height(FocusTheme.spacing.md))
            StepperRow(stringResource(R.string.sched_start), clock(d.startMin), { vm.shiftStart(-15) }, { vm.shiftStart(15) })
            StepperRow(stringResource(R.string.sched_end), clock(d.endMin), { vm.shiftEnd(-15) }, { vm.shiftEnd(15) })
            if (d.endMin < d.startMin) Text(stringResource(R.string.sched_overnight), style = FocusTheme.typography.label, color = c.textMuted)
        }
        FocusCard(Modifier.fillMaxWidth()) {
            SectionLabel(stringResource(R.string.sched_apps))
            Spacer(Modifier.height(FocusTheme.spacing.sm))
            Text(stringResource(R.string.sched_apps_count, packages.size), style = FocusTheme.typography.body, color = c.text)
            Spacer(Modifier.height(FocusTheme.spacing.sm))
            FocusButton(stringResource(R.string.sched_choose_apps), onPickApps, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary)
            Spacer(Modifier.height(FocusTheme.spacing.md))
            SectionLabel(stringResource(R.string.sched_sites))
            Spacer(Modifier.height(FocusTheme.spacing.sm))
            FocusTextField(d.sites, vm::setSites, stringResource(R.string.sched_sites_hint))
            Spacer(Modifier.height(FocusTheme.spacing.xs))
            Text(stringResource(R.string.sched_sites_note), style = FocusTheme.typography.label, color = c.textMuted)
        }
        FocusCard(Modifier.fillMaxWidth()) {
            FocusSwitch(d.silence, vm::setSilence, stringResource(R.string.sched_silence), description = stringResource(R.string.sched_silence_desc))
        }
        FocusButton(stringResource(R.string.action_save), vm::save, Modifier.fillMaxWidth())
        if (!vm.isNew) FocusButton(stringResource(R.string.action_delete), vm::delete, Modifier.fillMaxWidth(), style = ButtonStyle.Ghost)
    }
}

@Composable
fun LimitEditorScreen(onBack: () -> Unit, onPickApp: () -> Unit, vm: LimitEditorViewModel = hiltViewModel()) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val chosen by vm.chosenApp.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val c = FocusTheme.colors
    val info = LocalAppInfoProvider.current
    EditorShell(stringResource(if (vm.isNew) R.string.limit_new else R.string.limit_edit), onBack, vm) {
        val d = draft
        if (d == null) { ShimmerBox(Modifier.fillMaxWidth().height(240.dp)); return@EditorShell }
        FocusCard(Modifier.fillMaxWidth()) {
            FocusSegmented(
                listOf(stringResource(R.string.limit_kind_app), stringResource(R.string.limit_kind_group)),
                d.kind.ordinal, { vm.setKind(LimitKind.entries[it]) }, Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(FocusTheme.spacing.md))
            if (d.kind == LimitKind.APP) {
                val app = chosen
                if (app != null) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FocusTheme.spacing.md)) {
                        AppIcon(app)
                        Text(info.label(app), style = FocusTheme.typography.title, color = c.text)
                    }
                    Spacer(Modifier.height(FocusTheme.spacing.sm))
                }
                FocusButton(stringResource(if (app == null) R.string.limit_choose_app else R.string.limit_change_app), onPickApp, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary)
            } else if (groups.isEmpty()) {
                Text(stringResource(R.string.limit_no_groups), style = FocusTheme.typography.body, color = c.textMuted)
            } else {
                groups.forEach { g ->
                    FocusButton(
                        stringResource(R.string.group_row, g.name, g.packages.size),
                        { vm.setGroup(g.id) },
                        Modifier.fillMaxWidth(),
                        style = if (d.groupId == g.id) ButtonStyle.Primary else ButtonStyle.Secondary,
                    )
                    Spacer(Modifier.height(FocusTheme.spacing.xs))
                }
            }
        }
        FocusCard(Modifier.fillMaxWidth()) {
            StepperRow(
                stringResource(R.string.limit_per_day), durationText(d.limitMs), { vm.shiftLimit(-1) }, { vm.shiftLimit(1) },
                minusEnabled = d.limitMs > LimitMath.MIN_LIMIT_MS, plusEnabled = d.limitMs < LimitMath.MAX_LIMIT_MS,
            )
            Spacer(Modifier.height(FocusTheme.spacing.sm))
            SectionLabel(stringResource(R.string.editor_days))
            Spacer(Modifier.height(FocusTheme.spacing.sm))
            DayPicker(d.daysMask, vm::setDays)
            Spacer(Modifier.height(FocusTheme.spacing.sm))
            Text(stringResource(R.string.limit_explain), style = FocusTheme.typography.label, color = c.textMuted)
        }
        FocusButton(stringResource(R.string.action_save), vm::save, Modifier.fillMaxWidth())
        if (!vm.isNew) FocusButton(stringResource(R.string.action_delete), vm::delete, Modifier.fillMaxWidth(), style = ButtonStyle.Ghost)
    }
}

@Composable
fun GroupEditorScreen(onBack: () -> Unit, onPickApps: () -> Unit, vm: GroupEditorViewModel = hiltViewModel()) {
    val name by vm.name.collectAsStateWithLifecycle()
    val packages by vm.packages.collectAsStateWithLifecycle()
    val c = FocusTheme.colors
    EditorShell(stringResource(if (vm.isNew) R.string.group_new else R.string.group_edit), onBack, vm) {
        val n = name
        if (n == null) { ShimmerBox(Modifier.fillMaxWidth().height(160.dp)); return@EditorShell }
        FocusCard(Modifier.fillMaxWidth()) {
            FocusTextField(n, vm::setName, stringResource(R.string.editor_name))
            Spacer(Modifier.height(FocusTheme.spacing.md))
            Text(stringResource(R.string.sched_apps_count, packages.size), style = FocusTheme.typography.body, color = c.text)
            Spacer(Modifier.height(FocusTheme.spacing.sm))
            FocusButton(stringResource(R.string.sched_choose_apps), onPickApps, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary)
            Spacer(Modifier.height(FocusTheme.spacing.sm))
            Text(stringResource(R.string.group_explain), style = FocusTheme.typography.label, color = c.textMuted)
        }
        FocusButton(stringResource(R.string.action_save), vm::save, Modifier.fillMaxWidth())
        if (!vm.isNew) FocusButton(stringResource(R.string.action_delete), vm::delete, Modifier.fillMaxWidth(), style = ButtonStyle.Ghost)
    }
}
