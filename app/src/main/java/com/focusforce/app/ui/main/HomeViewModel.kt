package com.focusforce.app.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforce.app.data.apps.AppInfoProvider
import com.focusforce.app.domain.repo.SettingsRepository
import com.focusforce.app.domain.repo.UsageRepository
import com.focusforce.app.domain.usage.Comparison
import com.focusforce.app.domain.usage.DailyMission
import com.focusforce.app.domain.usage.Insights
import com.focusforce.app.domain.usage.Villain
import com.focusforce.app.permissions.PermissionChecker
import com.focusforce.app.permissions.PermissionKind
import com.focusforce.app.ui.insights.AppRow
import com.focusforce.app.ui.insights.toRows
import com.focusforce.app.ui.insights.todayFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VillainUi(val label: String, val villain: Villain)

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object NeedsPermission : HomeUiState
    data class Ready(
        /** False until the first sync has stored today's row. */
        val synced: Boolean,
        val totalMs: Long,
        val goalMs: Long,
        val unlocks: Int?,
        val comparison: Comparison,
        val topApps: List<AppRow>,
        val mission: DailyMission,
        val villain: VillainUi?,
    ) : HomeUiState
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val usage: UsageRepository,
    private val settings: SettingsRepository,
    private val permissions: PermissionChecker,
    private val appInfo: AppInfoProvider,
) : ViewModel() {

    private val granted = MutableStateFlow(permissions.isGranted(PermissionKind.USAGE_ACCESS))

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<HomeUiState> = granted
        .flatMapLatest { ok ->
            if (!ok) flowOf<HomeUiState>(HomeUiState.NeedsPermission)
            else todayFlow().flatMapLatest { today -> ready(today) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    private fun ready(today: LocalDate) = combine(
        usage.observeSummaries(today.minusDays(7), today),
        usage.observeApps(today.minusDays(13), today),
        usage.observeCategoryOverrides(),
        settings.settings,
    ) { summaries, apps, overrides, prefs ->
        val goalMs = prefs.dailyGoalMinutes * 60_000L
        val todaySummary = summaries.firstOrNull { it.date == today }
        val totalMs = todaySummary?.totalMs ?: 0L
        val weekStart = today.minusDays(6)
        val villain = Insights.villain(
            thisWeek = apps.filter { it.date >= weekStart },
            previousWeek = apps.filter { it.date < weekStart },
            weeklyGoalMs = goalMs * 7,
            categoryOf = { appInfo.resolveCategory(it, overrides) },
        )
        HomeUiState.Ready(
            synced = todaySummary != null,
            totalMs = totalMs,
            goalMs = goalMs,
            unlocks = todaySummary?.unlocks,
            comparison = Insights.comparison(summaries, today),
            topApps = Insights.topApps(apps.filter { it.date == today }, limit = 5).toRows(appInfo, overrides),
            mission = Insights.mission(totalMs, goalMs),
            villain = villain?.let { VillainUi(appInfo.label(it.packageName), it) },
        ) as HomeUiState
    }.flowOn(Dispatchers.Default)

    /** Call when the screen is visible: re-checks Usage access and pulls fresh numbers. */
    fun refresh() {
        val ok = permissions.isGranted(PermissionKind.USAGE_ACCESS)
        granted.value = ok
        if (ok) viewModelScope.launch { usage.refresh() }
    }
}
