package com.focusforce.app.data.usage

import com.focusforce.app.data.db.DailySummaryEntity
import com.focusforce.app.data.db.UsageDailyEntity
import com.focusforce.app.data.db.UsageDao
import com.focusforce.app.data.db.UsageHourlyEntity
import com.focusforce.app.domain.usage.DayWindow
import com.focusforce.app.domain.usage.SyncResult
import com.focusforce.app.domain.usage.UsageAggregator
import com.focusforce.app.permissions.PermissionChecker
import com.focusforce.app.permissions.PermissionKind
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Reads UsageStatsManager and persists per-day results to Room.
 *
 * Android only keeps usage events for roughly the last 7 days, so history beyond that exists only
 * because we sync it into Room; days FocusForce was never able to sync stay empty (shown as "no data").
 */
@Singleton
class UsageSyncer @Inject constructor(
    private val reader: UsageStatsReader,
    private val excluded: ExcludedPackages,
    private val dao: UsageDao,
    private val permissions: PermissionChecker,
) {
    private val mutex = Mutex()

    suspend fun sync(
        zone: ZoneId = ZoneId.systemDefault(),
        nowMs: Long = System.currentTimeMillis(),
    ): SyncResult = withContext(Dispatchers.Default) {
        if (!permissions.isGranted(PermissionKind.USAGE_ACCESS)) return@withContext SyncResult.PERMISSION_MISSING
        mutex.withLock {
            val today = LocalDate.now(zone)
            val newest = dao.latestSyncedDay()
            // First run: backfill what Android still has. Later: everything since the last sync (covers
            // days the app was not opened), always re-syncing the last synced day because it was partial.
            val span = if (newest == null) BACKFILL_DAYS else
                (today.toEpochDay() - newest + 1).coerceIn(1L, BACKFILL_DAYS.toLong()).toInt()
            val skip = excluded.current()
            for (offset in (span - 1) downTo 0) syncDay(today.minusDays(offset.toLong()), zone, nowMs, skip)
            dao.pruneBefore(today.toEpochDay() - RETENTION_DAYS)
            SyncResult.SYNCED
        }
    }

    private suspend fun syncDay(date: LocalDate, zone: ZoneId, nowMs: Long, skip: Set<String>) {
        val window = DayWindow.of(date, zone, nowMs)
        val raw = reader.read(window.startMs, window.endMs)
        val day = UsageAggregator.aggregate(raw.events, window, zone) { it !in skip }
        val epochDay = date.toEpochDay()
        dao.replaceDay(
            epochDay = epochDay,
            apps = day.perApp.map { (pkg, s) -> UsageDailyEntity(epochDay, pkg, s.foregroundMs, s.launches) },
            hours = day.hourlyMs.mapIndexedNotNull { h, ms -> if (ms > 0) UsageHourlyEntity(epochDay, h, ms) else null },
            summary = DailySummaryEntity(epochDay, day.totalMs, raw.unlocks, nowMs),
        )
    }

    private companion object {
        const val BACKFILL_DAYS = 7
        const val RETENTION_DAYS = 400L
    }
}
