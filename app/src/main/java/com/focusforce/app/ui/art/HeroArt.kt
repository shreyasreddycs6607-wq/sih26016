package com.focusforce.app.ui.art

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.focusforce.app.R
import com.focusforce.app.domain.model.HeroTheme
import com.focusforce.app.ui.theme.FocusTheme
import com.focusforce.app.ui.theme.nameRes
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Original hero and mascot illustrations, drawn in code on a 120 x 140 grid.
 * VECTOR: a visored engineer-robot with a glowing chest gauge.
 * NOCTIS: a hooded, cloaked sentinel with a crescent emblem (round hood, no ears or bat shapes).
 * DASH: a goggled courier in a round helmet with a trailing scarf and chevron chest.
 */

enum class HeroPose { Idle, Guard, Celebrate }

private const val ART_WIDTH = 120f
private const val ART_HEIGHT = 140f

@Composable
fun HeroMascot(
    hero: HeroTheme,
    modifier: Modifier = Modifier,
    pose: HeroPose = HeroPose.Idle,
    contentDescription: String? = null,
) {
    val reduce = FocusTheme.reduceMotion
    val motion = FocusTheme.motion
    val poseValue by animateFloatAsState(
        targetValue = pose.ordinal.toFloat(),
        animationSpec = if (reduce) snap() else motion.spring(),
        label = "heroPose",
    )
    val loop = rememberInfiniteTransition(label = "heroIdle")
    val phase by loop.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3_200, easing = LinearEasing), RepeatMode.Restart),
        label = "heroPhase",
    )
    val description = contentDescription ?: stringResource(R.string.mascot_description, stringResource(hero.nameRes))
    Canvas(
        modifier
            .aspectRatio(ART_WIDTH / ART_HEIGHT)
            .semantics { this.contentDescription = description },
    ) {
        val s = size.width / ART_WIDTH
        val ph = if (reduce) 0f else phase
        withTransform({ scale(s, s, pivot = Offset.Zero) }) {
            when (hero) {
                HeroTheme.VECTOR -> drawVector(poseValue, ph)
                HeroTheme.NOCTIS -> drawNoctis(poseValue, ph)
                HeroTheme.DASH -> drawDash(poseValue, ph)
            }
        }
    }
}

/** The FocusForce emblem: a hexagon shield with an "F". Used on the welcome screen. */
@Composable
fun FocusForceLogo(modifier: Modifier = Modifier, size: Dp = 96.dp) {
    val c = FocusTheme.colors
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 108f
        withTransform({ scale(s, s, pivot = Offset.Zero) }) {
            val hex = Path().apply {
                moveTo(54f, 12f); lineTo(94f, 34f); lineTo(94f, 74f); lineTo(54f, 96f); lineTo(14f, 74f); lineTo(14f, 34f); close()
            }
            drawPath(hex, c.primary.copy(alpha = 0.25f), style = Stroke(11f, join = StrokeJoin.Round))
            drawPath(hex, c.primary, style = Stroke(5f, join = StrokeJoin.Round))
            val f = Path().apply {
                moveTo(38f, 34f); lineTo(76f, 34f); lineTo(76f, 43f); lineTo(48f, 43f); lineTo(48f, 51f)
                lineTo(70f, 51f); lineTo(70f, 59f); lineTo(48f, 59f); lineTo(48f, 76f); lineTo(38f, 76f); close()
            }
            drawPath(f, c.text)
        }
    }
}

// ---- shared helpers -------------------------------------------------------

private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t

/** Arm angle (degrees, 0 = right, 90 = down) interpolated across Idle(0) -> Guard(1) -> Celebrate(2). */
private fun armAngle(v: Float, idle: Float, guard: Float, celebrate: Float): Float =
    if (v <= 1f) lerpF(idle, guard, v.coerceAtLeast(0f)) else lerpF(guard, celebrate, (v - 1f).coerceAtMost(1f))

private fun DrawScope.limb(from: Offset, angleDeg: Float, length: Float, width: Float, color: Color, hand: Color) {
    val a = angleDeg * (PI / 180.0).toFloat()
    val end = from + Offset(cos(a) * length, sin(a) * length)
    drawLine(color, from, end, width, StrokeCap.Round)
    drawCircle(hand, width * 0.62f, end)
}

private fun loopSin(ph: Float, cycles: Float = 1f, offset: Float = 0f): Float =
    sin((ph * cycles * 2.0 * PI + offset).toFloat())

/** 1 = eyes open; briefly ~0.15 once per cycle. */
private fun blink(ph: Float): Float = if ((ph * 3f) % 1f > 0.93f) 0.15f else 1f

// ---- VECTOR ---------------------------------------------------------------

private fun DrawScope.drawVector(v: Float, ph: Float) {
    val cyan = Color(0xFF4DE8E0)
    val steel = Color(0xFF16313F)
    val steel2 = Color(0xFF1D4A5C)
    translate(top = loopSin(ph) * 1.5f) {
        drawRoundRect(steel, Offset(44f, 100f), Size(14f, 30f), CornerRadius(5f))
        drawRoundRect(steel, Offset(62f, 100f), Size(14f, 30f), CornerRadius(5f))
        drawRect(cyan, Offset(44f, 124f), Size(14f, 3f))
        drawRect(cyan, Offset(62f, 124f), Size(14f, 3f))
        drawRoundRect(steel2, Offset(36f, 58f), Size(48f, 46f), CornerRadius(12f))
        // chest gauge with a spinning arc
        drawCircle(Brush.radialGradient(listOf(cyan.copy(alpha = 0.5f), cyan.copy(alpha = 0f)), Offset(60f, 82f), 18f), 18f, Offset(60f, 82f))
        drawCircle(cyan, 9f, Offset(60f, 82f), style = Stroke(2f))
        drawArc(cyan, -90f + ph * 360f, 200f, false, Offset(54f, 76f), Size(12f, 12f), style = Stroke(2.5f, cap = StrokeCap.Round))
        drawCircle(cyan, 2.2f, Offset(60f, 82f))
        limb(Offset(38f, 66f), armAngle(v, 105f, 20f, 235f), 28f, 9f, steel, cyan)
        limb(Offset(82f, 66f), armAngle(v, 75f, 160f, 305f), 28f, 9f, steel, cyan)
        // helmet + visor
        drawRoundRect(steel2, Offset(40f, 20f), Size(40f, 38f), CornerRadius(16f))
        drawRoundRect(Color(0xFF021015), Offset(45f, 31f), Size(30f, 13f), CornerRadius(6f))
        val b = blink(ph)
        drawRoundRect(cyan, Offset(48f, 37.5f - 3f * b), Size(24f, 6f * b), CornerRadius(3f))
        val sx = 46f + 28f * ((ph * 2f) % 1f)
        drawLine(Color.White.copy(alpha = 0.8f), Offset(sx, 32f), Offset(sx, 43f), 1.2f)
        drawLine(cyan, Offset(60f, 20f), Offset(60f, 12f), 2f)
        drawCircle(cyan.copy(alpha = 0.6f + 0.4f * loopSin(ph, 2f)), 3f, Offset(60f, 11f))
    }
}

// ---- NOCTIS ---------------------------------------------------------------

private fun DrawScope.drawNoctis(v: Float, ph: Float) {
    val cloak = Color(0xFF1A1E45)
    val body = Color(0xFF262C63)
    val silver = Color(0xFFD6DCF0)
    val glow = Color(0xFFF4E9B8)
    val sway = loopSin(ph) * 3f
    translate(top = loopSin(ph, 1f, 1.2f) * 1.2f) {
        val cape = Path().apply {
            moveTo(44f, 44f); lineTo(76f, 44f); lineTo(96f + sway, 126f)
            quadraticTo(84f + sway, 132f, 72f + sway / 2f, 127f)
            quadraticTo(60f, 134f, 48f - sway / 2f, 127f)
            quadraticTo(36f - sway, 132f, 24f - sway, 126f)
            close()
        }
        drawPath(cape, cloak)
        drawRoundRect(body, Offset(42f, 60f), Size(36f, 56f), CornerRadius(14f))
        // crescent emblem
        drawCircle(silver, 9f, Offset(60f, 82f))
        drawCircle(body, 8f, Offset(63.5f, 80.5f))
        limb(Offset(42f, 66f), armAngle(v, 105f, 20f, 235f), 26f, 8f, body, silver)
        limb(Offset(78f, 66f), armAngle(v, 75f, 160f, 305f), 26f, 8f, body, silver)
        // hood, shadowed face opening, glowing eyes
        drawCircle(cloak, 24f, Offset(60f, 38f))
        drawOval(Color(0xFF0A0B1A), Offset(47f, 30f), Size(26f, 24f))
        val b = blink(ph)
        drawOval(glow, Offset(51f, 41f + (1f - b) * 2f), Size(7f, 5f * b))
        drawOval(glow, Offset(62f, 41f + (1f - b) * 2f), Size(7f, 5f * b))
        // twinkling motes
        listOf(Offset(18f, 30f), Offset(104f, 46f), Offset(96f, 14f)).forEachIndexed { i, p ->
            val a = 0.35f + 0.65f * (0.5f + 0.5f * loopSin(ph, 1f, i * 2.1f))
            drawCircle(silver.copy(alpha = a), 1.8f, p)
        }
    }
}

// ---- DASH -----------------------------------------------------------------

private fun DrawScope.drawDash(v: Float, ph: Float) {
    val red = Color(0xFFFF5A5A)
    val blue = Color(0xFF3D7BFF)
    val yellow = Color(0xFFFFD92E)
    val ink = Color(0xFF101014)
    val lineAlpha = 0.25f + 0.2f * loopSin(ph)
    for (i in 0..2) {
        drawLine(yellow.copy(alpha = lineAlpha), Offset(2f, 70f + i * 14f), Offset(24f - i * 4f, 70f + i * 14f), 3f, StrokeCap.Round)
    }
    translate(top = loopSin(ph, 2f) * 1.8f) {
        val scarf = Path().apply {
            moveTo(70f, 58f)
            quadraticTo(92f, 50f + loopSin(ph) * 6f, 110f, 60f + loopSin(ph, 1f, 1f) * 6f)
        }
        drawPath(scarf, yellow, style = Stroke(7f, cap = StrokeCap.Round))
        drawRoundRect(blue, Offset(44f, 100f), Size(14f, 22f), CornerRadius(5f))
        drawRoundRect(blue, Offset(62f, 100f), Size(14f, 22f), CornerRadius(5f))
        drawRoundRect(red, Offset(42f, 118f), Size(18f, 12f), CornerRadius(5f))
        drawRoundRect(red, Offset(60f, 118f), Size(18f, 12f), CornerRadius(5f))
        drawRoundRect(blue, Offset(36f, 58f), Size(48f, 46f), CornerRadius(12f))
        for (y in listOf(70f, 82f)) {
            val chevron = Path().apply { moveTo(48f, y); lineTo(60f, y + 10f); lineTo(72f, y) }
            drawPath(chevron, yellow, style = Stroke(5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        limb(Offset(38f, 66f), armAngle(v, 105f, 20f, 235f), 28f, 9f, red, yellow)
        limb(Offset(82f, 66f), armAngle(v, 75f, 160f, 305f), 28f, 9f, red, yellow)
        // helmet, fin, goggles, grin
        drawPath(Path().apply { moveTo(55f, 16f); lineTo(60f, 5f); lineTo(65f, 16f); close() }, yellow)
        drawCircle(red, 22f, Offset(60f, 36f))
        drawRoundRect(yellow, Offset(38f, 29f), Size(44f, 17f), CornerRadius(8f))
        val b = blink(ph)
        drawOval(ink, Offset(43f, 32f + (1f - b) * 5f), Size(14f, 11f * b))
        drawOval(ink, Offset(63f, 32f + (1f - b) * 5f), Size(14f, 11f * b))
        drawCircle(Color.White, 1.8f, Offset(47f, 35f))
        drawCircle(Color.White, 1.8f, Offset(67f, 35f))
        drawArc(ink, 20f, 140f, false, Offset(52f, 45f), Size(16f, 9f), style = Stroke(2.5f, cap = StrokeCap.Round))
    }
}
