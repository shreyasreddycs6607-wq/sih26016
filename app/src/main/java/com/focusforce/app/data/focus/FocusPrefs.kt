package com.focusforce.app.data.focus

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.focusforce.app.domain.limits.LimitNotifyState
import com.focusforce.app.domain.nudge.NudgeSettings
import com.focusforce.app.domain.pomodoro.PomodoroConfig
import com.focusforce.app.domain.pomodoro.PomodoroPhase
import com.focusforce.app.domain.pomodoro.PomodoroRun
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.focusStore: DataStore<Preferences> by preferencesDataStore("focusforce_focus")

/** What we changed in Do Not Disturb, so it can be put back exactly (and left alone if the user changed it). */
data class DndRecord(val applied: Boolean = false, val previousFilter: Int = 1, val setFilter: Int = 2)

private object F {
    val FOCUS = intPreferencesKey("pomo_focus_min")
    val SHORT = intPreferencesKey("pomo_short_min")
    val LONG = intPreferencesKey("pomo_long_min")
    val CYCLES = intPreferencesKey("pomo_cycles")
    val SILENCE = booleanPreferencesKey("pomo_silence")

    val RUN_PHASE = stringPreferencesKey("run_phase")
    val RUN_DONE = intPreferencesKey("run_completed")
    val RUN_START = longPreferencesKey("run_start")
    val RUN_END = longPreferencesKey("run_end")
    val RUN_FOCUS = intPreferencesKey("run_cfg_focus")
    val RUN_SHORT = intPreferencesKey("run_cfg_short")
    val RUN_LONG = intPreferencesKey("run_cfg_long")
    val RUN_CYCLES = intPreferencesKey("run_cfg_cycles")

    val BREAK_ON = booleanPreferencesKey("nudge_break_on")
    val BREAK_AFTER = intPreferencesKey("nudge_break_after")
    val BREAK_LAST = longPreferencesKey("nudge_break_last")

    val LIMIT_DAY = longPreferencesKey("limit_notify_day")
    val LIMIT_SENT = stringSetPreferencesKey("limit_notify_sent")

    val DND_APPLIED = booleanPreferencesKey("dnd_applied")
    val DND_PREV = intPreferencesKey("dnd_prev")
    val DND_SET = intPreferencesKey("dnd_set")
    val ACTIVE_IDS = stringSetPreferencesKey("active_schedule_ids")
}

/** Small persisted state for focus features. DataStore, so it survives process death and reboot. */
@Singleton
class FocusPrefs @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store get() = context.focusStore

    val pomodoroConfig: Flow<PomodoroConfig> = store.data.map { p ->
        PomodoroConfig(p[F.FOCUS] ?: 25, p[F.SHORT] ?: 5, p[F.LONG] ?: 15, p[F.CYCLES] ?: 4).coerced()
    }.distinctUntilChanged()

    val silenceDuringPomodoro: Flow<Boolean> = store.data.map { it[F.SILENCE] ?: true }.distinctUntilChanged()

    val pomodoroRun: Flow<PomodoroRun?> = store.data.map { p ->
        val phase = PomodoroPhase.entries.firstOrNull { it.name == p[F.RUN_PHASE] } ?: return@map null
        PomodoroRun(
            config = PomodoroConfig(p[F.RUN_FOCUS] ?: 25, p[F.RUN_SHORT] ?: 5, p[F.RUN_LONG] ?: 15, p[F.RUN_CYCLES] ?: 4).coerced(),
            phase = phase,
            completedFocus = p[F.RUN_DONE] ?: 0,
            phaseStartMs = p[F.RUN_START] ?: 0L,
            phaseEndMs = p[F.RUN_END] ?: 0L,
        )
    }.distinctUntilChanged()

    val nudge: Flow<NudgeSettings> = store.data.map { NudgeSettings(it[F.BREAK_ON] ?: true, it[F.BREAK_AFTER] ?: 45).coerced() }.distinctUntilChanged()

    suspend fun setPomodoroConfig(c: PomodoroConfig) {
        val v = c.coerced()
        store.edit { it[F.FOCUS] = v.focusMin; it[F.SHORT] = v.shortBreakMin; it[F.LONG] = v.longBreakMin; it[F.CYCLES] = v.cyclesBeforeLong }
    }

    suspend fun setSilenceDuringPomodoro(on: Boolean) { store.edit { it[F.SILENCE] = on } }

    suspend fun setPomodoroRun(run: PomodoroRun?) {
        store.edit { p ->
            if (run == null) {
                p.remove(F.RUN_PHASE)
            } else {
                p[F.RUN_PHASE] = run.phase.name
                p[F.RUN_DONE] = run.completedFocus
                p[F.RUN_START] = run.phaseStartMs
                p[F.RUN_END] = run.phaseEndMs
                p[F.RUN_FOCUS] = run.config.focusMin
                p[F.RUN_SHORT] = run.config.shortBreakMin
                p[F.RUN_LONG] = run.config.longBreakMin
                p[F.RUN_CYCLES] = run.config.cyclesBeforeLong
            }
        }
    }

    suspend fun setNudge(s: NudgeSettings) {
        val v = s.coerced()
        store.edit { it[F.BREAK_ON] = v.breakRemindersEnabled; it[F.BREAK_AFTER] = v.breakAfterMin }
    }

    suspend fun lastBreakReminder(): Long = store.data.first()[F.BREAK_LAST] ?: 0L
    suspend fun setLastBreakReminder(ms: Long) { store.edit { it[F.BREAK_LAST] = ms } }

    suspend fun limitNotifyState(): LimitNotifyState =
        store.data.first().let { LimitNotifyState(it[F.LIMIT_DAY] ?: 0L, it[F.LIMIT_SENT] ?: emptySet()) }

    suspend fun setLimitNotifyState(s: LimitNotifyState) {
        store.edit { it[F.LIMIT_DAY] = s.epochDay; it[F.LIMIT_SENT] = s.sent }
    }

    suspend fun dnd(): DndRecord = store.data.first().let { DndRecord(it[F.DND_APPLIED] ?: false, it[F.DND_PREV] ?: 1, it[F.DND_SET] ?: 2) }
    suspend fun setDnd(r: DndRecord) { store.edit { it[F.DND_APPLIED] = r.applied; it[F.DND_PREV] = r.previousFilter; it[F.DND_SET] = r.setFilter } }

    suspend fun activeScheduleIds(): Set<Long> = (store.data.first()[F.ACTIVE_IDS] ?: emptySet()).mapNotNull { it.toLongOrNull() }.toSet()
    suspend fun setActiveScheduleIds(ids: Set<Long>) { store.edit { it[F.ACTIVE_IDS] = ids.map { id -> id.toString() }.toSet() } }
}
