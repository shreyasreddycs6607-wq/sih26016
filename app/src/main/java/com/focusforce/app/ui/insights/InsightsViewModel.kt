package com.focusforce.app.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforce.app.data.apps.AppInfoProvider
import com.focusforce.app.domain.repo.SettingsRepository
import com.focusforce.app.domain.repo.UsageRepository
import com.focusforce.app.domain.usage.AppCategory
import com.focusforce.app.domain.usage.Insights
import com.focusforce.app.permissions.PermissionChecker
import com.focusforce.app.permissions.PermissionKind
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class InsightsPeriod { DAILY, WEEKLY, MONTHLY }

data class InsightsUi(
    val period: InsightsPeriod,
    val anchor: LocalDate,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    /** Chart slots in ms (null = no data). 24 hours for DAILY, 7 or 30 days otherwise. */
    val values: List<Long?>,
    val labels: List<String>,
    /** Spoken/visible name of each slot, e.g. "9:00" or "Tue 3 Mar". */
    val slotTitles: List<String>,
    val goalMs: Long?,
    val totalMs: Long,
    val averageMs: Long?,
    val unlocks: Int?,
    val apps: List<AppRow>,
    val split: Map<AppCategory, Long>,
    val heatGrid: List<List<Long>>,
    val heatLabels: List<String>,
    val peakHour: Int?,
    val hasData: Boolean,
)

sealed interface InsightsState {
    data object Loading : InsightsState
    data object NeedsPermission : InsightsState
    data class Ready(val ui: InsightsUi) : InsightsState
}

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val usage: UsageRepository,
    private val settings: SettingsRepository,
    private val permissions: PermissionChecker,
    private val appInfo: AppInfoProvider,
    entitlement: com.focusforce.app.data.billing.EntitlementRepository,
) : ViewModel() {

    val pro: StateFlow<Boolean> = entitlement.pro

    private val granted = MutableStateFlow(permissions.isGranted(PermissionKind.USAGE_ACCESS))
    private val period = MutableStateFlow(InsightsPeriod.WEEKLY)
    private val dayOffset = MutableStateFlow(0) // DAILY only: 0 = today, -1 = yesterday...

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<InsightsState> = granted
        .flatMapLatest { ok ->
            if (!ok) flowOf<InsightsState>(InsightsState.NeedsPermission)
            else combine(period, dayOffset, ::Pair).flatMapLatest { (p, off) ->
                todayFlow().flatMapLatest { today -> build(p, off, today) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsState.Loading)

    val selectedPeriod: StateFlow<InsightsPeriod> = period

    private fun build(p: InsightsPeriod, offset: Int, today: LocalDate) = run {
        val anchor = if (p == InsightsPeriod.DAILY) today.plusDays(offset.toLong()) else today
        val from = when (p) {
            InsightsPeriod.DAILY -> anchor
            InsightsPeriod.WEEKLY -> today.minusDays(6)
            InsightsPeriod.MONTHLY -> today.minusDays(29)
        }
        val heatFrom = today.minusDays(6)
        combine(
            usage.observeSummaries(from, anchor),
            usage.observeApps(from, anchor),
            usage.observeHourly(minOf(from, heatFrom), today),
            usage.observeCategoryOverrides(),
            settings.settings,
        ) { summaries, apps, hourly, overrides, prefs ->
            val locale = Locale.getDefault()
            val goalMs = prefs.dailyGoalMinutes * 60_000L
            val byDate = summaries.associateBy { it.date }
            val dates = (0..(anchor.toEpochDay() - from.toEpochDay()).toInt()).map { from.plusDays(it.toLong()) }

            val values: List<Long?>
            val labels: List<String>
            val titles: List<String>
            when (p) {
                InsightsPeriod.DAILY -> {
                    val synced = byDate[anchor] != null
                    values = (0..23).map { h ->
                        if (synced) hourly.filter { it.date == anchor && it.hour == h }.sumOf { it.foregroundMs } else null
                    }
                    labels = (0..23).map { if (it % 6 == 0) it.toString() else "" }
                    titles = (0..23).map { "$it:00" }
                }
                InsightsPeriod.WEEKLY -> {
                    values = dates.map { byDate[it]?.totalMs }
                    labels = dates.map { it.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).take(3) }
                    titles = dates.map { it.format(DateTimeFormatter.ofPattern("EEE d MMM", locale)) }
                }
                InsightsPeriod.MONTHLY -> {
                    values = dates.map { byDate[it]?.totalMs }
                    labels = dates.mapIndexed { i, d -> if (i % 5 == 0 || i == dates.lastIndex) d.dayOfMonth.toString() else "" }
                    titles = dates.map { it.format(DateTimeFormatter.ofPattern("EEE d MMM", locale)) }
                }
            }
            val known = values.filterNotNull()
            val totalMs = if (p == InsightsPeriod.DAILY) byDate[anchor]?.totalMs ?: 0L else known.sum()
            val topApps = Insights.topApps(apps)
            val heatDates = (0..6).map { heatFrom.plusDays(it.toLong()) }
            val heatCells = hourly.filter { it.date >= heatFrom }
            InsightsState.Ready(
                InsightsUi(
                    period = p,
                    anchor = anchor,
                    canGoBack = offset > -MAX_DAYS_BACK,
                    canGoForward = offset < 0,
                    values = values,
                    labels = labels,
                    slotTitles = titles,
                    goalMs = if (p == InsightsPeriod.DAILY) null else goalMs,
                    totalMs = totalMs,
                    averageMs = if (p == InsightsPeriod.DAILY || known.isEmpty()) null else known.sum() / known.size,
                    unlocks = if (p == InsightsPeriod.DAILY) byDate[anchor]?.unlocks else null,
                    apps = topApps.take(10).toRows(appInfo, overrides),
                    split = Insights.categorySplit(topApps) { appInfo.resolveCategory(it, overrides) },
                    heatGrid = heatDates.map { d -> (0..23).map { h -> heatCells.filter { it.date == d && it.hour == h }.sumOf { it.foregroundMs } } },
                    heatLabels = heatDates.map { it.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).take(3) },
                    peakHour = Insights.peakHour(heatCells),
                    hasData = summaries.isNotEmpty(),
                ),
            ) as InsightsState
        }.flowOn(Dispatchers.Default)
    }

    fun setPeriod(value: InsightsPeriod) {
        period.value = value
        dayOffset.value = 0
    }

    fun shiftDay(delta: Int) = dayOffset.update { (it + delta).coerceIn(-MAX_DAYS_BACK, 0) }

    fun refresh() {
        val ok = permissions.isGranted(PermissionKind.USAGE_ACCESS)
        granted.value = ok
        if (ok) viewModelScope.launch { usage.refresh() }
    }

    private companion object {
        const val MAX_DAYS_BACK = 29
    }
}
