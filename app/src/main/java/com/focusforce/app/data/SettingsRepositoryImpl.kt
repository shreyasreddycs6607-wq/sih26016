package com.focusforce.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.focusforce.app.domain.model.DailyGoal
import com.focusforce.app.domain.model.FocusGoal
import com.focusforce.app.domain.model.HeroTheme
import com.focusforce.app.domain.model.UserSettings
import com.focusforce.app.domain.repo.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore("focusforce_settings")

private object Keys {
    val HERO = stringPreferencesKey("hero")
    val GOALS = stringSetPreferencesKey("goals")
    val DAILY_GOAL = intPreferencesKey("daily_goal_minutes")
    val ONBOARDED = booleanPreferencesKey("onboarding_complete")
}

/** Maps raw preferences to [UserSettings]. Unknown or missing values fall back to defaults. */
internal fun Preferences.toUserSettings(): UserSettings = UserSettings(
    hero = HeroTheme.entries.firstOrNull { it.name == this[Keys.HERO] } ?: HeroTheme.VECTOR,
    goals = (this[Keys.GOALS] ?: emptySet())
        .mapNotNull { name -> FocusGoal.entries.firstOrNull { it.name == name } }
        .toSet(),
    dailyGoalMinutes = DailyGoal.clamp(this[Keys.DAILY_GOAL] ?: DailyGoal.DEFAULT_MINUTES),
    onboardingComplete = this[Keys.ONBOARDED] ?: false,
)

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {

    private val store get() = context.settingsDataStore

    override val settings: Flow<UserSettings> = store.data.map { it.toUserSettings() }

    override suspend fun completeOnboarding(
        hero: HeroTheme,
        goals: Set<FocusGoal>,
        dailyGoalMinutes: Int,
    ) {
        store.edit {
            it[Keys.HERO] = hero.name
            it[Keys.GOALS] = goals.map(FocusGoal::name).toSet()
            it[Keys.DAILY_GOAL] = DailyGoal.clamp(dailyGoalMinutes)
            it[Keys.ONBOARDED] = true
        }
    }

    override suspend fun setHero(hero: HeroTheme) {
        store.edit { it[Keys.HERO] = hero.name }
    }

    override suspend fun setGoals(goals: Set<FocusGoal>) {
        store.edit { it[Keys.GOALS] = goals.map(FocusGoal::name).toSet() }
    }

    override suspend fun reset() {
        store.edit { it.clear() }
    }

    override suspend fun setDailyGoalMinutes(minutes: Int) {
        store.edit { it[Keys.DAILY_GOAL] = DailyGoal.clamp(minutes) }
    }
}
