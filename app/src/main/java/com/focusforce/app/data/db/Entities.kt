package com.focusforce.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One app on one local day. `epochDay` = LocalDate.toEpochDay(). */
@Entity(
    tableName = "usage_daily",
    primaryKeys = ["epochDay", "packageName"],
    indices = [Index("packageName")],
)
data class UsageDailyEntity(
    val epochDay: Long,
    val packageName: String,
    val foregroundMs: Long,
    val launches: Int,
)

/** Hour-of-day histogram cell; feeds the peak-hours heat map. */
@Entity(tableName = "usage_hourly", primaryKeys = ["epochDay", "hour"])
data class UsageHourlyEntity(
    val epochDay: Long,
    val hour: Int,
    val foregroundMs: Long,
)

/**
 * One row per synced day (even a day with zero usage), so "no data" and "zero usage" differ.
 * `unlocks` is null where Android cannot report it.
 */
@Entity(tableName = "daily_summary")
data class DailySummaryEntity(
    @PrimaryKey val epochDay: Long,
    val totalMs: Long,
    val unlocks: Int?,
    val syncedAtMs: Long,
)

@Entity(tableName = "app_category")
data class AppCategoryEntity(
    @PrimaryKey val packageName: String,
    val category: String,
)
