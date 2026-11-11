package com.focusforce.app.ui.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusforce.app.R
import com.focusforce.app.domain.blocking.DetectionMode
import com.focusforce.app.domain.limits.DaysMask
import com.focusforce.app.domain.limits.LimitKind
import com.focusforce.app.domain.pomodoro.PomodoroConfig
import com.focusforce.app.domain.pomodoro.PomodoroPhase
import com.focusforce.app.domain.schedule.FocusSchedule
import com.focusforce.app.permissions.PermissionKind
import com.focusforce.app.permissions.PermissionLauncher
import com.focusforce.app.ui.components.AppIcon
import com.focusforce.app.ui.components.ButtonStyle
import com.focusforce.app.ui.components.ContentColumn
import com.focusforce.app.ui.components.FocusButton
import com.focusforce.app.ui.components.FocusCard
import com.focusforce.app.ui.components.FocusProgressBar
import com.focusforce.app.ui.components.FocusSwitch
import com.focusforce.app.ui.components.HeroMoment
import com.focusforce.app.ui.components.RefreshWhileVisible
import com.focusforce.app.ui.components.ScreenTitle
import com.focusforce.app.ui.components.SectionLabel
import com.focusforce.app.ui.components.ShimmerBox
import com.focusforce.app.ui.components.StatRing
import com.focusforce.app.ui.components.StepperRow
import com.focusforce.app.ui.theme.FocusTheme
import com.focusforce.app.ui.util.durationText
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.delay

/** Focus tab: Pomodoro, focus schedules, daily limits (per app and per group) and mindful nudges. */
@Composable
fun FocusScreen(
    onOpenPermissions: () -> Unit,
    onOpenSchedule: (Long) -> Unit,
    onOpenLimit: (Long) -> Unit,
    onOpenGroup: (Long) -> Unit,
    viewModel: FocusViewModel = hiltViewModel(),
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val moment by viewModel.moment.collectAsStateWithLifecycle()
    val notice by viewModel.noticeRes.collectAsStateWithLifecycle()
    RefreshWhileVisible(intervalMs = 15_000L) { viewModel.refresh() }
    val c = FocusTheme.colors

    Box(Modifier.fillMaxSize()) {
        ContentColumn {
            ScreenTitle(stringResource(R.string.tab_focus))
            val s = ui
            if (s == null) {
                ShimmerBox(Modifier.fillMaxWidth().height(260.dp))
                ShimmerBox(Modifier.fillMaxWidth().height(140.dp))
                return@ContentColumn
            }
            notice?.let { res ->
                LaunchedEffect(res) { delay(5_000L); viewModel.clearNotice() }
                FocusCard(Modifier.fillMaxWidth(), selected = true) { Text(stringResource(res), style = FocusTheme.typography.body, color = c.danger) }
            }
            if (s.mode == DetectionMode.NONE || !s.blockingOn) {
                FocusCard(Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(if (s.mode == DetectionMode.NONE) R.string.focus_needs_detection else R.string.focus_blocking_off),
                        style = FocusTheme.typography.body,
                        color = c.text,
                    )
                    if (s.mode == DetectionMode.NONE) {
                        Spacer(Modifier.height(FocusTheme.spacing.md))
                        FocusButton(stringResource(R.string.blocks_fix_detection), onOpenPermissions, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary)
                    }
                }
            }

            PomodoroCard(s, viewModel)
            SchedulesCard(s, viewModel, onOpenSchedule)
            LimitsCard(s, viewModel, onOpenLimit, onOpenGroup)
            NudgesCard(s, viewModel)
        }
        moment?.let { HeroMoment(it) { viewModel.clearMoment() } }
    }
}

@Composable
private fun PomodoroCard(s: FocusUi, vm: FocusViewModel) {
    val c = FocusTheme.colors
    val run = s.run
    val now by produceState(System.currentTimeMillis(), run) {
        while (true) { value = System.currentTimeMillis(); delay(1_000L) }
    }
    FocusCard(Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.pomo_title))
        Spacer(Modifier.height(FocusTheme.spacing.md))
        if (run != null) {
            val total = (run.phaseEndMs - run.phaseStartMs).coerceAtLeast(1L)
            val left = (run.phaseEndMs - now).coerceAtLeast(0L)
            val mm = (left / 60_000L).toInt()
            val ss = ((left / 1000L) % 60L).toInt()
            val phaseName = stringResource(
                when (run.phase) {
                    PomodoroPhase.FOCUS -> R.string.pomo_phase_focus
                    PomodoroPhase.SHORT_BREAK -> R.string.pomo_phase_short
                    PomodoroPhase.LONG_BREAK -> R.string.pomo_phase_long
                },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                StatRing(
                    progress = 1f - left.toFloat() / total,
                    description = stringResource(R.string.pomo_ring_spoken, phaseName, mm, ss),
                    size = 200.dp,
                ) {
                    Text("%02d:%02d".format(mm, ss), style = FocusTheme.typography.numeralLarge, color = c.text)
                    Text(phaseName, style = FocusTheme.typography.label, color = c.textMuted)
                }
            }
            Spacer(Modifier.height(FocusTheme.spacing.sm))
            Text(
                stringResource(R.string.pomo_progress, run.completedFocus, run.config.cyclesBeforeLong),
                style = FocusTheme.typography.body,
                color = c.textMuted,
            )
            Spacer(Modifier.height(FocusTheme.spacing.md))
            FocusButton(stringResource(R.string.pomo_stop), vm::stopPomodoro, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary)
        } else {
            Text(stringResource(R.string.pomo_idle_body), style = FocusTheme.typography.body, color = c.textMuted)
            Spacer(Modifier.height(FocusTheme.spacing.md))
            ConfigSteppers(s.config, vm)
            FocusSwitch(
                checked = s.silenceDuringPomodoro,
                onCheckedChange = vm::setSilence,
                label = stringResource(R.string.pomo_silence),
                description = stringResource(R.string.pomo_silence_desc),
            )
            Spacer(Modifier.height(FocusTheme.spacing.md))
            FocusButton(stringResource(R.string.pomo_start), vm::startPomodoro, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(FocusTheme.spacing.md))
        Text(
            stringResource(R.string.pomo_stats, s.todayCompleted, s.todayMinutes, s.weekMinutes),
            style = FocusTheme.typography.label,
            color = c.textMuted,
        )
        if (s.recent.isNotEmpty()) {
            Spacer(Modifier.height(FocusTheme.spacing.sm))
            val timeFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            s.recent.forEach { r ->
                val whenText = java.time.Instant.ofEpochMilli(r.endedAtMs).atZone(java.time.ZoneId.systemDefault()).format(timeFormat)
                Text(
                    stringResource(if (r.completed) R.string.pomo_history_done else R.string.pomo_history_stopped, r.minutes, whenText),
                    style = FocusTheme.typography.label,
                    color = if (r.completed) c.success else c.textMuted,
                )
            }
        }
    }
}

@Composable
private fun ConfigSteppers(config: PomodoroConfig, vm: FocusViewModel) {
    StepperRow(stringResource(R.string.pomo_cfg_focus), stringResource(R.string.strict_minutes, config.focusMin),
        { vm.updateConfig { it.copy(focusMin = it.focusMin - 5) } }, { vm.updateConfig { it.copy(focusMin = it.focusMin + 5) } },
        minusEnabled = config.focusMin > 5, plusEnabled = config.focusMin < 120)
    StepperRow(stringResource(R.string.pomo_cfg_short), stringResource(R.string.strict_minutes, config.shortBreakMin),
        { vm.updateConfig { it.copy(shortBreakMin = it.shortBreakMin - 1) } }, { vm.updateConfig { it.copy(shortBreakMin = it.shortBreakMin + 1) } },
        minusEnabled = config.shortBreakMin > 1, plusEnabled = config.shortBreakMin < 30)
    StepperRow(stringResource(R.string.pomo_cfg_long), stringResource(R.string.strict_minutes, config.longBreakMin),
        { vm.updateConfig { it.copy(longBreakMin = it.longBreakMin - 5) } }, { vm.updateConfig { it.copy(longBreakMin = it.longBreakMin + 5) } },
        minusEnabled = config.longBreakMin > 5, plusEnabled = config.longBreakMin < 60)
    StepperRow(stringResource(R.string.pomo_cfg_cycles), config.cyclesBeforeLong.toString(),
        { vm.updateConfig { it.copy(cyclesBeforeLong = it.cyclesBeforeLong - 1) } }, { vm.updateConfig { it.copy(cyclesBeforeLong = it.cyclesBeforeLong + 1) } },
        minusEnabled = config.cyclesBeforeLong > 2, plusEnabled = config.cyclesBeforeLong < 8)
}

private fun minutesText(min: Int): String = LocalTime.of(min / 60, min % 60).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

@Composable
private fun daysText(mask: Int): String = when (mask) {
    DaysMask.ALL -> stringResource(R.string.days_every_day)
    DaysMask.WEEKDAYS -> stringResource(R.string.days_weekdays)
    DaysMask.WEEKEND -> stringResource(R.string.days_weekend)
    else -> DayOfWeek.entries.filter { DaysMask.has(mask, it) }.joinToString(" ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
}

@Composable
private fun SchedulesCard(s: FocusUi, vm: FocusViewModel, onOpen: (Long) -> Unit) {
    val c = FocusTheme.colors
    FocusCard(Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.sched_title))
        Spacer(Modifier.height(FocusTheme.spacing.sm))
        if (s.schedules.isEmpty()) {
            Text(stringResource(R.string.sched_empty), style = FocusTheme.typography.body, color = c.textMuted)
        }
        s.schedules.forEach { row ->
            val sch: FocusSchedule = row.schedule
            Column(Modifier.fillMaxWidth().padding(vertical = FocusTheme.spacing.xs)) {
                FocusSwitch(
                    checked = sch.enabled,
                    onCheckedChange = { vm.toggleSchedule(row) },
                    label = if (row.active) stringResource(R.string.sched_running, sch.name) else sch.name,
                    description = stringResource(R.string.sched_summary, minutesText(sch.startMin), minutesText(sch.endMin), daysText(sch.daysMask), sch.packages.size),
                )
                FocusButton(stringResource(R.string.action_edit), { onOpen(sch.id) }, style = ButtonStyle.Ghost)
            }
        }
        Spacer(Modifier.height(FocusTheme.spacing.sm))
        FocusButton(stringResource(R.string.sched_add), { onOpen(0L) }, Modifier.fillMaxWidth())
    }
}

@Composable
private fun LimitsCard(s: FocusUi, vm: FocusViewModel, onOpenLimit: (Long) -> Unit, onOpenGroup: (Long) -> Unit) {
    val c = FocusTheme.colors
    FocusCard(Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.limit_title))
        Spacer(Modifier.height(FocusTheme.spacing.sm))
        if (s.limits.isEmpty()) {
            Text(stringResource(R.string.limit_empty), style = FocusTheme.typography.body, color = c.textMuted)
        }
        s.limits.forEach { row ->
            Column(Modifier.fillMaxWidth().padding(vertical = FocusTheme.spacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (row.rule.kind == LimitKind.APP) AppIcon(row.rule.target, size = 36.dp)
                    Column(Modifier.weight(1f).padding(start = if (row.rule.kind == LimitKind.APP) FocusTheme.spacing.md else 0.dp)) {
                        FocusSwitch(
                            checked = row.rule.enabled,
                            onCheckedChange = { vm.toggleLimit(row) },
                            label = row.label,
                            description = stringResource(R.string.limit_summary, durationText(row.usedMs), durationText(row.rule.dailyLimitMs), daysText(row.rule.daysMask)),
                        )
                    }
                }
                FocusProgressBar(
                    progress = if (row.rule.dailyLimitMs <= 0) 0f else row.usedMs.toFloat() / row.rule.dailyLimitMs,
                    color = if (row.usedMs >= row.rule.dailyLimitMs) c.danger else if (row.usedMs * 100 >= row.rule.dailyLimitMs * 80) c.secondary else c.success,
                    barHeight = 6.dp,
                    description = stringResource(R.string.limit_progress_spoken, row.label, durationText(row.usedMs), durationText(row.rule.dailyLimitMs)),
                )
                FocusButton(stringResource(R.string.action_edit), { onOpenLimit(row.rule.id) }, style = ButtonStyle.Ghost)
            }
        }
        Spacer(Modifier.height(FocusTheme.spacing.sm))
        FocusButton(stringResource(R.string.limit_add), { onOpenLimit(0L) }, Modifier.fillMaxWidth())

        Spacer(Modifier.height(FocusTheme.spacing.lg))
        SectionLabel(stringResource(R.string.group_title))
        Spacer(Modifier.height(FocusTheme.spacing.sm))
        if (s.groups.isEmpty()) Text(stringResource(R.string.group_empty), style = FocusTheme.typography.body, color = c.textMuted)
        s.groups.forEach { g ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.group_row, g.name, g.packages.size), style = FocusTheme.typography.body, color = c.text, modifier = Modifier.weight(1f))
                FocusButton(stringResource(R.string.action_edit), { onOpenGroup(g.id) }, style = ButtonStyle.Ghost)
            }
        }
        Spacer(Modifier.height(FocusTheme.spacing.sm))
        FocusButton(stringResource(R.string.group_add), { onOpenGroup(0L) }, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary)
    }
}

@Composable
private fun NudgesCard(s: FocusUi, vm: FocusViewModel) {
    val c = FocusTheme.colors
    val context = androidx.compose.ui.platform.LocalContext.current
    FocusCard(Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.nudge_title))
        Spacer(Modifier.height(FocusTheme.spacing.sm))
        FocusSwitch(
            checked = s.nudge.breakRemindersEnabled,
            onCheckedChange = vm::setBreakReminders,
            label = stringResource(R.string.nudge_break),
            description = stringResource(R.string.nudge_break_desc),
        )
        if (s.nudge.breakRemindersEnabled) {
            StepperRow(
                stringResource(R.string.nudge_after), stringResource(R.string.strict_minutes, s.nudge.breakAfterMin),
                { vm.setBreakAfter(s.nudge.breakAfterMin - 15) }, { vm.setBreakAfter(s.nudge.breakAfterMin + 15) },
                minusEnabled = s.nudge.breakAfterMin > 15, plusEnabled = s.nudge.breakAfterMin < 180,
            )
        }
        if (!s.dndGranted) {
            Spacer(Modifier.height(FocusTheme.spacing.md))
            Text(stringResource(R.string.dnd_hint), style = FocusTheme.typography.body, color = c.textMuted)
            Spacer(Modifier.height(FocusTheme.spacing.sm))
            FocusButton(stringResource(R.string.perm_dnd_action), { PermissionLauncher.open(context, PermissionKind.DND_ACCESS) }, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary)
        }
    }
}
