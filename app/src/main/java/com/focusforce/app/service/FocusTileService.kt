package com.focusforce.app.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.focusforce.app.MainActivity
import com.focusforce.app.R
import com.focusforce.app.data.focus.PomodoroController
import com.focusforce.app.domain.blocking.DetectionMode
import com.focusforce.app.permissions.PermissionChecker
import com.focusforce.app.permissions.PermissionKind
import com.focusforce.app.widget.WidgetDataSource
import com.focusforce.app.widget.WidgetUpdater
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Quick Settings tile: tap to start or stop a Pomodoro focus set. Opens the app if blocking cannot work yet. */
@AndroidEntryPoint
class FocusTileService : TileService() {

    @Inject lateinit var controller: PomodoroController
    @Inject lateinit var data: WidgetDataSource
    @Inject lateinit var permissions: PermissionChecker

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        scope.launch { refresh() }
    }

    override fun onClick() {
        scope.launch {
            val running = data.snapshot().focusing
            val mode = DetectionMode.from(
                permissions.isGranted(PermissionKind.ACCESSIBILITY), permissions.isGranted(PermissionKind.USAGE_ACCESS), permissions.isGranted(PermissionKind.OVERLAY),
            )
            when {
                running -> controller.stop()
                mode == DetectionMode.NONE -> openApp()
                else -> controller.start()
            }
            WidgetUpdater.updateAll(applicationContext)
            refresh()
        }
    }

    private suspend fun refresh() {
        val tile = qsTile ?: return
        val running = data.snapshot().focusing
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = getString(if (running) R.string.tile_running else R.string.tile_idle)
        tile.updateTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
