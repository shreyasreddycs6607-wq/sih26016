package com.focusforce.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.focusforce.app.domain.model.HeroTheme
import com.focusforce.app.ui.theme.FocusTheme

private const val SEGMENTS = 14

/**
 * The streak as a "power meter" of 14 cells (two weeks = full). When a streak breaks the meter drains slowly and without
 * any red or alarm, from what it showed last time down to the new value. With reduce-motion on it simply changes.
 * VECTOR: glowing cells. NOCTIS: moon-phase dots. DASH: slanted, outlined cells.
 */
@Composable
fun PowerMeter(
    streakDays: Int,
    previousDays: Int,
    description: String,
    onShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = FocusTheme.tokens
    val c = t.colors
    val reduce = FocusTheme.reduceMotion
    val target = (streakDays.toFloat() / SEGMENTS).coerceIn(0f, 1f)
    val start = (previousDays.toFloat() / SEGMENTS).coerceIn(0f, 1f)
    val level = remember { Animatable(if (reduce) target else start) }
    LaunchedEffect(streakDays) {
        if (reduce) level.snapTo(target) else level.animateTo(target, tween(if (target < start) 2_000 else 900, easing = t.motion.easing))
        onShown()
    }

    Canvas(modifier.fillMaxWidth().height(36.dp).semantics { contentDescription = description }) {
        val gap = 4.dp.toPx()
        val cellW = (size.width - gap * (SEGMENTS - 1)) / SEGMENTS
        val value = level.value * SEGMENTS
        for (i in 0 until SEGMENTS) {
            val fill = (value - i).coerceIn(0f, 1f)
            val x = i * (cellW + gap)
            val draining = level.value > target + 0.001f
            val on = if (draining) c.textMuted else c.primary
            when (t.hero) {
                HeroTheme.VECTOR -> {
                    drawRect(c.surfaceHigh, Offset(x, 6.dp.toPx()), Size(cellW, size.height - 12.dp.toPx()))
                    if (fill > 0f) drawRect(on.copy(alpha = 0.35f + 0.65f * fill), Offset(x, 6.dp.toPx()), Size(cellW * fill.coerceAtLeast(0.15f), size.height - 12.dp.toPx()))
                }
                HeroTheme.NOCTIS -> {
                    val r = minOf(cellW, size.height) / 2.4f
                    drawCircle(c.surfaceHigh, r, Offset(x + cellW / 2, size.height / 2))
                    if (fill > 0f) drawCircle(c.tertiary.copy(alpha = 0.3f + 0.7f * fill), r * (0.5f + 0.5f * fill), Offset(x + cellW / 2, size.height / 2))
                }
                HeroTheme.DASH -> {
                    val slant = 4.dp.toPx()
                    val cell = Path().apply {
                        moveTo(x + slant, 4.dp.toPx()); lineTo(x + cellW + slant, 4.dp.toPx())
                        lineTo(x + cellW, size.height - 4.dp.toPx()); lineTo(x, size.height - 4.dp.toPx()); close()
                    }
                    drawPath(cell, c.surfaceHigh)
                    if (fill > 0.5f) drawPath(cell, if (draining) c.textMuted else c.secondary)
                    drawPath(cell, c.outline, style = Stroke(2.dp.toPx()))
                }
            }
        }
    }
}
