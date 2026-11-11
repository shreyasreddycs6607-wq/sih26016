package com.focusforce.app.ui.insights

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.focusforce.app.data.apps.AppInfoProvider
import com.focusforce.app.domain.repo.UsageRepository
import com.focusforce.app.domain.usage.AppCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.focusforce.app.ui.nav.AppDetail

data class AppDetailUi(
    val packageName: String,
    val label: String,
    val installed: Boolean,
    val category: AppCategory,
    val overridden: Boolean,
    val values: List<Long?>,
    val labels: List<String>,
    val slotTitles: List<String>,
    val todayMs: Long,
    val averageMs: Long?,
    val launches: Int,
)

@HiltViewModel
class AppDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val usage: UsageRepository,
    private val appInfo: AppInfoProvider,
) : ViewModel() {

    private val packageName: String = savedStateHandle.toRoute<AppDetail>().packageName

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<AppDetailUi?> = todayFlow().flatMapLatest { today ->
        val from = today.minusDays(13)
        combine(
            usage.observeAppDaily(packageName, from, today),
            usage.observeSummaries(from, today),
            usage.observeCategoryOverrides(),
        ) { rows, summaries, overrides ->
            val locale = Locale.getDefault()
            val synced = summaries.map { it.date }.toSet()
            val byDate = rows.associateBy { it.date }
            val dates = (0..13).map { from.plusDays(it.toLong()) }
            val values = dates.map { d -> if (d in synced) byDate[d]?.foregroundMs ?: 0L else null }
            val lastWeek = values.takeLast(7).filterNotNull()
            AppDetailUi(
                packageName = packageName,
                label = appInfo.label(packageName),
                installed = appInfo.meta(packageName).installed,
                category = appInfo.resolveCategory(packageName, overrides),
                overridden = packageName in overrides,
                values = values,
                labels = dates.map { it.dayOfWeek.getDisplayName(TextStyle.NARROW, locale) },
                slotTitles = dates.map { it.format(DateTimeFormatter.ofPattern("EEE d MMM", locale)) },
                todayMs = byDate[today]?.foregroundMs ?: 0L,
                averageMs = if (lastWeek.isEmpty()) null else lastWeek.sum() / lastWeek.size,
                launches = rows.sumOf { it.launches },
            )
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** null clears the override and returns to the automatic category. */
    fun setCategory(category: AppCategory?) {
        viewModelScope.launch { usage.setCategory(packageName, category) }
    }
}
