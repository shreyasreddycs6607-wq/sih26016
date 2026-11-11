package com.focusforce.app.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.graphics.toArgb
import com.focusforce.app.domain.model.HeroTheme
import com.focusforce.app.ui.theme.HeroTokensRegistry

/**
 * Widgets are RemoteViews and cannot run Canvas code, so the hero-styled gauges are drawn into small bitmaps here.
 * VECTOR: thin gauge arc with ticks. NOCTIS: moon phase. DASH: thick inked ring / slanted cells.
 */
internal object WidgetBitmaps {

    fun ring(hero: HeroTheme, progress: Float, sizePx: Int): Bitmap {
        val c = HeroTokensRegistry.forHero(hero).colors
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val p = progress.coerceIn(0f, 1f)
        val stroke = sizePx * 0.10f
        val box = RectF(stroke, stroke, sizePx - stroke, sizePx - stroke)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke }
        when (hero) {
            HeroTheme.VECTOR -> {
                paint.color = c.surfaceHigh.toArgb(); canvas.drawArc(box, 135f, 270f, false, paint)
                paint.color = c.primary.toArgb(); canvas.drawArc(box, 135f, 270f * p, false, paint)
            }
            HeroTheme.NOCTIS -> {
                val r = sizePx * 0.34f
                val cx = sizePx / 2f; val cy = sizePx / 2f
                val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
                fill.color = c.surfaceHigh.toArgb(); canvas.drawCircle(cx, cy, r, fill)
                // waxing moon: lit on the right, terminator an ellipse of width |cos(pi p)| r
                val k = kotlin.math.abs(kotlin.math.cos(Math.PI * p)).toFloat() * r
                val lit = Path().apply {
                    arcTo(RectF(cx - r, cy - r, cx + r, cy + r), -90f, 180f, true)
                    arcTo(RectF(cx - k, cy - r, cx + k, cy + r), 90f, if (p <= 0.5f) -180f else 180f, false)
                    close()
                }
                if (p > 0.01f) { fill.color = c.tertiary.toArgb(); canvas.drawPath(lit, fill) }
                paint.color = c.outline.toArgb(); paint.strokeWidth = sizePx * 0.015f; canvas.drawCircle(cx, cy, r, paint)
            }
            HeroTheme.DASH -> {
                paint.color = c.outline.toArgb(); paint.strokeWidth = stroke + sizePx * 0.05f; canvas.drawArc(box, 0f, 360f, false, paint)
                paint.strokeWidth = stroke
                paint.color = c.surfaceHigh.toArgb(); canvas.drawArc(box, 0f, 360f, false, paint)
                paint.color = c.primary.toArgb(); canvas.drawArc(box, -90f, 360f * p, false, paint)
            }
        }
        return bmp
    }

    /** The streak power meter: 14 cells, two weeks = full. */
    fun meter(hero: HeroTheme, streakDays: Int, widthPx: Int, heightPx: Int): Bitmap {
        val c = HeroTokensRegistry.forHero(hero).colors
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val cells = 14
        val gap = widthPx * 0.012f
        val cellW = (widthPx - gap * (cells - 1)) / cells
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        for (i in 0 until cells) {
            val on = i < streakDays
            val x = i * (cellW + gap)
            when (hero) {
                HeroTheme.VECTOR -> { paint.color = (if (on) c.primary else c.surfaceHigh).toArgb(); canvas.drawRect(x, heightPx * 0.2f, x + cellW, heightPx * 0.8f, paint) }
                HeroTheme.NOCTIS -> { paint.color = (if (on) c.tertiary else c.surfaceHigh).toArgb(); canvas.drawCircle(x + cellW / 2, heightPx / 2f, minOf(cellW, heightPx * 0.6f) / 2f, paint) }
                HeroTheme.DASH -> {
                    val slant = cellW * 0.25f
                    val cell = Path().apply { moveTo(x + slant, heightPx * 0.15f); lineTo(x + cellW + slant, heightPx * 0.15f); lineTo(x + cellW, heightPx * 0.85f); lineTo(x, heightPx * 0.85f); close() }
                    paint.style = Paint.Style.FILL; paint.color = (if (on) c.secondary else c.surfaceHigh).toArgb(); canvas.drawPath(cell, paint)
                    paint.style = Paint.Style.STROKE; paint.strokeWidth = heightPx * 0.06f; paint.color = c.outline.toArgb(); canvas.drawPath(cell, paint)
                    paint.style = Paint.Style.FILL
                }
            }
        }
        return bmp
    }
}
