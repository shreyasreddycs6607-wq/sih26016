package com.focusforce.app.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforce.app.data.apps.AppInfoProvider
import com.focusforce.app.domain.repo.UsageRepository
import com.focusforce.app.domain.usage.AppCategory
import com.focusforce.app.domain.usage.Insights
import dagger.hilt.android.lifecycle.HiltViewModel
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

/** Every app used in the last 30 days, with its (editable) productive / distracting / neutral category. */
@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val usage: UsageRepository,
    private val appInfo: AppInfoProvider,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val apps: StateFlow<List<AppRow>?> = todayFlow().flatMapLatest { today ->
        combine(usage.observeApps(today.minusDays(29), today), usage.observeCategoryOverrides()) { rows, overrides ->
            Insights.topApps(rows).toRows(appInfo, overrides)
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setCategory(packageName: String, category: AppCategory?) {
        viewModelScope.launch { usage.setCategory(packageName, category) }
    }
}
