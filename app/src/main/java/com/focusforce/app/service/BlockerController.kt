package com.focusforce.app.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Starts/stops the foreground service. Starting can be refused by Android 12+ when the app is in the background. */
object BlockerController {
    fun ensureRunning(context: Context) {
        try {
            ContextCompat.startForegroundService(context, Intent(context, BlockerService::class.java))
        } catch (_: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (API 31+): app is in the background. The
            // accessibility service keeps blocking on its own; the next time the app opens we try again.
        } catch (_: SecurityException) {
            // Missing foreground-service permission on this OEM build; nothing more we can do.
        }
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, BlockerService::class.java))
    }
}
