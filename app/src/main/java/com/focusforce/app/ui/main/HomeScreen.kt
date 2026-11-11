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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusforce.app.R
import com.focusforce.app.domain.model.UserSettings
import com.focusforce.app.ui.art.HeroMascot
import com.focusforce.app.ui.art.HeroPose
import com.focusforce.app.ui.components.ButtonStyle
import com.focusforce.app.ui.components.ContentColumn
import com.focusforce.app.ui.components.FocusButton
import com.focusforce.app.ui.components.FocusCard
import com.focusforce.app.ui.components.ScreenTitle
import com.focusforce.app.ui.components.SectionLabel
import com.focusforce.app.ui.components.StatRing
import com.focusforce.app.ui.permissions.PermissionsViewModel
import com.focusforce.app.ui.theme.FocusTheme
import com.focusforce.app.ui.theme.nameRes
import com.focusforce.app.ui.theme.titleRes

/**
 * Phase 1 home: greeting, the live "setup readiness" ring (real permission state) and the saved
 * daily goal. Screen-time totals, top apps, missions and the weekly villain arrive with tracking (Phase 2).
 */
@Composable
fun HomeScreen(
    settings: UserSettings,
    onOpenPermissions: () -> Unit,
    permissions: PermissionsViewModel = hiltViewModel(),
) {
    val state by permissions.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permissions.refresh() }
    val c = FocusTheme.colors
    val granted = state.count { it.value }
    val total = state.size
    val allReady = granted == total

    ContentColumn {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeroMascot(settings.hero, Modifier.height(96.dp), pose = if (allReady) HeroPose.Celebrate else HeroPose.Idle)
            Column(Modifier.padding(start = FocusTheme.spacing.lg)) {
                Text(stringResource(R.string.home_greeting), style = FocusTheme.typography.label, color = c.textMuted)
                ScreenTitle(stringResource(settings.hero.nameRes))
                Text(stringResource(settings.hero.titleRes), style = FocusTheme.typography.body, color = c.primary)
            }
        }

        FocusCard(Modifier.fillMaxWidth()) {
            SectionLabel(stringResource(R.string.home_readiness_title))
            Spacer(Modifier.height(FocusTheme.spacing.md))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                StatRing(
                    progress = if (total == 0) 0f else granted.toFloat() / total,
                    description = stringResource(R.string.home_readiness_spoken, granted, total),
                    size = 168.dp,
                ) {
                    Text("$granted/$total", style = FocusTheme.typography.numeral, color = c.text)
                    Text(stringResource(R.string.home_readiness_ring_label), style = FocusTheme.typography.label, color = c.textMuted)
                }
            }
            Spacer(Modifier.height(FocusTheme.spacing.md))
            Text(
                stringResource(if (allReady) R.string.home_readiness_done else R.string.home_readiness_todo),
                style = FocusTheme.typography.body,
                color = c.text,
            )
            Spacer(Modifier.height(FocusTheme.spacing.md))
            FocusButton(
                text = stringResource(R.string.home_review_permissions),
                onClick = onOpenPermissions,
                modifier = Modifier.fillMaxWidth(),
                style = if (allReady) ButtonStyle.Secondary else ButtonStyle.Primary,
            )
        }

        FocusCard(Modifier.fillMaxWidth()) {
            SectionLabel(stringResource(R.string.home_goal_title))
            Spacer(Modifier.height(FocusTheme.spacing.sm))
            Text(
                stringResource(R.string.duration_hm, settings.dailyGoalMinutes / 60, settings.dailyGoalMinutes % 60),
                style = FocusTheme.typography.numeralLarge,
                color = c.primary,
            )
            Text(stringResource(R.string.home_goal_body), style = FocusTheme.typography.body, color = c.textMuted)
        }
    }
}
