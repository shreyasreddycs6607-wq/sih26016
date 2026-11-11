package com.focusforce.app.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusforce.app.R
import com.focusforce.app.domain.model.UserSettings
import com.focusforce.app.ui.AppViewModel
import com.focusforce.app.ui.components.BottomBarItem
import com.focusforce.app.ui.components.ContentMaxWidth
import com.focusforce.app.ui.components.FocusBottomBar
import com.focusforce.app.ui.components.FocusIcon
import com.focusforce.app.ui.components.ThemedBackground
import com.focusforce.app.ui.components.UpcomingFeature
import com.focusforce.app.ui.blocks.BlocksScreen
import com.focusforce.app.ui.insights.InsightsScreen
import com.focusforce.app.ui.theme.FocusTheme

private enum class Tab(val icon: FocusIcon, val label: Int) {
    Home(FocusIcon.Home, R.string.tab_home),
    Focus(FocusIcon.Focus, R.string.tab_focus),
    Insights(FocusIcon.Insights, R.string.tab_insights),
    Blocks(FocusIcon.Blocks, R.string.tab_blocks),
    Profile(FocusIcon.Profile, R.string.tab_profile),
}

/** Home, Focus, Insights, Blocks, Profile behind the animated themed bottom bar. */
@Composable
fun MainScaffold(
    onOpenPermissions: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenApp: (String) -> Unit,
    onOpenCategories: () -> Unit,
    onOpenPicker: () -> Unit,
    appViewModel: AppViewModel = hiltViewModel(),
) {
    val settings by appViewModel.settings.collectAsStateWithLifecycle()
    val current = settings ?: UserSettings()
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val reduce = FocusTheme.reduceMotion
    val motion = FocusTheme.motion
    val items = Tab.entries.map { BottomBarItem(it.icon, stringResource(it.label)) }

    ThemedBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(
                    targetState = tabIndex,
                    transitionSpec = {
                        if (reduce) fadeIn(snap()) togetherWith fadeOut(snap())
                        else fadeIn(motion.medium()) togetherWith fadeOut(motion.fast())
                    },
                    label = "tabs",
                ) { index ->
                    when (Tab.entries[index]) {
                        Tab.Home -> HomeScreen(current, onOpenPermissions, onOpenApp)
                        Tab.Focus -> UpcomingFeature(
                            stringResource(R.string.tab_focus),
                            stringResource(R.string.phase_4),
                            stringResource(R.string.focus_desc),
                            stringArrayItems(R.array.focus_bullets),
                        )
                        Tab.Insights -> InsightsScreen(onOpenApp, onOpenCategories, onOpenPermissions)
                        Tab.Blocks -> BlocksScreen(onOpenPicker, onOpenPermissions)
                        Tab.Profile -> ProfileScreen(current, appViewModel, onOpenPermissions, onOpenGallery)
                    }
                }
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = FocusTheme.spacing.md, vertical = FocusTheme.spacing.sm), contentAlignment = Alignment.Center) {
                FocusBottomBar(
                    items = items,
                    selectedIndex = tabIndex,
                    onSelect = { tabIndex = it },
                    modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun stringArrayItems(id: Int): List<String> =
    androidx.compose.ui.res.stringArrayResource(id).toList()
