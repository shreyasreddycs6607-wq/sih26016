package com.focusforce.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [UsageDailyEntity::class, UsageHourlyEntity::class, DailySummaryEntity::class, AppCategoryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun usageDao(): UsageDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        const val NAME = "focusforce.db"
    }
}
