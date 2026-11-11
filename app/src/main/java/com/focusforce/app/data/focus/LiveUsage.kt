package com.focusforce.app.data.focus

import com.focusforce.app.data.usage.ExcludedPackages
import com.focusforce.app.data.usage.UsageStatsReader
import com.focusforce.app.domain.usage.DayWindow
import com.focusforce.app.domain.usage.UsageAggregator
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Today's foreground time per app, straight from UsageStatsManager (not the 30-minute Room sync), so
 * a daily limit can trip within seconds. Cached for a few seconds because the query is not free.
 */
@Singleton
class LiveUsage @Inject constructor(
    private val reader: UsageStatsReader,
    private val excluded: ExcludedPackages,
) {
    @Volatile private var cache: Map<String, Long> = emptyMap()
    @Volatile private var cacheDay = Long.MIN_VALUE
    @Volatile private var cachedAt = 0L

    /** Last computed values for today; empty if they are from another day. */
    fun cached(): Map<String, Long> = if (cacheDay == LocalDate.now().toEpochDay()) cache else emptyMap()

    suspend fun refreshIfStale(ttlMs: Long = 5_000L): Map<String, Long> = withContext(Dispatchers.Default) {
        val now = System.currentTimeMillis()
        val today = LocalDate.now().toEpochDay()
        if (cacheDay == today && now - cachedAt < ttlMs) return@withContext cache
        val zone = ZoneId.systemDefault()
        val window = DayWindow.of(LocalDate.now(zone), zone, now)
        val skip = excluded.current()
        val day = UsageAggregator.aggregate(reader.read(window.startMs, window.endMs).events, window, zone) { it !in skip }
        cache = day.perApp.mapValues { it.value.foregroundMs }
        cacheDay = today
        cachedAt = now
        cache
    }
}
