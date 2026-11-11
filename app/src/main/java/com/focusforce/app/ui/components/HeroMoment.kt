package com.focusforce.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.focusforce.app.R
import com.focusforce.app.domain.model.HeroTheme
import com.focusforce.app.ui.art.HeroMascot
import com.focusforce.app.ui.art.HeroPose
import com.focusforce.app.ui.theme.FocusColors
import com.focusforce.app.ui.theme.FocusTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlin.random.Random

/** The two signature moments: the "power-up" when a focus session starts and the celebration when one completes. */
enum class Moment { POWER_UP, CELEBRATE }

/**
 * Full-screen hero moment, in the active hero's style. VECTOR: HUD rings collapse inward / expand outward.
 * NOCTIS: stars rise around a moon glow. DASH: a comic starburst slams in with confetti. Tap to dismiss;
 * with reduce-motion on it is a static card for a moment.
 */
@Composable
fun HeroMoment(moment: Moment, onDone: () -> Unit) {
    val t = FocusTheme.tokens
    val c = t.colors
    val reduce = FocusTheme.reduceMotion
    val haptic = rememberHaptics()
    val progress = remember(moment) { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(moment) {
        haptic(Haptic.Confirm)
        if (!reduce) progress.animateTo(1f, tween(1400, easing = LinearEasing))
        delay(if (reduce) 1800L else 900L)
        onDone()
    }
    val label = stringResource(
        when (t.hero) {
            HeroTheme.VECTOR -> if (moment == Moment.POWER_UP) R.string.moment_vector_start else R.string.moment_vector_done
            HeroTheme.NOCTIS -> if (moment == Moment.POWER_UP) R.string.moment_noctis_start else R.string.moment_noctis_done
            HeroTheme.DASH -> if (moment == Moment.POWER_UP) R.string.moment_dash_start else R.string.moment_dash_done
        },
    )
    val confetti = remember { List(26) { Triple(Random(it * 7 + 1).nextFloat() * 2f * PI.toFloat(), 0.35f + Random(it * 13 + 5).nextFloat() * 0.65f, Random(it * 3 + 9).nextInt(3)) } }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.78f))
            .clickable(role = Role.Button, onClick = onDone)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) { drawMoment(t.hero, moment, c, progress.value, confetti) }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            HeroMascot(
                t.hero,
                Modifier.height(220.dp).graphicsLayer {
                    val e = progress.value
                    val bounce = if (moment == Moment.CELEBRATE) 1f + 0.08f * sin(e * PI.toFloat() * 3f) else 0.6f + 0.4f * e
                    scaleX = bounce; scaleY = bounce
                },
                pose = HeroPose.Celebrate,
            )
            Text(label, style = t.typography.display, color = c.text, textAlign = TextAlign.Center)
        }
    }
}

private fun DrawScope.drawMoment(hero: HeroTheme, moment: Moment, c: FocusColors, e: Float, confetti: List<Triple<Float, Float, Int>>) {
    val center = Offset(size.width / 2, size.height * 0.42f)
    val base = size.minDimension
    val inward = moment == Moment.POWER_UP
    when (hero) {
        HeroTheme.VECTOR -> for (i in 0 until 4) {
            val phase = ((e * 1.3f) - i * 0.12f).coerceIn(0f, 1f)
            val r = base * (if (inward) 0.75f - 0.45f * phase else 0.2f + 0.6f * phase)
            drawCircle(c.primary.copy(alpha = (1f - phase) * 0.7f), r, center, style = Stroke(2.dp.toPx()))
        }
        HeroTheme.NOCTIS -> {
            drawCircle(c.tertiary.copy(alpha = 0.16f * sin(e * PI.toFloat())), base * (0.25f + 0.4f * e), center)
            confetti.forEach { (a, d, kind) ->
                val y = center.y - d * base * 0.5f * e + sin(a) * 40.dp.toPx()
                val x = center.x + cos(a) * d * base * 0.45f
                drawCircle(c.text.copy(alpha = sin(e * PI.toFloat()) * 0.9f), (1.5f + kind).dp.toPx(), Offset(x, y))
            }
        }
        HeroTheme.DASH -> {
            val pop = if (e < 0.35f) e / 0.35f * 1.15f else 1.15f - 0.15f * ((e - 0.35f) / 0.65f)
            val spikes = 16
            val outer = base * 0.46f * pop
            val burst = Path()
            for (i in 0 until spikes * 2) {
                val r = if (i % 2 == 0) outer else outer * 0.64f
                val a = (PI * i / spikes).toFloat()
                val p = Offset(center.x + cos(a) * r, center.y + sin(a) * r)
                if (i == 0) burst.moveTo(p.x, p.y) else burst.lineTo(p.x, p.y)
            }
            burst.close()
            drawPath(burst, c.secondary.copy(alpha = 0.9f))
            drawPath(burst, c.outline, style = Stroke(4.dp.toPx()))
            confetti.forEach { (a, d, kind) ->
                val col = when (kind) { 0 -> c.primary; 1 -> c.secondary; else -> c.tertiary }
                val r = d * base * 0.62f * e
                drawCircle(col, 4.dp.toPx(), Offset(center.x + cos(a) * r, center.y + sin(a) * r))
            }
        }
    }
}
