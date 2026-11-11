package com.focusforce.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforce.app.domain.model.DailyGoal
import com.focusforce.app.domain.model.FocusGoal
import com.focusforce.app.domain.model.HeroTheme
import com.focusforce.app.domain.model.UserSettings
import com.focusforce.app.domain.repo.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Activity/app-wide view model: exposes persisted settings (null until first read) and mutations. */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val repository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<UserSettings?> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun completeOnboarding(hero: HeroTheme, goals: Set<FocusGoal>, minutes: Int) {
        viewModelScope.launch { repository.completeOnboarding(hero, goals, minutes) }
    }

    fun setHero(hero: HeroTheme) {
        viewModelScope.launch { repository.setHero(hero) }
    }

    fun setDailyGoal(minutes: Int) {
        viewModelScope.launch { repository.setDailyGoalMinutes(DailyGoal.clamp(minutes)) }
    }
}
