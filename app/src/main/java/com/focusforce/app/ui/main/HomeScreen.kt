package com.focusforce.app.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusforce.app.R
import com.focusforce.app.domain.model.UserSettings
import com.focusforce.app.domain.usage.Comparison
import com.focusforce.app.domain.usage.MissionStatus
import com.focusforce.app.ui.art.HeroMascot
import com.focusforce.app.ui.art.HeroPose
import com.focusforce.app.ui.components.AppIcon
import com.focusforce.app.ui.components.ButtonStyle
import com.focusforce.app.ui.components.ContentColumn
import com.focusforce.app.ui.components.DurationCounter
import com.focusforce.app.ui.components.FocusButton
import com.focusforce.app.ui.components.FocusCard
import com.focusforce.app.ui.components.FocusProgressBar
import com.focusforce.app.ui.components.RefreshWhileVisible
import com.focusforce.app.ui.components.ScreenTitle
import com.focusforce.app.ui.components.SectionLabel
import com.focusforce.app.ui.components.ShimmerBox
import com.focusforce.app.ui.components.StatRing
import com.focusforce.app.ui.insights.AppUsageRow
import com.focusforce.app.ui.permissions.PermissionsViewModel
import com.focusforce.app.ui.theme.FocusTheme
import com.focusforce.app.ui.theme.nameRes
import com.focusforce.app.ui.theme.titleRes
import com.focusforce.app.ui.util.durationText

/** Home: today's total and goal ring, comparisons, top apps, the daily mission and the villain of the week. */
@Composable
fun HomeScreen(
    settings: UserSettings,
    onOpenPermissions: () -> Unit,
    onOpenApp: (String) -> Unit,
    home: HomeViewModel = hiltViewModel(),
    permissions: PermissionsViewModel = hiltViewModel(),
) {
    val state by home.state.collectAsStateWithLifecycle()
    val permissionState by permissions.state.collectAsStateWithLifecycle()
    RefreshWhileVisible { home.refresh(); permissions.refresh() }
    val c = FocusTheme.colors
    val granted = permissionState.count { it.value }
    val total = permissionState.size
    val ready = state as? HomeUiState.Ready

    ContentColumn {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val mood = when (ready?.mission?.status) {
                MissionStatus.FAILED -> HeroPose.Guard
                MissionStatus.ON_TRACK -> HeroPose.Celebrate
                else -> HeroPose.Idle
            }
            HeroMascot(settings.hero, Modifier.height(96.dp), pose = mood)
            Column(Modifier.padding(start = FocusTheme.spacing.lg)) {
                Text(stringResource(R.string.home_greeting), style = FocusTheme.typography.label, color = c.textMuted)
                ScreenTitle(stringResource(settings.hero.nameRes))
                Text(stringResource(settings.hero.titleRes), style = FocusTheme.typography.body, color = c.primary)
            }
        }

        when (val s = state) {
            HomeUiState.Loading -> HomeSkeleton()
            HomeUiState.NeedsPermission -> UsageAccessCard(onOpenPermissions)
            is HomeUiState.Ready -> {
                TodayCard(s)
                TopAppsCard(s, onOpenApp)
                MissionCard(s)
                VillainCard(s, onOpenApp)
            }
        }

        if (total > 0 && granted < total) {
            FocusCard(Modifier.fillMaxWidth()) {
                SectionLabel(stringResource(R.string.home_readiness_title))
                Spacer(Modifier.height(FocusTheme.spacing.sm))
                Text(stringResource(R.string.home_readiness_spoken, granted, total), style = FocusTheme.typography.body, color = c.text)
                Text(stringResource(R.string.home_readiness_todo), style = FocusTheme.typography.body, color = c.textMuted)
                Spacer(Modifier.height(FocusTheme.spacing.md))
                FocusButton(stringResource(R.string.home_review_permissions), onOpenPermissions, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary)
            }
        }
    }
}

@Composable
private fun HomeSkeleton() {
    ShimmerBox(Modifier.fillMaxWidth().height(220.dp))
    ShimmerBox(Modifier.fillMaxWidth().height(160.dp))
    ShimmerBox(Modifier.fillMaxWidth().height(96.dp))
}

@Composable
private fun UsageAccessCard(onOpenPermissions: () -> Unit) {
    val c = FocusTheme.colors
    FocusCard(Modifier.fillMaxWidth(), selected = true) {
        SectionLabel(stringResource(R.string.usage_needed_title))
        Spacer(Modifier.height(FocusTheme.spacing.sm))
        Text(stringResource(R.string.usage_needed_body), style = FocusTheme.typography.body, color = c.text)
        Spacer(Modifier.height(FocusTheme.spacing.md))
        FocusButton(stringResource(R.string.usage_needed_action), onOpenPermissions, Modifier.fillMaxWidth())
    }
}

@Composable
private fun TodayCard(s: HomeUiState.Ready) {
    val c = FocusTheme.colors
    val goalText = durationText(s.goalMs)
    FocusCard(Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.home_today_title))
        Spacer(Modifier.height(FocusTheme.spacing.md))
        if (!s.synced) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { ShimmerBox(Modifier.height(168.dp).fillMaxWidth(0.6f)) }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                StatRing(
                    progress = s.totalMs.toFloat() / s.goalMs.coerceAtLeast(1L),
                    description = stringResource(R.string.home_today_spoken, durationText(s.totalMs), goalText),
                    size = 176.dp,
                ) {
                    DurationCounter((s.totalMs / 60_000L).toInt(), style = FocusTheme.typography.numeral)
                    Text(stringResource(R.string.home_today_of_goal, goalText), style = FocusTheme.typography.label, color = c.textMuted)
                }
            }
            Spacer(Modifier.height(FocusTheme.spacing.md))
            ComparisonLine(s.comparison)
            s.unlocks?.let {
                Text(stringResource(R.string.home_unlocks, it), style = FocusTheme.typography.body, color = c.textMuted)
            }
        }
    }
}

@Composable
private fun ComparisonLine(cmp: Comparison) {
    val c = FocusTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        deltaText(cmp.vsYesterdayPct, R.string.delta_yesterday_more, R.string.delta_yesterday_less, R.string.delta_yesterday_same, R.string.delta_no_yesterday, c.danger, c.success, c.textMuted)
        deltaText(cmp.vsWeeklyAvgPct, R.string.delta_week_more, R.string.delta_week_less, R.string.delta_week_same, R.string.delta_no_week, c.danger, c.success, c.textMuted)
    }
}

/** More time than the baseline is flagged with the danger color, less with success; the sentence says it too. */
@Composable
private fun deltaText(pct: Int?, more: Int, less: Int, same: Int, none: Int, bad: Color, good: Color, muted: Color) {
    val (text, color) = when {
        pct == null -> stringResource(none) to muted
        pct > 0 -> stringResource(more, pct) to bad
        pct < 0 -> stringResource(less, -pct) to good
        else -> stringResource(same) to muted
    }
    Text(text, style = FocusTheme.typography.body, color = color)
}

@Composable
private fun TopAppsCard(s: HomeUiState.Ready, onOpenApp: (String) -> Unit) {
    val c = FocusTheme.colors
    FocusCard(Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.home_top_apps))
        Spacer(Modifier.height(FocusTheme.spacing.sm))
        if (s.topApps.isEmpty()) {
            Text(stringResource(R.string.home_no_apps), style = FocusTheme.typography.body, color = c.textMuted)
        } else {
            val max = s.topApps.maxOf { it.foregroundMs }
            s.topApps.forEach { AppUsageRow(it, max) { onOpenApp(it.packageName) } }
        }
    }
}

@Composable
private fun statusColor(status: MissionStatus): Color = when (status) {
    MissionStatus.ON_TRACK -> FocusTheme.colors.success
    MissionStatus.AT_RISK -> FocusTheme.colors.secondary
    MissionStatus.FAILED -> FocusTheme.colors.danger
}

@Composable
private fun MissionCard(s: HomeUiState.Ready) {
    val c = FocusTheme.colors
    val m = s.mission
    val goalText = durationText(m.goalMs)
    val remaining = durationText(m.remainingMs)
    FocusCard(Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.mission_title))
        Spacer(Modifier.height(FocusTheme.spacing.sm))
        Text(stringResource(R.string.mission_stay_under, goalText), style = FocusTheme.typography.title, color = c.text)
        Spacer(Modifier.height(FocusTheme.spacing.sm))
        FocusProgressBar(
            progress = m.progress,
            color = statusColor(m.status),
            description = stringResource(R.string.mission_progress_spoken, durationText(m.totalMs), goalText),
        )
        Spacer(Modifier.height(FocusTheme.spacing.sm))
        Text(
            when (m.status) {
                MissionStatus.ON_TRACK -> stringResource(R.string.mission_on_track, remaining)
                MissionStatus.AT_RISK -> stringResource(R.string.mission_at_risk, remaining)
                MissionStatus.FAILED -> stringResource(R.string.mission_failed)
            },
            style = FocusTheme.typography.body,
            color = statusColor(m.status),
        )
    }
}

@Composable
private fun VillainCard(s: HomeUiState.Ready, onOpenApp: (String) -> Unit) {
    val c = FocusTheme.colors
    FocusCard(Modifier.fillMaxWidth(), onClick = s.villain?.let { v -> { onOpenApp(v.villain.packageName) } }) {
        SectionLabel(stringResource(R.string.villain_title))
        Spacer(Modifier.height(FocusTheme.spacing.sm))
        val v = s.villain
        if (v == null) {
            Text(stringResource(R.string.villain_none), style = FocusTheme.typography.body, color = c.textMuted)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(v.villain.packageName, size = 48.dp)
                Column(Modifier.padding(start = FocusTheme.spacing.md)) {
                    Text(v.label, style = FocusTheme.typography.title, color = c.text)
                    Text(
                        stringResource(R.string.villain_usage, durationText(v.villain.usageMs), durationText(v.villain.targetMs)),
                        style = FocusTheme.typography.body,
                        color = c.textMuted,
                    )
                }
            }
            Spacer(Modifier.height(FocusTheme.spacing.sm))
            FocusProgressBar(
                progress = v.villain.progress,
                color = statusColor(v.villain.status),
                description = stringResource(R.string.villain_progress_spoken, v.label, durationText(v.villain.usageMs), durationText(v.villain.targetMs)),
            )
            Spacer(Modifier.height(FocusTheme.spacing.sm))
            Text(
                stringResource(
                    when (v.villain.status) {
                        MissionStatus.ON_TRACK -> R.string.villain_on_track
                        MissionStatus.AT_RISK -> R.string.villain_at_risk
                        MissionStatus.FAILED -> R.string.villain_failed
                    },
                ),
                style = FocusTheme.typography.body,
                color = statusColor(v.villain.status),
            )
        }
    }
}
