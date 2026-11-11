package com.focusforce.app.domain.repo

import com.focusforce.app.domain.model.FocusGoal
import com.focusforce.app.domain.model.HeroTheme
import com.focusforce.app.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<UserSettings>

    suspend fun completeOnboarding(hero: HeroTheme, goals: Set<FocusGoal>, dailyGoalMinutes: Int)
    suspend fun setHero(hero: HeroTheme)
    suspend fun setGoals(goals: Set<FocusGoal>)
    suspend fun setDailyGoalMinutes(minutes: Int)

    /** Back to a fresh install (onboarding shows again). */
    suspend fun reset()
}
