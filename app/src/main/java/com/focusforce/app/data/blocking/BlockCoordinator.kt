package com.focusforce.app.data.blocking

import android.content.Context
import android.content.Intent
import android.view.inputmethod.InputMethodManager
import com.focusforce.app.data.usage.ExcludedPackages
import com.focusforce.app.domain.blocking.BlockContext
import com.focusforce.app.domain.blocking.BlockDecision
import com.focusforce.app.domain.blocking.BlockDecisionEngine
import com.focusforce.app.domain.blocking.BlockSettings
import com.focusforce.app.domain.blocking.PassState
import com.focusforce.app.domain.blocking.SafetyPackages
import com.focusforce.app.domain.repo.BlockSettingsRepository
import com.focusforce.app.domain.repo.BlockedAppsRepository
import com.focusforce.app.ui.block.BlockActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Receives "this app came to the front" from the accessibility service (primary) or the usage
 * polling loop (fallback), decides synchronously from an in-memory snapshot, and opens the block screen.
 * The snapshot is rebuilt from the database/DataStore, so it survives process death.
 */
@Singleton
class BlockCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    settingsRepository: BlockSettingsRepository,
    blockedApps: BlockedAppsRepository,
    private val excluded: ExcludedPackages,
) {
    private data class Snapshot(val settings: BlockSettings, val blocked: Set<String>, val pass: PassState)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var snapshot: Snapshot? = null
    @Volatile private var neverBlock: Set<String> = SafetyPackages.neverBlock
    @Volatile private var neverBlockAtMs = 0L
    @Volatile private var lastBlockPackage: String? = null
    @Volatile private var lastBlockAtMs = 0L

    /** Set by the accessibility service; the polling fallback stays idle while this is true. */
    @Volatile var accessibilityConnected: Boolean = false

    init {
        scope.launch {
            combine(settingsRepository.settings, blockedApps.observe(), settingsRepository.passState, ::Snapshot)
                .collect { snapshot = it }
        }
    }

    fun onForeground(packageName: String?, className: String?) {
        val pkg = packageName ?: return
        val snap = snapshot ?: return
        val now = System.currentTimeMillis()
        val decision = BlockDecisionEngine.decide(
            packageName = pkg,
            className = className.orEmpty(),
            ctx = BlockContext(snap.settings, snap.blocked, snap.pass, context.packageName, neverBlockSet(now)),
            nowMs = now,
            today = LocalDate.now().toEpochDay(),
        )
        if (decision !is BlockDecision.Block) return
        // Repeated window events from the same app within a moment would relaunch the screen over and over.
        if (decision.packageName == lastBlockPackage && now - lastBlockAtMs < DEDUPE_MS) return
        lastBlockPackage = decision.packageName
        lastBlockAtMs = now
        show(decision)
    }

    private fun neverBlockSet(now: Long): Set<String> {
        if (now - neverBlockAtMs > 60_000L) {
            val imes = context.getSystemService(InputMethodManager::class.java)
                ?.enabledInputMethodList.orEmpty().map { it.packageName }
            neverBlock = SafetyPackages.neverBlock + excluded.current() + imes
            neverBlockAtMs = now
        }
        return neverBlock
    }

    private fun show(block: BlockDecision.Block) {
        val intent = Intent(context, BlockActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            .putExtra(BlockActivity.EXTRA_PACKAGE, block.packageName)
            .putExtra(BlockActivity.EXTRA_REASON, block.reason.name)
        try {
            context.startActivity(intent)
        } catch (_: RuntimeException) {
            // Background-activity-start refused by the OS (e.g. overlay permission revoked). Nothing safe to do.
        }
    }

    private companion object {
        const val DEDUPE_MS = 800L
    }
}
