package com.focusforce.app.data.reset

import android.content.Context
import com.focusforce.app.data.blocking.Outcome
import com.focusforce.app.data.db.AppDatabase
import com.focusforce.app.data.focus.FocusEnvironment
import com.focusforce.app.data.focus.FocusPrefs
import com.focusforce.app.data.focus.PomodoroController
import com.focusforce.app.data.web.RulesRepository
import com.focusforce.app.data.web.WebPrefs
import com.focusforce.app.domain.blocking.BlockingRules
import com.focusforce.app.domain.repo.BlockSettingsRepository
import com.focusforce.app.domain.repo.SettingsRepository
import com.focusforce.app.service.BlockerController
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * "Reset data": returns the app to a fresh install on this phone: usage history, blocked apps, limits, schedules, groups,
 * focus history, settings, rules you imported. It does NOT touch your multiplayer account or what is stored online (use
 * Delete my data under Squads and leaderboards for that) and it never affects other apps. Refused during a strict lock,
 * because wiping the lock settings would be a way around it.
 */
@Singleton
class DataResetter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val blockSettings: BlockSettingsRepository,
    private val focusPrefs: FocusPrefs,
    private val webPrefs: WebPrefs,
    private val rules: RulesRepository,
    private val pomodoro: PomodoroController,
    private val environment: FocusEnvironment,
) {
    suspend fun resetAll(): Outcome {
        if (!BlockingRules.canLoosen(blockSettings.settings.first(), System.currentTimeMillis())) return Outcome.DENIED_STRICT
        BlockerController.stop(context)
        pomodoro.discardForReset()
        withContext(Dispatchers.IO) { db.clearAllTables() }
        environment.refresh() // schedules are gone: puts Do Not Disturb and the alarms back as they were
        rules.reset()
        webPrefs.reset()
        focusPrefs.reset()
        blockSettings.reset()
        settings.reset()
        com.focusforce.app.widget.WidgetUpdater.updateAll(context)
        return Outcome.OK
    }
}
