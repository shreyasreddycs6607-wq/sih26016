package com.focusforce.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusforce.app.domain.model.HeroTheme
import com.focusforce.app.ui.theme.FocusTheme
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private const val MIN_Y_MS = 10 * 60_000L

/**
 * Themed bar chart. [values] are milliseconds; null means "no data for this slot" (drawn as a dot, so
 * missing history is never mistaken for zero usage). Tap a bar to select it.
 * VECTOR: glowing gradient bars with a cap line. NOCTIS: soft rounded bars. DASH: thick outlined bars.
 */
@Composable
fun UsageBarChart(
    values: List<Long?>,
    labels: List<String>,
    description: String,
    modifier: Modifier = Modifier,
    goalMs: Long? = null,
    selectedIndex: Int? = null,
    onSelect: (Int?) -> Unit = {},
    height: Dp = 180.dp,
) {
    val t = FocusTheme.tokens
    val c = t.colors
    val reduce = FocusTheme.reduceMotion
    val progress = remember { Animatable(0f) }
    LaunchedEffect(values) {
        if (reduce) {
            progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(1f, t.motion.spring())
        }
    }
    val measurer = rememberTextMeasurer()
    val labelStyle = t.typography.label.copy(fontSize = 11.sp, lineHeight = 14.sp, color = c.textMuted)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description }
            .pointerInput(values.size, selectedIndex) {
                detectTapGestures { offset ->
                    val n = values.size
                    if (n > 0) {
                        val index = (offset.x / (size.width / n.toFloat())).toInt().coerceIn(0, n - 1)
                        onSelect(if (index == selectedIndex) null else index)
                    }
                }
            },
    ) {
        val n = values.size
        if (n == 0) return@Canvas
        val labelH = 18.dp.toPx()
        val chartH = size.height - labelH
        val slot = size.width / n
        val yMax = max(max(values.filterNotNull().maxOrNull() ?: 0L, goalMs ?: 0L), MIN_Y_MS).toFloat() * 1.15f

        if (t.hero != HeroTheme.DASH) {
            for (frac in listOf(0f, 0.5f, 1f)) {
                val y = chartH - chartH * frac / 1.15f
                drawLine(c.outline.copy(alpha = 0.25f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
        } else {
            drawLine(c.outline, Offset(0f, chartH), Offset(size.width, chartH), 3.dp.toPx())
        }

        values.forEachIndexed { i, v ->
            val cx = slot * i + slot / 2
            val selected = i == selectedIndex
            if (v == null) {
                drawCircle(c.textMuted.copy(alpha = 0.5f), 2.dp.toPx(), Offset(cx, chartH - 3.dp.toPx()))
            } else {
                val minH = if (v > 0) 3.dp.toPx() else 0f
                val h = max(minH, v / yMax * chartH * progress.value).coerceAtMost(chartH)
                val top = chartH - h
                when (t.hero) {
                    HeroTheme.VECTOR -> {
                        val w = min(slot * 0.42f, 14.dp.toPx())
                        val x = cx - w / 2
                        if (selected) drawRect(c.primary.copy(alpha = 0.22f), Offset(x - 3.dp.toPx(), top - 3.dp.toPx()), Size(w + 6.dp.toPx(), h + 3.dp.toPx()))
                        drawRect(
                            Brush.verticalGradient(listOf(c.primary.copy(alpha = 0.9f), c.primary.copy(alpha = 0.12f)), startY = top, endY = chartH),
                            Offset(x, top), Size(w, h),
                        )
                        drawLine(c.primary, Offset(x, top), Offset(x + w, top), 2.dp.toPx())
                    }
                    HeroTheme.NOCTIS -> {
                        val w = min(slot * 0.5f, 18.dp.toPx())
                        val x = cx - w / 2
                        val colors = if (selected) listOf(c.tertiary, c.tertiary.copy(alpha = 0.4f)) else listOf(c.primary, c.secondary.copy(alpha = 0.45f))
                        drawRoundRect(
                            Brush.verticalGradient(colors, startY = top, endY = chartH),
                            Offset(x, top), Size(w, max(h, w / 2)), CornerRadius(w / 2),
                        )
                    }
                    HeroTheme.DASH -> {
                        val w = slot * 0.62f
                        val x = cx - w / 2
                        drawRect(if (selected) c.secondary else c.primary, Offset(x, top), Size(w, h))
                        drawRect(c.outline, Offset(x, top), Size(w, h), style = Stroke(3.dp.toPx()))
                    }
                }
            }
        }

        if (goalMs != null) {
            val y = chartH - goalMs / yMax * chartH
            drawLine(
                c.tertiary, Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx())),
            )
        }

        labels.forEachIndexed { i, label ->
            if (label.isNotBlank() && i < n) {
                val layout = measurer.measure(label, labelStyle)
                drawText(layout, topLeft = Offset(slot * i + slot / 2 - layout.size.width / 2f, chartH + 3.dp.toPx()))
            }
        }
    }
}

/**
 * Hour-of-day heat map. [grid] is rows (days, oldest first) x 24 hours of milliseconds.
 * VECTOR: glowing cells. NOCTIS: soft moon-dust dots. DASH: halftone dots that grow with use.
 */
@Composable
fun HeatMap(
    grid: List<List<Long>>,
    rowLabels: List<String>,
    description: String,
    modifier: Modifier = Modifier,
) {
    val t = FocusTheme.tokens
    val c = t.colors
    val measurer = rememberTextMeasurer()
    val labelStyle: TextStyle = t.typography.label.copy(fontSize = 10.sp, lineHeight = 12.sp, color = c.textMuted)
    val rowH = 18.dp
    val headerH = 16.dp
    Canvas(
        modifier
            .fillMaxWidth()
            .height(headerH + rowH * grid.size)
            .semantics { contentDescription = description },
    ) {
        val labelW = 34.dp.toPx()
        val cellW = (size.width - labelW) / 24f
        val cellH = rowH.toPx()
        val maxMs = (grid.maxOfOrNull { row -> row.maxOrNull() ?: 0L } ?: 0L).coerceAtLeast(1L).toFloat()
        for (h in listOf(0, 6, 12, 18)) {
            val layout = measurer.measure(h.toString(), labelStyle)
            drawText(layout, topLeft = Offset(labelW + cellW * h + (cellW - layout.size.width) / 2f, 0f))
        }
        grid.forEachIndexed { r, row ->
            val y0 = headerH.toPx() + cellH * r
            val label = rowLabels.getOrNull(r).orEmpty()
            val layout = measurer.measure(label, labelStyle)
            drawText(layout, topLeft = Offset(0f, y0 + (cellH - layout.size.height) / 2f))
            row.forEachIndexed { h, ms ->
                val k = sqrt(ms / maxMs).coerceIn(0f, 1f) // sqrt so small-but-real use stays visible
                val cx = labelW + cellW * h + cellW / 2
                val cy = y0 + cellH / 2
                val side = min(cellW, cellH) - 2.dp.toPx()
                when (t.hero) {
                    HeroTheme.VECTOR -> {
                        drawRect(c.surfaceHigh.copy(alpha = 0.55f), Offset(cx - side / 2, cy - side / 2), Size(side, side))
                        if (k > 0f) drawRect(c.primary.copy(alpha = 0.15f + 0.85f * k), Offset(cx - side / 2, cy - side / 2), Size(side, side))
                    }
                    HeroTheme.NOCTIS -> {
                        drawCircle(c.surfaceHigh.copy(alpha = 0.6f), side * 0.18f, Offset(cx, cy))
                        if (k > 0f) drawCircle(
                            Brush.radialGradient(listOf(c.tertiary.copy(alpha = 0.95f * k + 0.05f), c.secondary.copy(alpha = 0f)), Offset(cx, cy), side * 0.6f),
                            side * 0.6f, Offset(cx, cy),
                        )
                    }
                    HeroTheme.DASH -> {
                        drawCircle(c.surfaceHigh, side * 0.12f, Offset(cx, cy))
                        if (k > 0f) drawCircle(c.primary, side * 0.5f * k.coerceAtLeast(0.2f), Offset(cx, cy))
                    }
                }
            }
        }
    }
}

/** One segment of a [SplitBar]. */
data class SplitPart(val color: Color, val weightMs: Long)

/** Stacked horizontal bar (e.g. productive vs distracting vs neutral). Zero-weight parts are skipped. */
@Composable
fun SplitBar(parts: List<SplitPart>, modifier: Modifier = Modifier, barHeight: Dp = 16.dp) {
    val t = FocusTheme.tokens
    val shown = parts.filter { it.weightMs > 0 }
    Row(
        modifier.fillMaxWidth().height(barHeight).clip(t.shapes.small).background(t.colors.surfaceHigh)
            .then(if (t.hero == HeroTheme.DASH) Modifier.border(2.dp, t.colors.outline, t.shapes.small) else Modifier),
    ) {
        shown.forEach { Box(Modifier.weight(it.weightMs.toFloat()).fillMaxHeight().background(it.color)) }
    }
}
