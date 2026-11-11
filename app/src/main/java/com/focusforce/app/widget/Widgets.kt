package com.focusforce.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.focusforce.app.MainActivity
import com.focusforce.app.R
import com.focusforce.app.domain.blocking.DetectionMode
import com.focusforce.app.permissions.PermissionKind
import com.focusforce.app.ui.theme.FocusColors
import com.focusforce.app.ui.theme.HeroTokensRegistry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private fun colors(s: WidgetSnapshot): FocusColors = HeroTokensRegistry.forHero(s.hero).colors
private fun cp(c: Color) = ColorProvider(c)
private fun minutes(ms: Long): String = if (ms >= 3_600_000L) "%dh %02dm".format(ms / 3_600_000L, (ms / 60_000L) % 60) else "${ms / 60_000L}m"
private fun clock(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

/** Today's screen time against your goal, drawn in your hero's style. Tap to open the app. */
class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = entryPoint(context).data().snapshot()
        val ring = WidgetBitmaps.ring(data.hero, data.progress, 240)
        provideContent {
            val c = colors(data)
            Column(
                GlanceModifier.fillMaxSize().background(cp(c.background)).cornerRadius(20.dp).padding(10.dp).clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(ImageProvider(ring), context.getString(R.string.widget_today_ring_description, minutes(data.totalMs), minutes(data.goalMs)), GlanceModifier.size(84.dp))
                Spacer(GlanceModifier.height(4.dp))
                Text(if (data.synced) minutes(data.totalMs) else context.getString(R.string.widget_today_waiting), style = TextStyle(color = cp(c.text), fontSize = 18.sp, fontWeight = FontWeight.Bold))
                Text(context.getString(R.string.widget_today_goal, minutes(data.goalMs)), style = TextStyle(color = cp(c.textMuted), fontSize = 12.sp))
            }
        }
    }
}

/** One-tap focus: starts or stops a Pomodoro. If blocking cannot work yet it opens the app instead of pretending. */
class QuickFocusWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = entryPoint(context).data().snapshot()
        provideContent {
            val c = colors(data)
            Column(
                GlanceModifier.fillMaxSize().background(cp(c.background)).cornerRadius(20.dp).padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (data.focusing) context.getString(R.string.widget_focus_until, clock(data.phaseEndsAtMs ?: 0L)) else context.getString(R.string.widget_focus_ready),
                    style = TextStyle(color = cp(c.text), fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                )
                Spacer(GlanceModifier.height(8.dp))
                Text(
                    context.getString(if (data.focusing) R.string.widget_focus_stop else R.string.widget_focus_start),
                    modifier = GlanceModifier.fillMaxWidth().background(cp(if (data.focusing) c.surfaceHigh else c.primary)).cornerRadius(14.dp).padding(10.dp).clickable(actionRunCallback<ToggleFocusAction>()),
                    style = TextStyle(color = cp(if (data.focusing) c.text else c.onPrimary), fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                )
            }
        }
    }
}

/** The streak power meter. Tap to open the app. */
class PowerMeterWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = entryPoint(context).data().snapshot()
        val meter = WidgetBitmaps.meter(data.hero, data.streak, 560, 90)
        provideContent {
            val c = colors(data)
            Column(
                GlanceModifier.fillMaxSize().background(cp(c.background)).cornerRadius(20.dp).padding(12.dp).clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(context.getString(R.string.widget_meter_title), style = TextStyle(color = cp(c.textMuted), fontSize = 12.sp))
                Spacer(GlanceModifier.height(4.dp))
                Image(ImageProvider(meter), context.getString(R.string.widget_meter_description, data.streak), GlanceModifier.fillMaxWidth().height(28.dp))
                Spacer(GlanceModifier.height(4.dp))
                Text(context.getString(R.string.widget_meter_days, data.streak), style = TextStyle(color = cp(c.text), fontSize = 16.sp, fontWeight = FontWeight.Bold))
            }
        }
    }
}

class ToggleFocusAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val ep = entryPoint(context)
        val data = ep.data().snapshot()
        if (data.focusing) {
            ep.pomodoro().stop()
        } else {
            val p = ep.permissions()
            val mode = DetectionMode.from(p.isGranted(PermissionKind.ACCESSIBILITY), p.isGranted(PermissionKind.USAGE_ACCESS), p.isGranted(PermissionKind.OVERLAY))
            if (mode == DetectionMode.NONE) {
                context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } else {
                ep.pomodoro().start()
            }
        }
        WidgetUpdater.updateAll(context)
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = TodayWidget() }
class QuickFocusWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = QuickFocusWidget() }
class PowerMeterWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = PowerMeterWidget() }

object WidgetUpdater {
    /** Redraws every placed widget. Cheap when none is placed. */
    suspend fun updateAll(context: Context) {
        runCatching { TodayWidget().updateAll(context) }
        runCatching { QuickFocusWidget().updateAll(context) }
        runCatching { PowerMeterWidget().updateAll(context) }
    }
}
