package com.focusforce.app.ui.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforce.app.R
import com.focusforce.app.data.apps.AppInfoProvider
import com.focusforce.app.data.blocking.Outcome
import com.focusforce.app.data.focus.FocusManager
import com.focusforce.app.data.focus.FocusPrefs
import com.focusforce.app.data.focus.PomodoroController
import com.focusforce.app.domain.blocking.DetectionMode
import com.focusforce.app.domain.limits.AppGroup
import com.focusforce.app.domain.limits.LimitKind
import com.focusforce.app.domain.limits.LimitMath
import com.focusforce.app.domain.limits.LimitRule
import com.focusforce.app.domain.nudge.NudgeSettings
import com.focusforce.app.domain.pomodoro.PomodoroConfig
import com.focusforce.app.domain.pomodoro.PomodoroEvent
import com.focusforce.app.domain.pomodoro.PomodoroRun
import com.focusforce.app.domain.repo.BlockSettingsRepository
import com.focusforce.app.domain.repo.FocusRepository
import com.focusforce.app.domain.repo.PomodoroSessionRecord
import com.focusforce.app.domain.repo.UsageRepository
import com.focusforce.app.domain.schedule.FocusSchedule
import com.focusforce.app.domain.schedule.ScheduleMatcher
import com.focusforce.app.permissions.PermissionChecker
import com.focusforce.app.permissions.PermissionKind
import com.focusforce.app.ui.components.Moment
import com.focusforce.app.ui.insights.todayFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ScheduleRow(val schedule: FocusSchedule, val active: Boolean)
data class LimitRow(val rule: LimitRule, val label: String, val usedMs: Long)

data class FocusUi(
    val blockingOn: Boolean,
    val strictActive: Boolean,
    val mode: DetectionMode,
    val config: PomodoroConfig,
    val run: PomodoroRun?,
    val silenceDuringPomodoro: Boolean,
    val dndGranted: Boolean,
    val todayCompleted: Int,
    val todayMinutes: Int,
    val weekMinutes: Int,
    val recent: List<PomodoroSessionRecord>,
    val schedules: List<ScheduleRow>,
    val limits: List<LimitRow>,
    val groups: List<AppGroup>,
    val nudge: NudgeSettings,
)

private data class PomodoroPart(val blockingOn: Boolean, val strictUntilMs: Long, val config: PomodoroConfig, val run: PomodoroRun?, val silence: Boolean)
private data class ListsPart(
    val schedules: List<FocusSchedule>,
    val limits: List<LimitRule>,
    val groups: List<AppGroup>,
    val sessions: List<PomodoroSessionRecord>,
    val usageToday: Map<String, Long>,
)

@HiltViewModel
class FocusViewModel @Inject constructor(
    blockSettings: BlockSettingsRepository,
    private val prefs: FocusPrefs,
    repository: FocusRepository,
    usage: UsageRepository,
    private val manager: FocusManager,
    private val pomodoro: PomodoroController,
    private val permissions: PermissionChecker,
    private val appInfo: AppInfoProvider,
) : ViewModel() {

    private val tick = MutableStateFlow(0)
    private val notice = MutableStateFlow<Int?>(null)
    private val _moment = MutableStateFlow<Moment?>(null)
    val moment: StateFlow<Moment?> = _moment
    val noticeRes: StateFlow<Int?> = notice

    init {
        viewModelScope.launch {
            pomodoro.events.collect { if (it is PomodoroEvent.FocusCompleted) _moment.value = Moment.CELEBRATE }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<FocusUi?> = todayFlow().flatMapLatest { today ->
        val zone = ZoneId.systemDefault()
        val weekStartMs = today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
        val a = combine(blockSettings.settings, prefs.pomodoroConfig, prefs.pomodoroRun, prefs.silenceDuringPomodoro) { s, c, r, sil ->
            PomodoroPart(s.enabled, s.strictUntilMs, c, r, sil)
        }
        val b = combine(
            repository.observeSchedules(), repository.observeLimits(), repository.observeGroups(),
            repository.observeSessions(weekStartMs), usage.observeApps(today, today),
        ) { sch, lim, grp, ses, apps ->
            ListsPart(sch, lim, grp, ses, apps.groupBy { it.packageName }.mapValues { (_, v) -> v.sumOf { it.foregroundMs } })
        }
        combine(a, b, prefs.nudge, tick) { p, l, nudge, _ -> build(p, l, nudge, today, weekStartMs) }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun build(p: PomodoroPart, l: ListsPart, nudge: NudgeSettings, today: LocalDate, weekStartMs: Long): FocusUi {
        val zone = ZoneId.systemDefault()
        val nowMs = System.currentTimeMillis()
        val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val groups = l.groups.associateBy { it.id }
        val todaySessions = l.sessions.filter { it.endedAtMs >= dayStart && it.completed }
        return FocusUi(
            blockingOn = p.blockingOn,
            strictActive = p.strictUntilMs > nowMs,
            mode = DetectionMode.from(
                permissions.isGranted(PermissionKind.ACCESSIBILITY),
                permissions.isGranted(PermissionKind.USAGE_ACCESS),
                permissions.isGranted(PermissionKind.OVERLAY),
            ),
            config = p.config,
            run = p.run,
            silenceDuringPomodoro = p.silence,
            dndGranted = permissions.isGranted(PermissionKind.DND_ACCESS),
            todayCompleted = todaySessions.size,
            todayMinutes = todaySessions.sumOf { it.minutes },
            weekMinutes = l.sessions.filter { it.completed }.sumOf { it.minutes },
            recent = l.sessions.take(5),
            schedules = l.schedules.map { ScheduleRow(it, ScheduleMatcher.isActive(it, LocalDateTime.now())) },
            limits = l.limits.map { rule ->
                val label = when (rule.kind) {
                    LimitKind.APP -> appInfo.label(rule.target)
                    LimitKind.GROUP -> rule.target.toLongOrNull()?.let { groups[it]?.name } ?: rule.target
                }
                LimitRow(rule, label, LimitMath.usedMs(rule, groups, l.usageToday))
            },
            groups = l.groups,
            nudge = nudge,
        )
    }

    fun refresh() {
        tick.value++
        viewModelScope.launch { pomodoro.sync() }
    }

    fun clearNotice() { notice.value = null }
    fun clearMoment() { _moment.value = null }

    // ---- Pomodoro -----------------------------------------------------------------------------------

    fun startPomodoro() {
        viewModelScope.launch {
            val ok = DetectionMode.from(
                permissions.isGranted(PermissionKind.ACCESSIBILITY),
                permissions.isGranted(PermissionKind.USAGE_ACCESS),
                permissions.isGranted(PermissionKind.OVERLAY),
            ) != DetectionMode.NONE
            if (!ok) { notice.value = R.string.notice_need_detection; return@launch }
            pomodoro.start()
            _moment.value = Moment.POWER_UP
        }
    }

    fun stopPomodoro() {
        viewModelScope.launch { if (pomodoro.stop() == Outcome.DENIED_STRICT) notice.value = R.string.notice_strict_locked }
    }

    fun updateConfig(transform: (PomodoroConfig) -> PomodoroConfig) {
        val current = state.value?.config ?: PomodoroConfig()
        viewModelScope.launch { prefs.setPomodoroConfig(transform(current)) }
    }

    fun setSilence(on: Boolean) { viewModelScope.launch { prefs.setSilenceDuringPomodoro(on) } }

    // ---- Schedules, limits, nudges -------------------------------------------------------------------

    fun toggleSchedule(row: ScheduleRow) = denyIfStrict { manager.saveSchedule(row.schedule.copy(enabled = !row.schedule.enabled)) }
    fun toggleLimit(row: LimitRow) = denyIfStrict { manager.saveLimit(row.rule.copy(enabled = !row.rule.enabled)) }

    fun setBreakReminders(on: Boolean) = updateNudge { it.copy(breakRemindersEnabled = on) }
    fun setBreakAfter(minutes: Int) = updateNudge { it.copy(breakAfterMin = minutes) }

    private fun updateNudge(transform: (NudgeSettings) -> NudgeSettings) {
        val current = state.value?.nudge ?: NudgeSettings()
        viewModelScope.launch { prefs.setNudge(transform(current)) }
    }

    private fun denyIfStrict(action: suspend () -> Outcome) {
        viewModelScope.launch { if (action() == Outcome.DENIED_STRICT) notice.value = R.string.notice_strict_locked }
    }
}
