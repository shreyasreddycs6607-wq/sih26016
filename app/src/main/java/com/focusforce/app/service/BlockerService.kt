package com.focusforce.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.focusforce.app.MainActivity
import com.focusforce.app.R
import com.focusforce.app.data.blocking.BlockCoordinator
import com.focusforce.app.data.focus.BreakNudger
import com.focusforce.app.data.focus.FocusPrefs
import com.focusforce.app.data.focus.PomodoroController
import com.focusforce.app.data.usage.UsageStatsReader
import com.focusforce.app.domain.pomodoro.PomodoroPhase
import com.focusforce.app.domain.pomodoro.PomodoroRun
import com.focusforce.app.domain.blocking.ForegroundResolver
import com.focusforce.app.domain.repo.BlockSettingsRepository
import com.focusforce.app.domain.repo.BlockedAppsRepository
import com.focusforce.app.permissions.PermissionChecker
import com.focusforce.app.permissions.PermissionKind
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps blocking alive: shows the persistent notification Android requires, runs the UsageStats polling
 * fallback when the accessibility service is not connected, and keeps a 1-pixel overlay so the block screen
 * may be launched from the background on Android 15. Restarts itself (START_STICKY), is restarted at boot
 * by [BootReceiver], and stops itself when blocking is switched off.
 */
@AndroidEntryPoint
class BlockerService : Service() {

    @Inject lateinit var coordinator: BlockCoordinator
    @Inject lateinit var reader: UsageStatsReader
    @Inject lateinit var permissions: PermissionChecker
    @Inject lateinit var settings: BlockSettingsRepository
    @Inject lateinit var blockedApps: BlockedAppsRepository
    @Inject lateinit var prefs: FocusPrefs
    @Inject lateinit var pomodoro: PomodoroController
    @Inject lateinit var breakNudger: BreakNudger

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var anchor: View? = null
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(0, false, null),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        if (!started) {
            started = true
            addOverlayAnchor()
            scope.launch { observeState() }
            scope.launch { pollFallback() }
            scope.launch { housekeeping() }
        }
        return START_STICKY
    }

    private suspend fun observeState() {
        combine(settings.settings, blockedApps.observe(), prefs.pomodoroRun) { s, apps, run -> Triple(s, apps.size, run) }.collect { (s, count, run) ->
            if (!s.enabled) {
                stopSelf()
            } else {
                getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID, buildNotification(count, s.strictActive(System.currentTimeMillis()), run))
            }
        }
    }

    /** Fallback detector: used only while the accessibility service is not connected and the screen is on. */
    private suspend fun pollFallback() {
        val power = getSystemService(PowerManager::class.java)
        while (scope.isActive) {
            if (!coordinator.accessibilityConnected && power.isInteractive && permissions.isGranted(PermissionKind.USAGE_ACCESS)) {
                val now = System.currentTimeMillis()
                ForegroundResolver.latest(reader.read(now - LOOKBACK_MS, now).events)
                    ?.let { coordinator.onForeground(it.packageName, it.className) }
            }
            delay(POLL_MS)
        }
    }

    /**
     * Timer work that no event triggers: re-check the app in front (a limit, schedule or focus block may have
     * just started), advance Pomodoro if its alarm was missed, and send break reminders every two minutes.
     */
    private suspend fun housekeeping() {
        val power = getSystemService(PowerManager::class.java)
        var tick = 0
        while (scope.isActive) {
            if (power.isInteractive) {
                coordinator.recheck()
                pomodoro.sync()
                if (tick % BREAK_EVERY_TICKS == 0) runCatching { breakNudger.tick() }
                tick++
            }
            delay(RECHECK_MS)
        }
    }

    private fun addOverlayAnchor() {
        if (!Settings.canDrawOverlays(this)) return
        try {
            val view = View(this)
            val params = WindowManager.LayoutParams(
                1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            )
            getSystemService(WindowManager::class.java).addView(view, params)
            anchor = view
        } catch (_: RuntimeException) {
            // Overlay refused by the system; the block screen then relies on the accessibility exemption.
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel_blocking), NotificationManager.IMPORTANCE_LOW)
            .apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(guarded: Int, strict: Boolean, run: PomodoroRun?): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setContentTitle(getString(if (strict) R.string.notif_title_strict else R.string.notif_title))
            .setContentText(getString(R.string.notif_text, guarded))
        if (run != null) {
            // Ongoing countdown without per-second updates: the system draws the chronometer itself.
            builder.setContentTitle(getString(if (run.phase == PomodoroPhase.FOCUS) R.string.notif_pomo_focus else R.string.notif_pomo_break))
                .setContentText(getString(R.string.notif_pomo_remaining))
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(run.phaseEndMs)
                .setShowWhen(true)
        }
        return builder
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        anchor?.let { runCatching { getSystemService(WindowManager::class.java).removeView(it) } }
        anchor = null
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL_ID = "blocking_status"
        const val NOTIFICATION_ID = 1001
        const val POLL_MS = 1_000L
        const val LOOKBACK_MS = 15_000L
        const val RECHECK_MS = 5_000L
        const val BREAK_EVERY_TICKS = 24 // 24 x 5 s = 2 minutes
    }
}
