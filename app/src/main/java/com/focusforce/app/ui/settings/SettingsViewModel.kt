package com.focusforce.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforce.app.R
import com.focusforce.app.data.billing.EntitlementRepository
import com.focusforce.app.data.blocking.Outcome
import com.focusforce.app.data.reset.DataResetter
import com.focusforce.app.data.reset.ExportRepository
import com.focusforce.app.domain.model.HeroTheme
import com.focusforce.app.domain.pro.ProFeature
import com.focusforce.app.domain.pro.ProPolicy
import com.focusforce.app.domain.repo.SettingsRepository
import com.focusforce.app.ui.util.noticeRes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ExportKind { USAGE, SESSIONS }

data class SettingsUi(val hero: HeroTheme, val pro: Boolean, val notice: Int?, val resetDone: Boolean)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val entitlement: EntitlementRepository,
    private val exporter: ExportRepository,
    private val resetter: DataResetter,
) : ViewModel() {

    private val notice = MutableStateFlow<Int?>(null)
    private val resetDone = MutableStateFlow(false)

    val state: StateFlow<SettingsUi> = combine(settings.settings, entitlement.pro, notice, resetDone) { s, pro, n, r ->
        SettingsUi(s.hero, pro, n, r)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUi(HeroTheme.VECTOR, false, null, false))

    fun clearNotice() { notice.value = null }

    fun setHero(hero: HeroTheme) { viewModelScope.launch { settings.setHero(hero) } }

    /** Export is a Pro feature; this is asked before opening the file picker. */
    fun canExport(): Boolean {
        val ok = ProPolicy.hasFeature(ProFeature.CSV_EXPORT, entitlement.isPro())
        if (!ok) notice.value = Outcome.UPGRADE.noticeRes()
        return ok
    }

    fun export(kind: ExportKind, uri: Uri) {
        viewModelScope.launch {
            val text = if (kind == ExportKind.USAGE) exporter.usageCsv() else exporter.sessionsCsv()
            notice.value = if (exporter.write(uri, text)) R.string.settings_export_done else R.string.settings_export_failed
        }
    }

    fun resetAll() {
        viewModelScope.launch {
            when (val outcome = resetter.resetAll()) {
                Outcome.OK -> resetDone.value = true
                else -> notice.value = outcome.noticeRes() ?: R.string.notice_strict_locked
            }
        }
    }
}
