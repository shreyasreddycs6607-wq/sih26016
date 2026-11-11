package com.focusforce.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [UsageDailyEntity::class, UsageHourlyEntity::class, DailySummaryEntity::class, AppCategoryEntity::class, BlockedAppEntity::class,
        AppGroupEntity::class, GroupMemberEntity::class, LimitRuleEntity::class, FocusScheduleEntity::class, PomodoroSessionEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun usageDao(): UsageDao
    abstract fun categoryDao(): CategoryDao
    abstract fun blockedAppDao(): BlockedAppDao
    abstract fun focusDao(): FocusDao

    companion object {
        const val NAME = "focusforce.db"
    }
}
