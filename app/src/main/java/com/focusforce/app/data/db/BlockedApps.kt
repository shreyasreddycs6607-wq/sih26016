package com.focusforce.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "blocked_app")
data class BlockedAppEntity(
    @PrimaryKey val packageName: String,
    val addedAtMs: Long,
)

@Dao
interface BlockedAppDao {
    @Query("SELECT packageName FROM blocked_app")
    fun observePackages(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(rows: List<BlockedAppEntity>)

    @Query("DELETE FROM blocked_app WHERE packageName IN (:packageNames)")
    suspend fun delete(packageNames: List<String>)
}

/** v1 (Phase 2) -> v2 (Phase 3): adds the blocked-apps table. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `blocked_app` (`packageName` TEXT NOT NULL, `addedAtMs` INTEGER NOT NULL, PRIMARY KEY(`packageName`))")
    }
}
