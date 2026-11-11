package com.focusforce.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.focusforce.app.domain.model.HeroTheme
import com.focusforce.app.ui.theme.FocusColors
import com.focusforce.app.ui.theme.FocusTheme
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/** Looping 0..1 value for ambient motion. Constant 0 when reduce-motion is on. */
@Composable
private fun rememberAmbientPhase(): State<Float> {
    val reduce = FocusTheme.reduceMotion
    val period = FocusTheme.motion.ambientMs
    if (reduce) return remember { mutableFloatStateOf(0f) }
    val transition = rememberInfiniteTransition(label = "ambient")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(period, easing = LinearEasing), RepeatMode.Restart),
        label = "ambientPhase",
    )
}

/**
 * Full-bleed themed backdrop. VECTOR: grid + scanning line. NOCTIS: stars + drifting fog.
 * DASH: halftone dots + speed lines.
 */
@Composable
fun ThemedBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = FocusTheme.colors
    val hero = FocusTheme.hero
    val phase = rememberAmbientPhase()
    val stars = remember { List(70) { Triple(Random(it * 31 + 7).nextFloat(), Random(it * 17 + 3).nextFloat(), Random(it * 13 + 5).nextFloat()) } }
    Box(modifier.background(c.background)) {
        Canvas(Modifier.fillMaxSize()) {
            when (hero) {
                HeroTheme.VECTOR -> drawVectorHud(c, phase.value)
                HeroTheme.NOCTIS -> drawNight(c, phase.value, stars)
                HeroTheme.DASH -> drawComicPop(c)
            }
        }
        content()
    }
}

private fun DrawScope.drawVectorHud(c: FocusColors, t: Float) {
    val step = 32.dp.toPx()
    val grid = c.primary.copy(alpha = 0.05f)
    var x = 0f
    while (x < size.width) { drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1f); x += step }
    var y = 0f
    while (y < size.height) { drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f); y += step }
    // scanning line sweeping down with a soft trailing glow
    val sy = t * size.height
    drawRect(
        Brush.verticalGradient(
            listOf(c.primary.copy(alpha = 0f), c.primary.copy(alpha = 0.10f)),
            startY = sy - 120.dp.toPx(), endY = sy,
        ),
        topLeft = Offset(0f, sy - 120.dp.toPx()),
        size = androidx.compose.ui.geometry.Size(size.width, 120.dp.toPx()),
    )
    drawLine(c.primary.copy(alpha = 0.35f), Offset(0f, sy), Offset(size.width, sy), 1.5f)
}

private fun DrawScope.drawNight(c: FocusColors, t: Float, stars: List<Triple<Float, Float, Float>>) {
    drawRect(Brush.verticalGradient(listOf(c.background, c.surface.copy(alpha = 0.9f), c.background)))
    // drifting fog banks
    val drift = (sin(t * 2 * PI) .toFloat()) * size.width * 0.12f
    drawCircle(
        Brush.radialGradient(listOf(c.secondary.copy(alpha = 0.16f), c.secondary.copy(alpha = 0f)), Offset(size.width * 0.25f + drift, size.height * 0.30f), size.width * 0.7f),
        radius = size.width * 0.7f, center = Offset(size.width * 0.25f + drift, size.height * 0.30f),
    )
    drawCircle(
        Brush.radialGradient(listOf(c.primary.copy(alpha = 0.10f), c.primary.copy(alpha = 0f)), Offset(size.width * 0.85f - drift, size.height * 0.78f), size.width * 0.8f),
        radius = size.width * 0.8f, center = Offset(size.width * 0.85f - drift, size.height * 0.78f),
    )
    // twinkling stars
    stars.forEach { (sx, sy, seed) ->
        val twinkle = 0.35f + 0.65f * (0.5f + 0.5f * sin((t + seed) * 2 * PI).toFloat())
        drawCircle(c.text.copy(alpha = 0.55f * twinkle), 0.8.dp.toPx() + seed * 1.2.dp.toPx(), Offset(sx * size.width, sy * size.height))
    }
}

private fun DrawScope.drawComicPop(c: FocusColors) {
    // halftone: dots shrink with distance from the top-right corner
    val step = 14.dp.toPx()
    val reach = hypot(size.width, size.height) * 0.55f
    val corner = Offset(size.width, 0f)
    var y = 0f
    var row = 0
    while (y < size.height * 0.6f) {
        var x = if (row % 2 == 0) 0f else step / 2
        while (x < size.width) {
            val d = hypot(x - corner.x, y - corner.y)
            val k = 1f - (d / reach)
            if (k > 0f) drawCircle(c.primary.copy(alpha = 0.18f), step * 0.38f * k, Offset(x, y))
            x += step
        }
        y += step * 0.866f
        row++
    }
    // speed lines from the left edge
    val line = c.secondary.copy(alpha = 0.10f)
    for (i in 0 until 6) {
        val yy = size.height * (0.55f + i * 0.07f)
        drawLine(line, Offset(0f, yy), Offset(size.width * (0.25f + 0.05f * i), yy), 3.dp.toPx())
    }
}
