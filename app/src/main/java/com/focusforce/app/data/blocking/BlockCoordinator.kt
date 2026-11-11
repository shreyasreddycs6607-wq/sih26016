package com.focusforce.app.data.blocking

import android.content.Context
import android.content.Intent
import android.view.inputmethod.InputMethodManager
import com.focusforce.app.R
import com.focusforce.app.data.apps.AppInfoProvider
import com.focusforce.app.data.focus.FocusPrefs
import com.focusforce.app.data.focus.LiveUsage
import com.focusforce.app.data.focus.NotificationIds
import com.focusforce.app.data.focus.Notifier
import com.focusforce.app.data.usage.ExcludedPackages
import com.focusforce.app.domain.blocking.BlockContext
import com.focusforce.app.domain.blocking.BlockDecision
import com.focusforce.app.domain.blocking.BlockDecisionEngine
import com.focusforce.app.domain.blocking.BlockSettings
import com.focusforce.app.domain.blocking.PassState
import com.focusforce.app.domain.blocking.SafetyPackages
import com.focusforce.app.domain.limits.AppGroup
import com.focusforce.app.domain.limits.LimitLevel
import com.focusforce.app.domain.limits.LimitMath
import com.focusforce.app.domain.limits.LimitNotifier
import com.focusforce.app.domain.limits.LimitRule
import com.focusforce.app.domain.pomodoro.Pomodoro
import com.focusforce.app.domain.pomodoro.PomodoroRun
import com.focusforce.app.domain.repo.BlockSettingsRepository
import com.focusforce.app.domain.repo.BlockedAppsRepository
import com.focusforce.app.domain.repo.FocusRepository
import com.focusforce.app.domain.repo.UsageRepository
import com.focusforce.app.domain.schedule.FocusSchedule
import com.focusforce.app.domain.schedule.ScheduleMatcher
import com.focusforce.app.domain.usage.AppCategory
import com.focusforce.app.domain.usage.Insights
import com.focusforce.app.ui.block.BlockActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Receives "this app came to the front" from the accessibility service (primary) or the usage polling loop
 * (fallback), decides from an in-memory snapshot, and opens the block screen. The snapshot is rebuilt from
 * the database/DataStore, so it survives process death.
 *
 * Limits need today's real usage, which costs a UsageStats query, so that is only fetched (and cached for a
 * few seconds) when the app in front actually has a limit. [recheck] re-evaluates the app in front on a timer,
 * so a limit, schedule or Pomodoro phase that begins while you are already inside an app still catches you.
 */
@Singleton
class BlockCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    settingsRepository: BlockSettingsRepository,
    blockedApps: BlockedAppsRepository,
    focusRepository: FocusRepository,
    usageRepository: UsageRepository,
    prefs: FocusPrefs,
    private val liveUsage: LiveUsage,
    private val notifier: Notifier,
    private val appInfo: AppInfoProvider,
    private val focusPrefs: FocusPrefs,
    private val excluded: ExcludedPackages,
) {
    private data class Core(val settings: BlockSettings, val blocked: Set<String>, val pass: PassState)
    private data class Focus(val limits: List<LimitRule>, val groups: Map<Long, AppGroup>, val schedules: List<FocusSchedule>, val run: PomodoroRun?)
    private data class Snapshot(val core: Core, val focus: Focus, val distracting: Set<String>)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var snapshot: Snapshot? = null
    @Volatile private var neverBlock: Set<String> = SafetyPackages.neverBlock
    @Volatile private var neverBlockAtMs = 0L
    @Volatile private var lastBlockPackage: String? = null
    @Volatile private var lastBlockAtMs = 0L
    @Volatile private var lastPackage: String? = null
    @Volatile private var lastClass: String = ""

    /** Set by the accessibility service; the polling fallback stays idle while this is true. */
    @Volatile var accessibilityConnected: Boolean = false

    init {
        val today = LocalDate.now()
        val core = combine(settingsRepository.settings, blockedApps.observe(), settingsRepository.passState, ::Core)
        val focus = combine(
            focusRepository.observeLimits(), focusRepository.observeGroups(), focusRepository.observeSchedules(), prefs.pomodoroRun,
        ) { l, g, s, r -> Focus(l, g.associateBy { it.id }, s, r) }
        // Apps used in the last 30 days whose category is DISTRACTING: what a Pomodoro focus block also keeps you out of.
        val distracting = combine(usageRepository.observeApps(today.minusDays(29), today), usageRepository.observeCategoryOverrides()) { rows, overrides ->
            Insights.topApps(rows).map { it.packageName }.filterTo(mutableSetOf()) { appInfo.resolveCategory(it, overrides) == AppCategory.DISTRACTING }
        }
        scope.launch { combine(core, focus, distracting, ::Snapshot).collect { snapshot = it } }
    }

    fun onForeground(packageName: String?, className: String?) {
        val pkg = packageName ?: return
        if (pkg != context.packageName) {
            lastPackage = pkg
            lastClass = className.orEmpty()
        }
        evaluate(pkg, className.orEmpty())
    }

    /** Re-evaluates the last app seen in front. Called every few seconds by the foreground service. */
    fun recheck() {
        val pkg = lastPackage ?: return
        evaluate(pkg, lastClass)
    }

    private fun evaluate(pkg: String, cls: String) {
        val snap = snapshot ?: return
        val day = LocalDate.now().dayOfWeek
        val limited = snap.core.settings.enabled && pkg in LimitMath.limitedPackages(snap.focus.limits, snap.focus.groups, day)
        if (limited) {
            scope.launch {
                val usage = liveUsage.refreshIfStale()
                sendLimitNotifications(snap, usage)
                decide(pkg, cls, snap, usage)
            }
        } else {
            decide(pkg, cls, snap, emptyMap())
        }
    }

    private fun decide(pkg: String, cls: String, snap: Snapshot, usage: Map<String, Long>) {
        val nowMs = System.currentTimeMillis()
        val nowTime = LocalDateTime.now()
        val run = snap.focus.run?.let { Pomodoro.advance(it, nowMs).first }
        val ctx = BlockContext(
            settings = snap.core.settings,
            blocked = snap.core.blocked,
            pass = snap.core.pass,
            ownPackage = context.packageName,
            neverBlock = neverBlockSet(nowMs),
            limitReached = LimitMath.reachedPackages(snap.focus.limits, snap.focus.groups, usage, nowTime.dayOfWeek),
            scheduleBlocked = ScheduleMatcher.blockedPackages(snap.focus.schedules, nowTime),
            pomodoroBlocked = if (Pomodoro.isBlocking(run)) snap.core.blocked + snap.distracting else emptySet(),
        )
        val decision = BlockDecisionEngine.decide(pkg, cls, ctx, nowMs, LocalDate.now().toEpochDay())
        if (decision !is BlockDecision.Block) return
        // Repeated window events from the same app within a moment would relaunch the screen over and over.
        if (decision.packageName == lastBlockPackage && nowMs - lastBlockAtMs < DEDUPE_MS) return
        lastBlockPackage = decision.packageName
        lastBlockAtMs = nowMs
        show(decision)
    }

    private suspend fun sendLimitNotifications(snap: Snapshot, usage: Map<String, Long>) {
        val today = LocalDate.now()
        val (events, state) = LimitNotifier.due(snap.focus.limits, snap.focus.groups, usage, today.dayOfWeek, today.toEpochDay(), focusPrefs.limitNotifyState())
        if (events.isEmpty()) return
        focusPrefs.setLimitNotifyState(state)
        for (e in events) {
            val name = when (e.rule.kind) {
                com.focusforce.app.domain.limits.LimitKind.APP -> appInfo.label(e.rule.target)
                com.focusforce.app.domain.limits.LimitKind.GROUP -> e.rule.target.toLongOrNull()?.let { snap.focus.groups[it]?.name } ?: e.rule.target
            }
            val id = NotificationIds.LIMIT_BASE + (e.rule.id % 900).toInt()
            if (e.level == LimitLevel.REACHED) {
                notifier.alert(id, context.getString(R.string.notif_limit_reached_title, name), context.getString(R.string.notif_limit_reached_text))
            } else {
                notifier.alert(id, context.getString(R.string.notif_limit_warn_title, name), context.getString(R.string.notif_limit_warn_text, (e.usedMs / 60_000L).toInt(), (e.rule.dailyLimitMs / 60_000L).toInt()))
            }
        }
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
