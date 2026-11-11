package com.focusforce.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class UsageDao {
    @Query("SELECT * FROM usage_daily WHERE epochDay BETWEEN :from AND :to")
    abstract fun observeApps(from: Long, to: Long): Flow<List<UsageDailyEntity>>

    @Query("SELECT * FROM usage_daily WHERE packageName = :packageName AND epochDay BETWEEN :from AND :to ORDER BY epochDay")
    abstract fun observeAppDaily(packageName: String, from: Long, to: Long): Flow<List<UsageDailyEntity>>

    @Query("SELECT * FROM daily_summary WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay")
    abstract fun observeSummaries(from: Long, to: Long): Flow<List<DailySummaryEntity>>

    @Query("SELECT * FROM usage_hourly WHERE epochDay BETWEEN :from AND :to")
    abstract fun observeHourly(from: Long, to: Long): Flow<List<UsageHourlyEntity>>

    @Query("SELECT MAX(epochDay) FROM daily_summary")
    abstract suspend fun latestSyncedDay(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertApps(rows: List<UsageDailyEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertHours(rows: List<UsageHourlyEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertSummary(row: DailySummaryEntity)

    @Query("DELETE FROM usage_daily WHERE epochDay = :epochDay")
    abstract suspend fun deleteApps(epochDay: Long)

    @Query("DELETE FROM usage_hourly WHERE epochDay = :epochDay")
    abstract suspend fun deleteHours(epochDay: Long)

    @Query("DELETE FROM usage_daily WHERE epochDay < :epochDay")
    abstract suspend fun pruneApps(epochDay: Long)

    @Query("DELETE FROM usage_hourly WHERE epochDay < :epochDay")
    abstract suspend fun pruneHours(epochDay: Long)

    @Query("DELETE FROM daily_summary WHERE epochDay < :epochDay")
    abstract suspend fun pruneSummaries(epochDay: Long)

    /** Atomically replaces everything stored for one day. */
    @Transaction
    open suspend fun replaceDay(
        epochDay: Long,
        apps: List<UsageDailyEntity>,
        hours: List<UsageHourlyEntity>,
        summary: DailySummaryEntity,
    ) {
        deleteApps(epochDay)
        deleteHours(epochDay)
        insertApps(apps)
        insertHours(hours)
        insertSummary(summary)
    }

    @Transaction
    open suspend fun pruneBefore(epochDay: Long) {
        pruneApps(epochDay)
        pruneHours(epochDay)
        pruneSummaries(epochDay)
    }
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM app_category")
    fun observeAll(): Flow<List<AppCategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: AppCategoryEntity)

    @Query("DELETE FROM app_category WHERE packageName = :packageName")
    suspend fun delete(packageName: String)
}
