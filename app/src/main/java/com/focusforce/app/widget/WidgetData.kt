package com.focusforce.app.widget

import android.content.Context
import com.focusforce.app.data.focus.FocusPrefs
import com.focusforce.app.data.focus.PomodoroController
import com.focusforce.app.data.social.GameStateRepository
import com.focusforce.app.domain.model.HeroTheme
import com.focusforce.app.domain.pomodoro.Pomodoro
import com.focusforce.app.domain.pomodoro.PomodoroPhase
import com.focusforce.app.domain.repo.SettingsRepository
import com.focusforce.app.domain.repo.UsageRepository
import com.focusforce.app.permissions.PermissionChecker
import dagger.Module
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** Everything a widget or the Quick Settings tile shows, computed from local data only. */
data class WidgetSnapshot(
    val hero: HeroTheme,
    val totalMs: Long,
    val goalMs: Long,
    val synced: Boolean,
    val streak: Int,
    /** When the current focus/break phase ends, or null if no Pomodoro is running. */
    val phaseEndsAtMs: Long?,
    val phase: PomodoroPhase?,
) {
    val progress: Float get() = if (goalMs <= 0) 0f else (totalMs.toFloat() / goalMs).coerceIn(0f, 1f)
    val focusing: Boolean get() = phaseEndsAtMs != null
}

@Singleton
class WidgetDataSource @Inject constructor(
    private val usage: UsageRepository,
    private val game: GameStateRepository,
    private val settings: SettingsRepository,
    private val prefs: FocusPrefs,
) {
    suspend fun snapshot(): WidgetSnapshot {
        val s = settings.settings.first()
        val today = LocalDate.now()
        val summary = usage.observeSummaries(today, today).first().firstOrNull()
        val run = prefs.pomodoroRun.first()?.let { Pomodoro.advance(it, System.currentTimeMillis()).first }
        return WidgetSnapshot(
            hero = s.hero,
            totalMs = summary?.totalMs ?: 0L,
            goalMs = s.dailyGoalMinutes * 60_000L,
            synced = summary != null,
            streak = runCatching { game.current().streak }.getOrDefault(0),
            phaseEndsAtMs = run?.phaseEndMs,
            phase = run?.phase,
        )
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun data(): WidgetDataSource
    fun pomodoro(): PomodoroController
    fun permissions(): PermissionChecker
}

internal fun entryPoint(context: Context): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
