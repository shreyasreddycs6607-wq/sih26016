package com.focusforce.app.data.focus

import com.focusforce.app.data.db.AppGroupEntity
import com.focusforce.app.data.db.FocusDao
import com.focusforce.app.data.db.FocusScheduleEntity
import com.focusforce.app.data.db.LimitRuleEntity
import com.focusforce.app.data.db.PomodoroSessionEntity
import com.focusforce.app.domain.limits.AppGroup
import com.focusforce.app.domain.limits.LimitKind
import com.focusforce.app.domain.limits.LimitRule
import com.focusforce.app.domain.repo.FocusRepository
import com.focusforce.app.domain.repo.PomodoroSessionRecord
import com.focusforce.app.domain.schedule.FocusSchedule
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

private fun String.toSet(): Set<String> = split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

private fun FocusScheduleEntity.toDomain() = FocusSchedule(
    id, name, daysMask, startMin, endMin, packages.toSet(), sites.toSet(), silenceNotifications, enabled,
)

private fun FocusSchedule.toEntity() = FocusScheduleEntity(
    id, name, daysMask, startMin, endMin, packages.sorted().joinToString(","), sites.sorted().joinToString(","),
    silenceNotifications, enabled,
)

@Singleton
class FocusRepositoryImpl @Inject constructor(
    private val dao: FocusDao,
) : FocusRepository {

    override fun observeLimits(): Flow<List<LimitRule>> = dao.observeLimits().map { rows ->
        rows.mapNotNull { r ->
            LimitKind.entries.firstOrNull { it.name == r.kind }
                ?.let { LimitRule(r.id, it, r.target, r.dailyLimitMs, r.daysMask, r.enabled) }
        }
    }

    override suspend fun saveLimit(rule: LimitRule): Long =
        dao.upsertLimit(LimitRuleEntity(rule.id, rule.kind.name, rule.target, rule.dailyLimitMs, rule.daysMask, rule.enabled))

    override suspend fun deleteLimit(id: Long) = dao.deleteLimit(id)

    override fun observeGroups(): Flow<List<AppGroup>> = combine(dao.observeGroups(), dao.observeMembers()) { groups, members ->
        val byGroup = members.groupBy({ it.groupId }, { it.packageName })
        groups.map { AppGroup(it.id, it.name, byGroup[it.id].orEmpty().toSet()) }
    }

    override suspend fun saveGroup(group: AppGroup): Long = dao.saveGroup(AppGroupEntity(group.id, group.name), group.packages)

    override suspend fun deleteGroup(id: Long) {
        dao.deleteLimitsForGroup(id.toString())
        dao.deleteGroup(id)
    }

    override fun observeSchedules(): Flow<List<FocusSchedule>> = dao.observeSchedules().map { rows -> rows.map { it.toDomain() } }
    override suspend fun schedule(id: Long): FocusSchedule? = dao.schedule(id)?.toDomain()
    override suspend fun saveSchedule(schedule: FocusSchedule): Long = dao.upsertSchedule(schedule.toEntity())
    override suspend fun deleteSchedule(id: Long) = dao.deleteSchedule(id)

    override fun observeSessions(sinceMs: Long): Flow<List<PomodoroSessionRecord>> =
        dao.observeSessions(sinceMs).map { rows -> rows.map { PomodoroSessionRecord(it.id, it.startedAtMs, it.endedAtMs, it.focusMin, it.completed) } }

    override suspend fun addSession(record: PomodoroSessionRecord) {
        dao.insertSession(PomodoroSessionEntity(0, record.startedAtMs, record.endedAtMs, record.minutes, record.completed))
    }
}
