package com.focusforce.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [UsageDailyEntity::class, UsageHourlyEntity::class, DailySummaryEntity::class, AppCategoryEntity::class, BlockedAppEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun usageDao(): UsageDao
    abstract fun categoryDao(): CategoryDao
    abstract fun blockedAppDao(): BlockedAppDao

    companion object {
        const val NAME = "focusforce.db"
    }
}
