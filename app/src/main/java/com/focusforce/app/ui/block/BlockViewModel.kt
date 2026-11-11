package com.focusforce.app.ui.block

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforce.app.data.apps.AppInfoProvider
import com.focusforce.app.data.blocking.BlockCoordinator
import com.focusforce.app.data.blocking.BlockingManager
import com.focusforce.app.data.blocking.Outcome
import com.focusforce.app.domain.blocking.BlockReason
import com.focusforce.app.domain.blocking.BlockingRules
import com.focusforce.app.domain.model.HeroTheme
import com.focusforce.app.domain.repo.BlockSettingsRepository
import com.focusforce.app.domain.repo.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class BlockUiState(
    val hero: HeroTheme,
    val packageName: String,
    val label: String,
    val reason: BlockReason,
    val canPass: Boolean,
    val passesLeft: Int,
    val strictActive: Boolean,
    /** Blocked site name for WEBSITE; shown on screen only. */
    val detail: String?,
)

private val PASSABLE = setOf(BlockReason.BLOCKED_APP, BlockReason.LIMIT_REACHED, BlockReason.SCHEDULE, BlockReason.SHORT_VIDEO, BlockReason.WEBSITE)

private data class Target(val packageName: String, val reason: BlockReason, val detail: String?, val passKey: String)

@HiltViewModel
class BlockViewModel @Inject constructor(
    appSettings: SettingsRepository,
    blockSettings: BlockSettingsRepository,
    private val manager: BlockingManager,
    private val coordinator: BlockCoordinator,
    private val appInfo: AppInfoProvider,
) : ViewModel() {

    private val target = MutableStateFlow<Target?>(null)

    val state: StateFlow<BlockUiState?> = combine(
        target, appSettings.settings, blockSettings.settings, blockSettings.passState,
    ) { t, app, block, pass ->
        t?.let { target ->
            val pkg = target.packageName
            val reason = target.reason
            val now = System.currentTimeMillis()
            val today = LocalDate.now().toEpochDay()
            BlockUiState(
                hero = app.hero,
                packageName = pkg,
                label = appInfo.label(pkg),
                reason = reason,
                canPass = reason in PASSABLE && BlockingRules.canGrantPass(block, pass, now, today),
                passesLeft = pass.remaining(today),
                strictActive = block.strictActive(now),
                detail = target.detail,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setTarget(packageName: String, reason: BlockReason, detail: String?, passKey: String?) {
        target.value = Target(packageName, reason, detail, passKey ?: packageName)
    }

    /** Presses Back inside the blocked app (through the accessibility service) to leave a feed or page. */
    fun leave() { coordinator.performBack() }

    /** True when a pass was granted and the blocked app may be reopened. */
    suspend fun takePass(): Boolean {
        val key = target.value?.passKey ?: return false
        return manager.grantPass(key) == Outcome.OK
    }
}
