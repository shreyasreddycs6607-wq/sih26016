package com.focusforce.app.data.focus

import com.focusforce.app.data.blocking.Outcome
import com.focusforce.app.domain.blocking.BlockingRules
import com.focusforce.app.domain.limits.AppGroup
import com.focusforce.app.domain.limits.LimitRule
import com.focusforce.app.domain.repo.BlockSettingsRepository
import com.focusforce.app.domain.repo.FocusRepository
import com.focusforce.app.domain.schedule.FocusSchedule
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Writer for limits, groups and schedules. During a strict lock existing ones cannot be edited, disabled or
 * deleted (that would loosen the lock); new ones can always be added.
 */
@Singleton
class FocusManager @Inject constructor(
    private val repository: FocusRepository,
    private val settings: BlockSettingsRepository,
    private val environment: FocusEnvironment,
) {
    private suspend fun locked(): Boolean = !BlockingRules.canLoosen(settings.settings.first(), System.currentTimeMillis())

    suspend fun saveLimit(rule: LimitRule): Outcome {
        if (rule.id != 0L && locked()) return Outcome.DENIED_STRICT
        repository.saveLimit(rule)
        return Outcome.OK
    }

    suspend fun deleteLimit(id: Long): Outcome {
        if (locked()) return Outcome.DENIED_STRICT
        repository.deleteLimit(id)
        return Outcome.OK
    }

    suspend fun saveGroup(group: AppGroup): Outcome {
        if (group.id != 0L && locked()) return Outcome.DENIED_STRICT
        repository.saveGroup(group)
        return Outcome.OK
    }

    suspend fun deleteGroup(id: Long): Outcome {
        if (locked()) return Outcome.DENIED_STRICT
        repository.deleteGroup(id)
        return Outcome.OK
    }

    suspend fun saveSchedule(schedule: FocusSchedule): Outcome {
        if (schedule.id != 0L && locked()) return Outcome.DENIED_STRICT
        repository.saveSchedule(schedule)
        environment.refresh()
        return Outcome.OK
    }

    suspend fun deleteSchedule(id: Long): Outcome {
        if (locked()) return Outcome.DENIED_STRICT
        repository.deleteSchedule(id)
        environment.refresh()
        return Outcome.OK
    }
}
