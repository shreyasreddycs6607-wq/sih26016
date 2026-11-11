package com.focusforce.app.domain.repo

import com.focusforce.app.domain.usage.AppCategory
import com.focusforce.app.domain.usage.DayAppUsage
import com.focusforce.app.domain.usage.DaySummary
import com.focusforce.app.domain.usage.HourCell
import com.focusforce.app.domain.usage.SyncResult
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Locally persisted usage history. All ranges are inclusive local dates. */
interface UsageRepository {
    fun observeApps(from: LocalDate, to: LocalDate): Flow<List<DayAppUsage>>
    fun observeAppDaily(packageName: String, from: LocalDate, to: LocalDate): Flow<List<DayAppUsage>>
    fun observeSummaries(from: LocalDate, to: LocalDate): Flow<List<DaySummary>>
    fun observeHourly(from: LocalDate, to: LocalDate): Flow<List<HourCell>>

    /** User overrides only; defaults are resolved by CategoryDefaults. */
    fun observeCategoryOverrides(): Flow<Map<String, AppCategory>>

    /** null removes the override (back to the automatic category). */
    suspend fun setCategory(packageName: String, category: AppCategory?)

    /** Pulls fresh data from UsageStatsManager into the database. */
    suspend fun refresh(): SyncResult
}
