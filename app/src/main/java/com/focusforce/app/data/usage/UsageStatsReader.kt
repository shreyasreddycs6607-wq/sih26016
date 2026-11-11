package com.focusforce.app.data.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import com.focusforce.app.domain.usage.UsageEventRecord
import com.focusforce.app.domain.usage.UsageEventType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Events and unlock count for one time window. [unlocks] is null where Android cannot report it. */
data class RawUsage(val events: List<UsageEventRecord>, val unlocks: Int?)

/**
 * Thin wrapper over UsageStatsManager.queryEvents. Only package name, activity class name and
 * timestamps are read; nothing about what the user does inside an app is available or stored.
 */
@Singleton
class UsageStatsReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager: UsageStatsManager = context.getSystemService(UsageStatsManager::class.java)

    @Suppress("DEPRECATION") // MOVE_TO_FOREGROUND/BACKGROUND (=1/=2) are the only constants valid on API 26-28
    fun read(startMs: Long, endMs: Long): RawUsage {
        val events = manager.queryEvents(startMs, endMs)
        val event = UsageEvents.Event()
        val out = ArrayList<UsageEventRecord>()
        var keyguardHidden = 0
        var screenOn = 0
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND ->
                    out += UsageEventRecord(event.timeStamp, event.packageName, event.className.orEmpty(), UsageEventType.RESUMED)
                UsageEvents.Event.MOVE_TO_BACKGROUND ->
                    out += UsageEventRecord(event.timeStamp, event.packageName, event.className.orEmpty(), UsageEventType.PAUSED)
                else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    when (event.eventType) {
                        UsageEvents.Event.KEYGUARD_HIDDEN -> keyguardHidden++
                        UsageEvents.Event.SCREEN_INTERACTIVE -> screenOn++
                    }
                }
            }
        }
        // Unlocks = keyguard dismissals. Phones with no lock screen emit none, so fall back to screen-on count.
        val unlocks = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (keyguardHidden > 0) keyguardHidden else screenOn
        } else {
            null
        }
        return RawUsage(out, unlocks)
    }
}
