package com.focusforce.app.data.usage

import com.focusforce.app.data.db.AppCategoryEntity
import com.focusforce.app.data.db.CategoryDao
import com.focusforce.app.data.db.UsageDao
import com.focusforce.app.domain.repo.UsageRepository
import com.focusforce.app.domain.usage.AppCategory
import com.focusforce.app.domain.usage.DayAppUsage
import com.focusforce.app.domain.usage.DaySummary
import com.focusforce.app.domain.usage.HourCell
import com.focusforce.app.domain.usage.SyncResult
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class UsageRepositoryImpl @Inject constructor(
    private val usageDao: UsageDao,
    private val categoryDao: CategoryDao,
    private val syncer: UsageSyncer,
) : UsageRepository {

    override fun observeApps(from: LocalDate, to: LocalDate): Flow<List<DayAppUsage>> =
        usageDao.observeApps(from.toEpochDay(), to.toEpochDay()).map { rows ->
            rows.map { DayAppUsage(LocalDate.ofEpochDay(it.epochDay), it.packageName, it.foregroundMs, it.launches) }
        }

    override fun observeAppDaily(packageName: String, from: LocalDate, to: LocalDate): Flow<List<DayAppUsage>> =
        usageDao.observeAppDaily(packageName, from.toEpochDay(), to.toEpochDay()).map { rows ->
            rows.map { DayAppUsage(LocalDate.ofEpochDay(it.epochDay), it.packageName, it.foregroundMs, it.launches) }
        }

    override fun observeSummaries(from: LocalDate, to: LocalDate): Flow<List<DaySummary>> =
        usageDao.observeSummaries(from.toEpochDay(), to.toEpochDay()).map { rows ->
            rows.map { DaySummary(LocalDate.ofEpochDay(it.epochDay), it.totalMs, it.unlocks) }
        }

    override fun observeHourly(from: LocalDate, to: LocalDate): Flow<List<HourCell>> =
        usageDao.observeHourly(from.toEpochDay(), to.toEpochDay()).map { rows ->
            rows.map { HourCell(LocalDate.ofEpochDay(it.epochDay), it.hour, it.foregroundMs) }
        }

    override fun observeCategoryOverrides(): Flow<Map<String, AppCategory>> =
        categoryDao.observeAll().map { rows ->
            rows.mapNotNull { r -> AppCategory.entries.firstOrNull { it.name == r.category }?.let { r.packageName to it } }.toMap()
        }

    override suspend fun setCategory(packageName: String, category: AppCategory?) {
        if (category == null) categoryDao.delete(packageName)
        else categoryDao.upsert(AppCategoryEntity(packageName, category.name))
    }

    override suspend fun refresh(): SyncResult = syncer.sync()
}
