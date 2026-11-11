package com.focusforce.app.ui.focus

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.focusforce.app.R
import com.focusforce.app.data.blocking.Outcome
import com.focusforce.app.data.focus.FocusManager
import com.focusforce.app.domain.limits.AppGroup
import com.focusforce.app.domain.limits.DaysMask
import com.focusforce.app.domain.limits.LimitKind
import com.focusforce.app.domain.limits.LimitMath
import com.focusforce.app.domain.limits.LimitRule
import com.focusforce.app.domain.repo.FocusRepository
import com.focusforce.app.domain.schedule.FocusSchedule
import com.focusforce.app.domain.schedule.SchedulePreset
import com.focusforce.app.domain.schedule.SiteList
import com.focusforce.app.ui.blocks.PickerBridge
import com.focusforce.app.ui.nav.GroupEditor
import com.focusforce.app.ui.util.noticeRes
import com.focusforce.app.ui.nav.LimitEditor
import com.focusforce.app.ui.nav.ScheduleEditor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val DAY_MIN = 24 * 60

/** Shared bits: a one-off error message and a "saved, close the screen" flag. */
abstract class EditorViewModel : ViewModel() {
    protected val _notice = MutableStateFlow<Int?>(null)
    val notice: StateFlow<Int?> = _notice
    protected val _finished = MutableStateFlow(false)
    val finished: StateFlow<Boolean> = _finished
    fun clearNotice() { _notice.value = null }

    protected fun handle(outcome: Outcome) {
        if (outcome == Outcome.OK) _finished.value = true else _notice.value = outcome.noticeRes() ?: R.string.notice_strict_locked
    }
}

// ---- Schedule ------------------------------------------------------------------------------------------

data class ScheduleDraft(
    val id: Long,
    val name: String,
    val daysMask: Int,
    val startMin: Int,
    val endMin: Int,
    val sites: String,
    val silence: Boolean,
    val enabled: Boolean,
)

@HiltViewModel
class ScheduleEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: FocusRepository,
    private val manager: FocusManager,
    private val bridge: PickerBridge,
) : EditorViewModel() {

    private val id = savedStateHandle.toRoute<ScheduleEditor>().id
    private val _draft = MutableStateFlow<ScheduleDraft?>(null)
    val draft: StateFlow<ScheduleDraft?> = _draft
    val packages: StateFlow<Set<String>> = bridge.selection

    init {
        viewModelScope.launch {
            val existing = if (id != 0L) repository.schedule(id) else null
            bridge.begin(existing?.packages ?: emptySet())
            _draft.value = existing?.let { ScheduleDraft(it.id, it.name, it.daysMask, it.startMin, it.endMin, it.sites.sorted().joinToString(", "), it.silenceNotifications, it.enabled) }
                ?: ScheduleDraft(0, "", DaysMask.WEEKDAYS, 16 * 60, 18 * 60, "", true, true)
        }
    }

    private fun edit(transform: (ScheduleDraft) -> ScheduleDraft) = _draft.update { it?.let(transform) }

    fun setName(v: String) = edit { it.copy(name = v) }
    fun setDays(mask: Int) = edit { it.copy(daysMask = mask) }
    fun shiftStart(deltaMin: Int) = edit { it.copy(startMin = Math.floorMod(it.startMin + deltaMin, DAY_MIN)) }
    fun shiftEnd(deltaMin: Int) = edit { it.copy(endMin = Math.floorMod(it.endMin + deltaMin, DAY_MIN)) }
    fun setSites(v: String) = edit { it.copy(sites = v) }
    fun setSilence(v: Boolean) = edit { it.copy(silence = v) }
    fun applyPreset(preset: SchedulePreset, name: String) = edit {
        it.copy(name = name, daysMask = preset.daysMask, startMin = preset.startMin, endMin = preset.endMin, silence = preset.silence)
    }

    fun save() {
        val d = _draft.value ?: return
        when {
            d.name.isBlank() -> { _notice.value = R.string.editor_need_name; return }
            d.startMin == d.endMin -> { _notice.value = R.string.editor_need_times; return }
            d.daysMask == 0 -> { _notice.value = R.string.editor_need_days; return }
        }
        viewModelScope.launch {
            handle(manager.saveSchedule(FocusSchedule(d.id, d.name.trim(), d.daysMask, d.startMin, d.endMin, bridge.selection.value, SiteList.parse(d.sites), d.silence, d.enabled)))
        }
    }

    fun delete() { if (id != 0L) viewModelScope.launch { handle(manager.deleteSchedule(id)) } else _finished.value = true }
    val isNew: Boolean get() = id == 0L
}

// ---- Limit ---------------------------------------------------------------------------------------------

data class LimitDraft(val id: Long, val kind: LimitKind, val groupId: Long?, val limitMs: Long, val daysMask: Int, val enabled: Boolean)

@HiltViewModel
class LimitEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: FocusRepository,
    private val manager: FocusManager,
    private val bridge: PickerBridge,
) : EditorViewModel() {

    private val id = savedStateHandle.toRoute<LimitEditor>().id
    private val _draft = MutableStateFlow<LimitDraft?>(null)
    val draft: StateFlow<LimitDraft?> = _draft

    /** The app chosen for an APP limit: whatever the picker last left in the bridge. */
    val chosenApp: StateFlow<String?> = bridge.selection
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val groups: StateFlow<List<AppGroup>> = repository.observeGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val existing = if (id != 0L) repository.observeLimits().first().firstOrNull { it.id == id } else null
            bridge.begin(if (existing?.kind == LimitKind.APP) setOf(existing.target) else emptySet())
            _draft.value = if (existing == null) {
                LimitDraft(0, LimitKind.APP, null, 60 * 60_000L, DaysMask.ALL, true)
            } else {
                val group = if (existing.kind == LimitKind.GROUP) existing.target.toLongOrNull() else null
                LimitDraft(existing.id, existing.kind, group, existing.dailyLimitMs, existing.daysMask, existing.enabled)
            }
        }
    }

    private fun edit(transform: (LimitDraft) -> LimitDraft) = _draft.update { it?.let(transform) }
    fun setKind(kind: LimitKind) = edit { it.copy(kind = kind) }
    fun setGroup(groupId: Long) = edit { it.copy(groupId = groupId) }
    fun setDays(mask: Int) = edit { it.copy(daysMask = mask) }
    fun shiftLimit(steps: Int) = edit {
        it.copy(limitMs = (it.limitMs + steps * LimitMath.STEP_MS).coerceIn(LimitMath.MIN_LIMIT_MS, LimitMath.MAX_LIMIT_MS))
    }

    fun save() {
        val d = _draft.value ?: return
        val target = if (d.kind == LimitKind.APP) chosenApp.value else d.groupId?.toString()
        if (target.isNullOrBlank()) {
            _notice.value = if (d.kind == LimitKind.APP) R.string.editor_need_app else R.string.editor_need_group
            return
        }
        viewModelScope.launch { handle(manager.saveLimit(LimitRule(d.id, d.kind, target, d.limitMs, d.daysMask, d.enabled))) }
    }

    fun delete() { if (id != 0L) viewModelScope.launch { handle(manager.deleteLimit(id)) } else _finished.value = true }
    val isNew: Boolean get() = id == 0L
}

// ---- Group ---------------------------------------------------------------------------------------------

@HiltViewModel
class GroupEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: FocusRepository,
    private val manager: FocusManager,
    private val bridge: PickerBridge,
) : EditorViewModel() {

    private val id = savedStateHandle.toRoute<GroupEditor>().id
    private val _name = MutableStateFlow<String?>(null)
    val name: StateFlow<String?> = _name
    val packages: StateFlow<Set<String>> = bridge.selection

    init {
        viewModelScope.launch {
            val existing = if (id != 0L) repository.observeGroups().first().firstOrNull { it.id == id } else null
            bridge.begin(existing?.packages ?: emptySet())
            _name.value = existing?.name ?: ""
        }
    }

    fun setName(v: String) { _name.value = v }

    fun save() {
        val n = _name.value?.trim().orEmpty()
        if (n.isEmpty()) { _notice.value = R.string.editor_need_name; return }
        if (bridge.selection.value.isEmpty()) { _notice.value = R.string.editor_need_apps; return }
        viewModelScope.launch { handle(manager.saveGroup(AppGroup(id, n, bridge.selection.value))) }
    }

    fun delete() { if (id != 0L) viewModelScope.launch { handle(manager.deleteGroup(id)) } else _finished.value = true }
    val isNew: Boolean get() = id == 0L
}
