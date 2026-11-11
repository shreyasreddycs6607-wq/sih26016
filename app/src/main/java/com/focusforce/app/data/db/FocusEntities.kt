package com.focusforce.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "app_group")
data class AppGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(tableName = "group_member", primaryKeys = ["groupId", "packageName"])
data class GroupMemberEntity(val groupId: Long, val packageName: String)

@Entity(tableName = "limit_rule")
data class LimitRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val target: String,
    val dailyLimitMs: Long,
    val daysMask: Int,
    val enabled: Boolean,
)

/** `packages` and `sites` are comma-separated (package names and domains never contain commas). */
@Entity(tableName = "focus_schedule")
data class FocusScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val daysMask: Int,
    val startMin: Int,
    val endMin: Int,
    val packages: String,
    val sites: String,
    val silenceNotifications: Boolean,
    val enabled: Boolean,
)

@Entity(tableName = "pomodoro_session")
data class PomodoroSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val focusMin: Int,
    val completed: Boolean,
)

@Dao
abstract class FocusDao {
    // groups
    @Query("SELECT * FROM app_group ORDER BY name COLLATE NOCASE")
    abstract fun observeGroups(): Flow<List<AppGroupEntity>>

    @Query("SELECT * FROM group_member")
    abstract fun observeMembers(): Flow<List<GroupMemberEntity>>

    @Insert abstract suspend fun insertGroup(group: AppGroupEntity): Long
    @Update abstract suspend fun updateGroup(group: AppGroupEntity)
    @Query("DELETE FROM app_group WHERE id = :id") abstract suspend fun deleteGroupRow(id: Long)
    @Query("DELETE FROM group_member WHERE groupId = :id") abstract suspend fun deleteMembers(id: Long)
    @Insert(onConflict = OnConflictStrategy.IGNORE) abstract suspend fun insertMembers(rows: List<GroupMemberEntity>)

    @Transaction
    open suspend fun saveGroup(group: AppGroupEntity, packages: Set<String>): Long {
        val id = if (group.id == 0L) insertGroup(group) else group.id.also { updateGroup(group) }
        deleteMembers(id)
        insertMembers(packages.map { GroupMemberEntity(id, it) })
        return id
    }

    @Transaction
    open suspend fun deleteGroup(id: Long) {
        deleteMembers(id)
        deleteGroupRow(id)
    }

    // limits
    @Query("SELECT * FROM limit_rule") abstract fun observeLimits(): Flow<List<LimitRuleEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun upsertLimit(rule: LimitRuleEntity): Long
    @Query("DELETE FROM limit_rule WHERE id = :id") abstract suspend fun deleteLimit(id: Long)
    /** A deleted group must not leave limits pointing at nothing. */
    @Query("DELETE FROM limit_rule WHERE kind = 'GROUP' AND target = :groupId") abstract suspend fun deleteLimitsForGroup(groupId: String)

    // schedules
    @Query("SELECT * FROM focus_schedule ORDER BY startMin") abstract fun observeSchedules(): Flow<List<FocusScheduleEntity>>
    @Query("SELECT * FROM focus_schedule WHERE id = :id") abstract suspend fun schedule(id: Long): FocusScheduleEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun upsertSchedule(row: FocusScheduleEntity): Long
    @Query("DELETE FROM focus_schedule WHERE id = :id") abstract suspend fun deleteSchedule(id: Long)

    // pomodoro history
    @Query("SELECT * FROM pomodoro_session WHERE endedAtMs >= :sinceMs ORDER BY endedAtMs DESC")
    abstract fun observeSessions(sinceMs: Long): Flow<List<PomodoroSessionEntity>>
    @Insert abstract suspend fun insertSession(row: PomodoroSessionEntity): Long
}

/** v2 (Phase 3) -> v3 (Phase 4): groups, limits, focus schedules, Pomodoro history. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `app_group` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `group_member` (`groupId` INTEGER NOT NULL, `packageName` TEXT NOT NULL, PRIMARY KEY(`groupId`, `packageName`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `limit_rule` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `kind` TEXT NOT NULL, `target` TEXT NOT NULL, `dailyLimitMs` INTEGER NOT NULL, `daysMask` INTEGER NOT NULL, `enabled` INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `focus_schedule` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `daysMask` INTEGER NOT NULL, `startMin` INTEGER NOT NULL, `endMin` INTEGER NOT NULL, `packages` TEXT NOT NULL, `sites` TEXT NOT NULL, `silenceNotifications` INTEGER NOT NULL, `enabled` INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `pomodoro_session` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `startedAtMs` INTEGER NOT NULL, `endedAtMs` INTEGER NOT NULL, `focusMin` INTEGER NOT NULL, `completed` INTEGER NOT NULL)")
    }
}
