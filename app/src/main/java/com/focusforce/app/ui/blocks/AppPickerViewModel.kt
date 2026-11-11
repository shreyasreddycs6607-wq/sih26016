package com.focusforce.app.ui.blocks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforce.app.R
import com.focusforce.app.data.apps.AppInfoProvider
import com.focusforce.app.data.blocking.BlockingManager
import com.focusforce.app.data.blocking.InstalledApps
import com.focusforce.app.data.blocking.Outcome
import com.focusforce.app.domain.repo.BlockedAppsRepository
import com.focusforce.app.domain.repo.UsageRepository
import com.focusforce.app.domain.usage.AppCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PickerApp(val packageName: String, val label: String, val category: AppCategory, val selected: Boolean)

data class PickerUi(
    val loading: Boolean,
    val query: String,
    val filter: AppCategory?,
    val apps: List<PickerApp>,
    val selectedCount: Int,
    val allShownSelected: Boolean,
    val notice: Int?,
)

@HiltViewModel
class AppPickerViewModel @Inject constructor(
    private val installed: InstalledApps,
    private val appInfo: AppInfoProvider,
    private val manager: BlockingManager,
    blockedApps: BlockedAppsRepository,
    usage: UsageRepository,
) : ViewModel() {

    private val packages = MutableStateFlow<List<String>?>(null)
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow<AppCategory?>(null)
    private val notice = MutableStateFlow<Int?>(null)

    init {
        viewModelScope.launch { packages.value = installed.blockable() }
    }

    val state: StateFlow<PickerUi> = combine(
        packages, query, filter, combine(blockedApps.observe(), usage.observeCategoryOverrides(), ::Pair), notice,
    ) { pkgs, q, f, (selected, overrides), n ->
        val all = (pkgs ?: emptyList()).map {
            PickerApp(it, appInfo.label(it), appInfo.resolveCategory(it, overrides), it in selected)
        }.sortedBy { it.label.lowercase() }
        val shown = all.filter { (f == null || it.category == f) && (q.isBlank() || it.label.contains(q.trim(), ignoreCase = true)) }
        PickerUi(
            loading = pkgs == null,
            query = q,
            filter = f,
            apps = shown,
            selectedCount = selected.size,
            allShownSelected = shown.isNotEmpty() && shown.all { it.selected },
            notice = n,
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PickerUi(true, "", null, emptyList(), 0, false, null))

    fun setQuery(value: String) { query.value = value }
    fun setFilter(value: AppCategory?) { filter.value = value }
    fun clearNotice() { notice.value = null }

    fun toggle(app: PickerApp) {
        viewModelScope.launch {
            if (app.selected) denyIfStrict(manager.removeApps(listOf(app.packageName))) else manager.addApps(listOf(app.packageName))
        }
    }

    /** Select every app currently shown; if they are all selected already, unselect them. */
    fun toggleAllShown() {
        val s = state.value
        val shown = s.apps.map { it.packageName }
        viewModelScope.launch {
            if (s.allShownSelected) denyIfStrict(manager.removeApps(shown)) else manager.addApps(shown)
        }
    }

    private fun denyIfStrict(outcome: Outcome) {
        if (outcome == Outcome.DENIED_STRICT) notice.value = R.string.notice_strict_locked
    }
}
