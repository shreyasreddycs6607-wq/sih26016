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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.focusforce.app.BuildConfig
import com.focusforce.app.R
import com.focusforce.app.domain.model.DailyGoal
import com.focusforce.app.domain.model.HeroTheme
import com.focusforce.app.domain.model.UserSettings
import com.focusforce.app.ui.AppViewModel
import com.focusforce.app.ui.art.HeroMascot
import com.focusforce.app.ui.art.HeroPose
import com.focusforce.app.ui.components.AnimatedCounter
import com.focusforce.app.ui.components.ButtonStyle
import com.focusforce.app.ui.components.ContentColumn
import com.focusforce.app.ui.components.FocusButton
import com.focusforce.app.ui.components.FocusCard
import com.focusforce.app.ui.components.ScreenTitle
import com.focusforce.app.ui.components.SectionLabel
import com.focusforce.app.ui.theme.FocusTheme
import com.focusforce.app.ui.theme.nameRes
import com.focusforce.app.ui.theme.titleRes

/** Profile: rank card, live hero/theme switcher, daily goal editor, links to permissions (and the debug gallery). */
@Composable
fun ProfileScreen(
    settings: UserSettings,
    viewModel: AppViewModel,
    onOpenPermissions: () -> Unit,
    onOpenGallery: () -> Unit,
) {
    val c = FocusTheme.colors
    ContentColumn {
        ScreenTitle(stringResource(R.string.tab_profile))

        FocusCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeroMascot(settings.hero, Modifier.height(120.dp), pose = HeroPose.Idle)
                Column(Modifier.padding(start = FocusTheme.spacing.lg)) {
                    Text(stringResource(settings.hero.nameRes), style = FocusTheme.typography.headline, color = c.text)
                    Text(stringResource(settings.hero.titleRes), style = FocusTheme.typography.body, color = c.primary)
                    Spacer(Modifier.height(FocusTheme.spacing.sm))
                    Text(stringResource(R.string.profile_rank), style = FocusTheme.typography.label, color = c.textMuted)
                }
            }
        }

        SectionLabel(stringResource(R.string.profile_hero_section))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FocusTheme.spacing.md)) {
            HeroTheme.entries.forEach { hero ->
                val selectedLabel = stringResource(R.string.hero_switch_spoken, stringResource(hero.nameRes))
                FocusCard(
                    Modifier.weight(1f).semantics { contentDescription = selectedLabel },
                    onClick = { viewModel.setHero(hero) },
                    selected = hero == settings.hero,
                    contentPadding = FocusTheme.spacing.sm,
                ) {
                    HeroMascot(hero, Modifier.height(72.dp).align(Alignment.CenterHorizontally))
                    Text(
                        stringResource(hero.nameRes),
                        style = FocusTheme.typography.label,
                        color = c.text,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        SectionLabel(stringResource(R.string.profile_goal_section))
        FocusCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                AnimatedCounter(settings.dailyGoalMinutes / 60, color = c.primary)
                Text("h ", style = FocusTheme.typography.title, color = c.textMuted, modifier = Modifier.padding(bottom = 10.dp))
                AnimatedCounter(settings.dailyGoalMinutes % 60, color = c.primary, format = { "%02d".format(it) })
                Text("m", style = FocusTheme.typography.title, color = c.textMuted, modifier = Modifier.padding(bottom = 10.dp))
            }
            Spacer(Modifier.height(FocusTheme.spacing.md))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FocusTheme.spacing.md)) {
                FocusButton(
                    stringResource(R.string.goal_minus),
                    { viewModel.setDailyGoal(settings.dailyGoalMinutes - DailyGoal.STEP_MINUTES) },
                    Modifier.weight(1f),
                    style = ButtonStyle.Secondary,
                    enabled = settings.dailyGoalMinutes > DailyGoal.MIN_MINUTES,
                )
                FocusButton(
                    stringResource(R.string.goal_plus),
                    { viewModel.setDailyGoal(settings.dailyGoalMinutes + DailyGoal.STEP_MINUTES) },
                    Modifier.weight(1f),
                    style = ButtonStyle.Secondary,
                    enabled = settings.dailyGoalMinutes < DailyGoal.MAX_MINUTES,
                )
            }
        }

        FocusButton(stringResource(R.string.home_review_permissions), onOpenPermissions, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary)
        if (BuildConfig.DEBUG) {
            FocusButton(stringResource(R.string.debug_gallery_open), onOpenGallery, Modifier.fillMaxWidth(), style = ButtonStyle.Ghost)
        }
    }
}
