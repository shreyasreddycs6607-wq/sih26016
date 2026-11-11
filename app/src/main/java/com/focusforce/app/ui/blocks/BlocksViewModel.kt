package com.focusforce.app.ui.blocks

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforce.app.R
import com.focusforce.app.data.apps.AppInfoProvider
import com.focusforce.app.data.blocking.BlockingManager
import com.focusforce.app.data.blocking.Outcome
import com.focusforce.app.domain.blocking.DetectionMode
import com.focusforce.app.domain.repo.BlockSettingsRepository
import com.focusforce.app.domain.repo.BlockedAppsRepository
import com.focusforce.app.permissions.PermissionChecker
import com.focusforce.app.permissions.PermissionKind
import com.focusforce.app.service.BlockerController
import com.focusforce.app.ui.util.noticeRes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BlockedRow(val packageName: String, val label: String)

data class BlocksUi(
    val enabled: Boolean,
    val strictUntilMs: Long,
    val guardSettings: Boolean,
    val passesEnabled: Boolean,
    val mode: DetectionMode,
    val blocked: List<BlockedRow>,
    /** String resource of a one-off message (e.g. "locked by strict mode"), or null. */
    val notice: Int?,
)

@HiltViewModel
class BlocksViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val manager: BlockingManager,
    settings: BlockSettingsRepository,
    blockedApps: BlockedAppsRepository,
    private val permissions: PermissionChecker,
    private val appInfo: AppInfoProvider,
) : ViewModel() {

    private val mode = MutableStateFlow(currentMode())
    private val notice = MutableStateFlow<Int?>(null)

    val state: StateFlow<BlocksUi?> = combine(settings.settings, blockedApps.observe(), mode, notice) { s, apps, m, n ->
        BlocksUi(
            enabled = s.enabled,
            strictUntilMs = s.strictUntilMs,
            guardSettings = s.guardSettings,
            passesEnabled = s.passesEnabled,
            mode = m,
            blocked = apps.map { BlockedRow(it, appInfo.label(it)) }.sortedBy { it.label.lowercase() },
            notice = n,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun currentMode() = DetectionMode.from(
        accessibility = permissions.isGranted(PermissionKind.ACCESSIBILITY),
        usageAccess = permissions.isGranted(PermissionKind.USAGE_ACCESS),
        overlay = permissions.isGranted(PermissionKind.OVERLAY),
    )

    /** Call whenever the screen is visible: re-reads permissions and restarts the service if blocking is on. */
    fun refresh() {
        mode.value = currentMode()
        viewModelScope.launch { if (state.value?.enabled == true) BlockerController.ensureRunning(context) }
    }

    fun clearNotice() { notice.value = null }

    fun setEnabled(on: Boolean) {
        viewModelScope.launch {
            if (on && currentMode() == DetectionMode.NONE) {
                notice.value = R.string.notice_need_detection
                return@launch
            }
            when (manager.setEnabled(on)) {
                Outcome.OK -> if (on) BlockerController.ensureRunning(context) else BlockerController.stop(context)
                else -> notice.value = R.string.notice_strict_locked
            }
        }
    }

    fun setPasses(on: Boolean) = launchAction { manager.setPassesEnabled(on) }
    fun setGuard(on: Boolean) = launchAction { manager.setGuardSettings(on) }
    fun remove(pkg: String) = launchAction { manager.removeApps(listOf(pkg)) }

    fun startStrict(minutes: Int) {
        viewModelScope.launch {
            if (currentMode() == DetectionMode.NONE) {
                notice.value = R.string.notice_need_detection
                return@launch
            }
            val outcome = manager.startStrict(minutes)
            outcome.noticeRes()?.let { notice.value = it }
            if (outcome == Outcome.OK) BlockerController.ensureRunning(context)
        }
    }

    private fun launchAction(action: suspend () -> Outcome) {
        viewModelScope.launch {
            action().noticeRes()?.let { notice.value = it }
        }
    }
}
