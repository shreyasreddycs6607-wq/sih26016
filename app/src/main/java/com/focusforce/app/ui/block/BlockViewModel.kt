package com.focusforce.app.ui.block

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforce.app.data.apps.AppInfoProvider
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
)

@HiltViewModel
class BlockViewModel @Inject constructor(
    appSettings: SettingsRepository,
    blockSettings: BlockSettingsRepository,
    private val manager: BlockingManager,
    private val appInfo: AppInfoProvider,
) : ViewModel() {

    private val target = MutableStateFlow<Pair<String, BlockReason>?>(null)

    val state: StateFlow<BlockUiState?> = combine(
        target, appSettings.settings, blockSettings.settings, blockSettings.passState,
    ) { t, app, block, pass ->
        t?.let { (pkg, reason) ->
            val now = System.currentTimeMillis()
            val today = LocalDate.now().toEpochDay()
            BlockUiState(
                hero = app.hero,
                packageName = pkg,
                label = appInfo.label(pkg),
                reason = reason,
                canPass = reason == BlockReason.BLOCKED_APP && BlockingRules.canGrantPass(block, pass, now, today),
                passesLeft = pass.remaining(today),
                strictActive = block.strictActive(now),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setTarget(packageName: String, reason: BlockReason) {
        target.value = packageName to reason
    }

    /** True when a pass was granted and the blocked app may be reopened. */
    suspend fun takePass(): Boolean {
        val pkg = target.value?.first ?: return false
        return manager.grantPass(pkg) == Outcome.OK
    }
}
