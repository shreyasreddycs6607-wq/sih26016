package com.focusforce.app.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusforce.app.R
import com.focusforce.app.domain.model.DailyGoal
import com.focusforce.app.domain.model.FocusGoal
import com.focusforce.app.domain.model.HeroTheme
import com.focusforce.app.permissions.PermissionKind
import com.focusforce.app.ui.art.FocusForceLogo
import com.focusforce.app.ui.art.HeroMascot
import com.focusforce.app.ui.art.HeroPose
import com.focusforce.app.ui.components.AnimatedCounter
import com.focusforce.app.ui.components.ButtonStyle
import com.focusforce.app.ui.components.ContentColumn
import com.focusforce.app.ui.components.ContentMaxWidth
import com.focusforce.app.ui.components.FocusButton
import com.focusforce.app.ui.components.FocusCard
import com.focusforce.app.ui.components.FocusIcon
import com.focusforce.app.ui.components.FocusIconView
import com.focusforce.app.ui.components.ScreenTitle
import com.focusforce.app.ui.components.ThemedBackground
import com.focusforce.app.ui.permissions.PermissionExplainer
import com.focusforce.app.ui.permissions.PermissionsViewModel
import com.focusforce.app.ui.theme.FocusForceTheme
import com.focusforce.app.ui.theme.FocusTheme
import com.focusforce.app.ui.theme.nameRes
import com.focusforce.app.ui.theme.taglineRes
import com.focusforce.app.ui.theme.titleRes
import com.focusforce.app.ui.theme.traitsRes
import kotlinx.coroutines.delay

private enum class Step { Welcome, Hero, Goals, DailyGoal, Permissions }

/**
 * Cinematic onboarding: welcome -> hero -> goals -> daily goal -> guided permissions.
 * The theme previews live: as the user swipes between heroes the whole UI re-skins.
 * Nothing is persisted until [onFinished] (so quitting midway restarts cleanly).
 */
@Composable
fun OnboardingFlow(
    onFinished: (hero: HeroTheme, goals: Set<FocusGoal>, dailyGoalMinutes: Int) -> Unit,
) {
    var step by rememberSaveable { mutableStateOf(Step.Welcome) }
    var hero by rememberSaveable { mutableStateOf(HeroTheme.VECTOR) }
    var goals by rememberSaveable { mutableStateOf<Set<FocusGoal>>(setOf()) }
    var minutes by rememberSaveable { mutableIntStateOf(DailyGoal.DEFAULT_MINUTES) }
    var permissionIndex by rememberSaveable { mutableIntStateOf(-1) }

    FocusForceTheme(hero) {
        val reduce = FocusTheme.reduceMotion
        val motion = FocusTheme.motion

        fun back() {
            when {
                step == Step.Permissions && permissionIndex >= 0 -> permissionIndex -= 1
                step.ordinal > 0 -> step = Step.entries[step.ordinal - 1]
            }
        }
        BackHandler(enabled = step != Step.Welcome) { back() }

        ThemedBackground(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                if (step != Step.Welcome) {
                    StepHeader(current = step.ordinal, total = Step.entries.size - 1, onBack = ::back)
                }
                AnimatedContent(
                    targetState = step,
                    modifier = Modifier.weight(1f),
                    transitionSpec = {
                        val forward = targetState.ordinal > initialState.ordinal
                        if (reduce) {
                            fadeIn(snap()) togetherWith fadeOut(snap())
                        } else {
                            (slideInHorizontally(motion.medium()) { if (forward) it / 4 else -it / 4 } + fadeIn(motion.medium())) togetherWith
                                (slideOutHorizontally(motion.medium()) { if (forward) -it / 4 else it / 4 } + fadeOut(motion.fast()))
                        }
                    },
                    label = "onboardingStep",
                ) { target ->
                    when (target) {
                        Step.Welcome -> WelcomeStep(onBegin = { step = Step.Hero })
                        Step.Hero -> HeroStep(
                            initial = hero,
                            onHeroChange = { hero = it },
                            onChoose = { step = Step.Goals },
                        )
                        Step.Goals -> GoalsStep(
                            selected = goals,
                            onToggle = { g -> goals = if (g in goals) goals - g else goals + g },
                            onNext = { step = Step.DailyGoal },
                        )
                        Step.DailyGoal -> DailyGoalStep(
                            minutes = minutes,
                            onChange = { minutes = DailyGoal.clamp(it) },
                            onNext = { step = Step.Permissions },
                        )
                        Step.Permissions -> PermissionFlow(
                            index = permissionIndex,
                            onIndexChange = { permissionIndex = it },
                            onDone = { onFinished(hero, goals, minutes) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepHeader(current: Int, total: Int, onBack: () -> Unit) {
    val c = FocusTheme.colors
    val backLabel = stringResource(R.string.action_back)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = FocusTheme.spacing.sm, vertical = FocusTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = backLabel },
            contentAlignment = Alignment.Center,
        ) { FocusIconView(FocusIcon.Back, c.text) }
        Spacer(Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.semantics { contentDescription = "Step $current of $total" },
        ) {
            for (i in 1..total) {
                Box(Modifier.size(width = if (i == current) 24.dp else 8.dp, height = 8.dp).clip(CircleShape).background(if (i <= current) c.primary else c.surfaceHigh))
            }
        }
        Spacer(Modifier.size(24.dp))
    }
}

// ---- Welcome --------------------------------------------------------------

@Composable
private fun WelcomeStep(onBegin: () -> Unit) {
    val c = FocusTheme.colors
    val reduce = FocusTheme.reduceMotion
    val motion = FocusTheme.motion
    var stage by remember { mutableIntStateOf(if (reduce) 4 else 0) }
    LaunchedEffect(Unit) {
        while (stage < 4) {
            delay(280)
            stage += 1
        }
    }
    val enter: EnterTransition = if (reduce) EnterTransition.None else fadeIn(motion.medium()) + scaleIn(motion.spring(), initialScale = 0.7f)
    val rise: EnterTransition = if (reduce) EnterTransition.None else fadeIn(motion.medium()) + slideInVertically(motion.medium()) { it / 3 }

    ContentColumn(verticalArrangement = Arrangement.Center) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(FocusTheme.spacing.lg)) {
            Spacer(Modifier.height(FocusTheme.spacing.xl))
            AnimatedVisibility(stage >= 1, enter = enter) { FocusForceLogo(size = 120.dp) }
            AnimatedVisibility(stage >= 2, enter = rise) {
                Text(
                    stringResource(R.string.app_wordmark),
                    style = FocusTheme.typography.display,
                    color = c.text,
                    textAlign = TextAlign.Center,
                )
            }
            AnimatedVisibility(stage >= 2, enter = rise) {
                Text(
                    stringResource(R.string.welcome_tagline),
                    style = FocusTheme.typography.body,
                    color = c.textMuted,
                    textAlign = TextAlign.Center,
                )
            }
            AnimatedVisibility(stage >= 3, enter = rise) {
                Row(horizontalArrangement = Arrangement.spacedBy(FocusTheme.spacing.md), verticalAlignment = Alignment.Bottom) {
                    HeroTheme.entries.forEach { HeroMascot(it, Modifier.height(110.dp), pose = HeroPose.Idle) }
                }
            }
            AnimatedVisibility(stage >= 4, enter = rise) {
                FocusButton(stringResource(R.string.welcome_begin), onBegin, Modifier.widthIn(min = 220.dp))
            }
        }
    }
}

// ---- Hero -----------------------------------------------------------------

@Composable
private fun HeroStep(initial: HeroTheme, onHeroChange: (HeroTheme) -> Unit, onChoose: () -> Unit) {
    val pagerState = rememberPagerState(initialPage = initial.ordinal, pageCount = { HeroTheme.entries.size })
    LaunchedEffect(pagerState) {
        androidx.compose.runtime.snapshotFlow { pagerState.currentPage }.collect { onHeroChange(HeroTheme.entries[it]) }
    }
    val current = HeroTheme.entries[pagerState.currentPage]
    val c = FocusTheme.colors

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        ScreenTitle(stringResource(R.string.hero_step_title), Modifier.padding(horizontal = FocusTheme.spacing.lg))
        Text(
            stringResource(R.string.hero_step_hint),
            style = FocusTheme.typography.body,
            color = c.textMuted,
            modifier = Modifier.padding(horizontal = FocusTheme.spacing.lg, vertical = FocusTheme.spacing.xs),
        )
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            val hero = HeroTheme.entries[page]
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = FocusTheme.spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HeroMascot(
                    hero,
                    Modifier.height(210.dp),
                    pose = if (page == pagerState.currentPage) HeroPose.Guard else HeroPose.Idle,
                )
                Spacer(Modifier.height(FocusTheme.spacing.md))
                Text(stringResource(hero.nameRes), style = FocusTheme.typography.display, color = FocusTheme.colors.text, textAlign = TextAlign.Center)
                Text(stringResource(hero.titleRes), style = FocusTheme.typography.title, color = FocusTheme.colors.primary, textAlign = TextAlign.Center)
                Spacer(Modifier.height(FocusTheme.spacing.sm))
                Text(stringResource(hero.taglineRes), style = FocusTheme.typography.body, color = FocusTheme.colors.text, textAlign = TextAlign.Center)
                Spacer(Modifier.height(FocusTheme.spacing.sm))
                Text(stringResource(hero.traitsRes), style = FocusTheme.typography.label, color = FocusTheme.colors.textMuted, textAlign = TextAlign.Center)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = FocusTheme.spacing.sm)) {
            HeroTheme.entries.forEach {
                Box(Modifier.size(if (it == current) 12.dp else 8.dp).clip(CircleShape).background(if (it == current) c.primary else c.surfaceHigh))
            }
        }
        FocusButton(
            text = stringResource(R.string.hero_choose, stringResource(current.nameRes)),
            onClick = onChoose,
            modifier = Modifier.padding(horizontal = FocusTheme.spacing.lg).widthIn(max = ContentMaxWidth).fillMaxWidth(),
        )
        Spacer(Modifier.height(FocusTheme.spacing.lg))
    }
}

// ---- Goals ----------------------------------------------------------------

private fun FocusGoal.titleRes() = when (this) {
    FocusGoal.STUDY -> R.string.goal_study
    FocusGoal.WORK -> R.string.goal_work
    FocusGoal.SLEEP -> R.string.goal_sleep
    FocusGoal.LESS_SOCIAL -> R.string.goal_social
}

private fun FocusGoal.descRes() = when (this) {
    FocusGoal.STUDY -> R.string.goal_study_desc
    FocusGoal.WORK -> R.string.goal_work_desc
    FocusGoal.SLEEP -> R.string.goal_sleep_desc
    FocusGoal.LESS_SOCIAL -> R.string.goal_social_desc
}

@Composable
private fun GoalsStep(selected: Set<FocusGoal>, onToggle: (FocusGoal) -> Unit, onNext: () -> Unit) {
    val c = FocusTheme.colors
    ContentColumn {
        ScreenTitle(stringResource(R.string.goals_title))
        Text(stringResource(R.string.goals_hint), style = FocusTheme.typography.body, color = c.textMuted)
        FocusGoal.entries.forEach { goal ->
            val on = goal in selected
            FocusCard(Modifier.fillMaxWidth(), onClick = { onToggle(goal) }, selected = on) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(goal.titleRes()), style = FocusTheme.typography.title, color = c.text)
                        Text(stringResource(goal.descRes()), style = FocusTheme.typography.body, color = c.textMuted)
                    }
                    if (on) FocusIconView(FocusIcon.Check, c.primary, size = 28.dp, contentDescription = stringResource(R.string.selected))
                }
            }
        }
        FocusButton(stringResource(R.string.action_continue), onNext, Modifier.fillMaxWidth(), enabled = selected.isNotEmpty())
        if (selected.isEmpty()) {
            Text(stringResource(R.string.goals_pick_one), style = FocusTheme.typography.label, color = c.textMuted)
        }
    }
}

// ---- Daily goal -----------------------------------------------------------

@Composable
private fun DailyGoalStep(minutes: Int, onChange: (Int) -> Unit, onNext: () -> Unit) {
    val c = FocusTheme.colors
    val steps = (DailyGoal.MAX_MINUTES - DailyGoal.MIN_MINUTES) / DailyGoal.STEP_MINUTES - 1
    val spoken = stringResource(R.string.daily_goal_spoken, minutes / 60, minutes % 60)
    ContentColumn {
        ScreenTitle(stringResource(R.string.daily_goal_title))
        Text(stringResource(R.string.daily_goal_hint), style = FocusTheme.typography.body, color = c.textMuted)
        FocusCard(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = spoken },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom,
            ) {
                AnimatedCounter(minutes / 60, color = c.primary)
                Text("h ", style = FocusTheme.typography.title, color = c.textMuted, modifier = Modifier.padding(bottom = 10.dp))
                AnimatedCounter(minutes % 60, color = c.primary, format = { "%02d".format(it) })
                Text("m", style = FocusTheme.typography.title, color = c.textMuted, modifier = Modifier.padding(bottom = 10.dp))
            }
            Slider(
                value = minutes.toFloat(),
                onValueChange = { onChange(it.toInt()) },
                valueRange = DailyGoal.MIN_MINUTES.toFloat()..DailyGoal.MAX_MINUTES.toFloat(),
                steps = steps,
                colors = SliderDefaults.colors(
                    thumbColor = c.primary,
                    activeTrackColor = c.primary,
                    inactiveTrackColor = c.surfaceHigh,
                    activeTickColor = c.onPrimary,
                    inactiveTickColor = c.textMuted,
                ),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FocusTheme.spacing.sm)) {
            listOf(120, 180, 240).forEach { preset ->
                FocusButton("${preset / 60}h", { onChange(preset) }, Modifier.weight(1f), style = if (minutes == preset) ButtonStyle.Primary else ButtonStyle.Secondary)
            }
        }
        Text(stringResource(R.string.daily_goal_change_later), style = FocusTheme.typography.label, color = c.textMuted)
        FocusButton(stringResource(R.string.action_continue), onNext, Modifier.fillMaxWidth())
    }
}

// ---- Permissions ----------------------------------------------------------

@Composable
private fun PermissionFlow(
    index: Int,
    onIndexChange: (Int) -> Unit,
    onDone: () -> Unit,
    viewModel: PermissionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val kinds = remember { PermissionKind.applicable() }
    val c = FocusTheme.colors

    if (index < 0) {
        ContentColumn(verticalArrangement = Arrangement.spacedBy(FocusTheme.spacing.lg)) {
            ScreenTitle(stringResource(R.string.perm_intro_title))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                HeroMascot(FocusTheme.hero, Modifier.height(160.dp), pose = HeroPose.Guard)
            }
            Text(stringResource(R.string.perm_intro_body, kinds.size), style = FocusTheme.typography.body, color = c.text)
            Text(stringResource(R.string.perm_intro_privacy), style = FocusTheme.typography.body, color = c.textMuted)
            FocusButton(stringResource(R.string.perm_intro_start), { onIndexChange(0) }, Modifier.fillMaxWidth())
            FocusButton(stringResource(R.string.perm_intro_later), onDone, Modifier.fillMaxWidth(), style = ButtonStyle.Ghost)
        }
    } else {
        val kind = kinds[index.coerceIn(0, kinds.lastIndex)]
        val advance = { if (index >= kinds.lastIndex) onDone() else onIndexChange(index + 1) }
        PermissionExplainer(
            kind = kind,
            granted = state[kind] == true,
            stepLabel = stringResource(R.string.perm_step_label, index + 1, kinds.size),
            onRefresh = viewModel::refresh,
            onNext = advance,
            onSkip = advance,
        )
    }
}
