package com.focusforce.app.domain.repo

import com.focusforce.app.domain.limits.AppGroup
import com.focusforce.app.domain.limits.LimitRule
import com.focusforce.app.domain.schedule.FocusSchedule
import kotlinx.coroutines.flow.Flow

data class PomodoroSessionRecord(val id: Long, val startedAtMs: Long, val endedAtMs: Long, val minutes: Int, val completed: Boolean)

/** Limits, groups, focus schedules and Pomodoro history. Writes that loosen anything go through FocusManager. */
interface FocusRepository {
    fun observeLimits(): Flow<List<LimitRule>>
    suspend fun saveLimit(rule: LimitRule): Long
    suspend fun deleteLimit(id: Long)

    fun observeGroups(): Flow<List<AppGroup>>
    suspend fun saveGroup(group: AppGroup): Long
    suspend fun deleteGroup(id: Long)

    fun observeSchedules(): Flow<List<FocusSchedule>>
    suspend fun schedule(id: Long): FocusSchedule?
    suspend fun saveSchedule(schedule: FocusSchedule): Long
    suspend fun deleteSchedule(id: Long)

    fun observeSessions(sinceMs: Long): Flow<List<PomodoroSessionRecord>>
    suspend fun addSession(record: PomodoroSessionRecord)
}
