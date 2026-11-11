package com.focusforce.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.focusforce.app.domain.model.HeroTheme
import com.focusforce.app.ui.theme.FocusColors
import com.focusforce.app.ui.theme.FocusTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

/**
 * Progress ring in the active hero's style.
 * VECTOR: 270 degree HUD gauge with tick marks. NOCTIS: moon phase (new -> full). DASH: thick inked ring.
 *
 * @param progress 0f..1f (values outside are clamped). The ring animates to new values.
 * @param description spoken by TalkBack, e.g. "Daily goal, 40 percent used".
 * @param content centre content (value text). For NOCTIS it is placed under the moon.
 */
@Composable
fun StatRing(
    progress: Float,
    description: String,
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    content: @Composable () -> Unit = {},
) {
    val t = FocusTheme.tokens
    val reduce = FocusTheme.reduceMotion
    val target = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = if (reduce) snap() else t.motion.spring(),
        label = "ringProgress",
    )
    Box(
        modifier
            .size(size)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f)
            },
        contentAlignment = if (t.hero == HeroTheme.NOCTIS) Alignment.BottomCenter else Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val p = animated.coerceIn(0f, 1f)
            when (t.hero) {
                HeroTheme.VECTOR -> drawHudGauge(t.colors, p, t.strokeWidth.toPx())
                HeroTheme.NOCTIS -> drawMoonPhase(t.colors, p)
                HeroTheme.DASH -> drawInkRing(t.colors, p)
            }
        }
        Column(
            Modifier.padding(bottom = if (t.hero == HeroTheme.NOCTIS) size * 0.08f else 0.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
    }
}

private fun DrawScope.drawHudGauge(c: FocusColors, p: Float, hairline: Float) {
    val pad = 14.dp.toPx()
    val d = size.minDimension - pad * 2
    val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
    val arc = Size(d, d)
    val start = 135f
    val sweep = 270f
    // track + thin outer line
    drawArc(c.surfaceHigh, start, sweep, false, topLeft, arc, style = Stroke(8.dp.toPx(), cap = StrokeCap.Butt))
    drawArc(c.primary.copy(alpha = 0.4f), start, sweep, false, topLeft - Offset(8.dp.toPx(), 8.dp.toPx()), Size(d + 16.dp.toPx(), d + 16.dp.toPx()), style = Stroke(hairline))
    // glow then value arc
    drawArc(c.primary.copy(alpha = 0.25f), start, sweep * p, false, topLeft, arc, style = Stroke(14.dp.toPx(), cap = StrokeCap.Butt))
    drawArc(c.primary, start, sweep * p, false, topLeft, arc, style = Stroke(6.dp.toPx(), cap = StrokeCap.Butt))
    // tick marks every 10%
    val center = Offset(size.width / 2, size.height / 2)
    for (i in 0..10) {
        val angle = (start + sweep * i / 10f) * PI.toFloat() / 180f
        val inner = d / 2 - 12.dp.toPx()
        val outer = d / 2 - 5.dp.toPx()
        val col = if (i / 10f <= p) c.primary else c.textMuted.copy(alpha = 0.6f)
        drawLine(
            col,
            center + Offset(cos(angle) * inner, kotlin.math.sin(angle) * inner),
            center + Offset(cos(angle) * outer, kotlin.math.sin(angle) * outer),
            hairline.coerceAtLeast(1.5f),
        )
    }
}

private fun DrawScope.drawMoonPhase(c: FocusColors, p: Float) {
    val r = size.minDimension * 0.30f
    val center = Offset(size.width / 2, size.height * 0.34f)
    // halo
    drawCircle(Brush.radialGradient(listOf(c.tertiary.copy(alpha = 0.30f * (0.3f + p)), c.tertiary.copy(alpha = 0f)), center, r * 2.1f), r * 2.1f, center)
    // dark disc
    drawCircle(c.surfaceHigh, r, center)
    drawCircle(c.outline.copy(alpha = 0.6f), r, center, style = Stroke(1.dp.toPx()))
    // lit portion: p=0 new, 0.5 first quarter, 1 full (waxing, lit on the right)
    val k = abs(cos(PI * p)).toFloat() * r
    val lit = Path().apply {
        arcTo(Rect(center.x - r, center.y - r, center.x + r, center.y + r), -90f, 180f, true)
        val term = Rect(center.x - k, center.y - r, center.x + k, center.y + r)
        if (p <= 0.5f) arcTo(term, 90f, -180f, false) else arcTo(term, 90f, 180f, false)
        close()
    }
    if (p > 0.001f) drawPath(lit, c.tertiary)
}

private fun DrawScope.drawInkRing(c: FocusColors, p: Float) {
    val ink = 14.dp.toPx()
    val pad = ink
    val d = size.minDimension - pad * 2
    val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
    val arc = Size(d, d)
    // outline "ink" layer, then colored layers on top
    drawArc(c.outline, 0f, 360f, false, topLeft, arc, style = Stroke(ink + 6.dp.toPx()))
    drawArc(c.surfaceHigh, 0f, 360f, false, topLeft, arc, style = Stroke(ink))
    rotate(-90f, Offset(size.width / 2, size.height / 2)) {
        drawArc(c.primary, 0f, 360f * p, false, topLeft, arc, style = Stroke(ink, cap = StrokeCap.Butt))
        if (p >= 0.999f) drawArc(c.secondary, 0f, 360f, false, topLeft, arc, style = Stroke(ink))
    }
}
