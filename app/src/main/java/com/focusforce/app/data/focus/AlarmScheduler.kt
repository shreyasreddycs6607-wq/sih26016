package com.focusforce.app.data.focus

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build

/** Exact alarm when the user granted "Alarms and reminders", otherwise a Doze-friendly inexact one. */
object AlarmScheduler {
    fun setAt(context: Context, pending: PendingIntent, atMs: Long) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pending)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pending)
            }
        } catch (_: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pending)
        }
    }

    fun cancel(context: Context, pending: PendingIntent) {
        context.getSystemService(AlarmManager::class.java).cancel(pending)
    }
}
