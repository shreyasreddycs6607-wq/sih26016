package com.focusforce.app.data.social

import com.focusforce.app.domain.game.FocusMinutes
import com.focusforce.app.domain.game.Levels
import com.focusforce.app.domain.game.Rank
import com.focusforce.app.domain.game.WeekKey
import com.focusforce.app.domain.game.Xp
import com.focusforce.app.domain.repo.FocusRepository
import com.focusforce.app.domain.repo.SettingsRepository
import com.focusforce.app.domain.repo.UsageRepository
import com.focusforce.app.ui.insights.todayFlow
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn

data class GameState(
    val xp: Int,
    val rank: Rank,
    val level: Int,
    val levelProgress: Float,
    val rankProgress: Float,
    val streak: Int,
    val longestStreak: Int,
    val todayOnTrack: Boolean,
    val weeklyXp: Int,
    val weekKey: String,
)

/** XP, rank, level and streak, derived on the fly from usage history and completed focus blocks (see [Xp]). */
@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class GameStateRepository @Inject constructor(
    private val usage: UsageRepository,
    private val focus: FocusRepository,
    private val settings: SettingsRepository,
) {
    fun observe(): Flow<GameState> = todayFlow().flatMapLatest { today ->
        combine(
            usage.observeSummaries(today.minusDays(HISTORY_DAYS), today),
            focus.observeSessions(0L),
            settings.settings,
        ) { summaries, sessions, prefs ->
            val goalMs = prefs.dailyGoalMinutes * 60_000L
            val zone = ZoneId.systemDefault()
            val minutes = sessions.filter { it.completed }
                .map { FocusMinutes(Instant.ofEpochMilli(it.endedAtMs).atZone(zone).toLocalDate(), it.minutes) }
            val r = Xp.compute(summaries, minutes, goalMs, today)
            GameState(
                xp = r.totalXp,
                rank = Rank.of(r.totalXp),
                level = Levels.of(r.totalXp),
                levelProgress = Levels.progress(r.totalXp),
                rankProgress = Levels.rankProgress(r.totalXp),
                streak = r.currentStreak,
                longestStreak = r.longestStreak,
                todayOnTrack = r.todayOnTrack,
                weeklyXp = WeekKey.weeklyXp(summaries, minutes, goalMs, today),
                weekKey = WeekKey.of(today),
            )
        }.flowOn(Dispatchers.Default)
    }

    suspend fun current(): GameState = observe().first()

    private companion object {
        const val HISTORY_DAYS = 400L
    }
}
