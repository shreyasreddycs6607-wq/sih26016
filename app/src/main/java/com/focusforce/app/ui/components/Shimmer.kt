package com.focusforce.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import com.focusforce.app.ui.theme.FocusTheme

/** Shimmer loading fill. With reduce-motion on it is a flat tint (no sweep). */
@Composable
fun Modifier.shimmer(): Modifier {
    val c = FocusTheme.colors
    val reduce = FocusTheme.reduceMotion
    val durationMs = (FocusTheme.motion.slowMs * 3).coerceAtLeast(1_200)
    val progress by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMs, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerProgress",
    )
    return this.drawWithCache {
        val base = c.surfaceHigh
        val highlight = c.primary.copy(alpha = 0.22f)
        onDrawBehind {
            if (reduce) {
                drawRect(base)
            } else {
                val w = size.width.coerceAtLeast(1f)
                val x = (progress * 2f - 0.5f) * w
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(base, highlight, base),
                        start = Offset(x - w * 0.4f, 0f),
                        end = Offset(x + w * 0.4f, size.height),
                    ),
                )
            }
        }
    }
}

/** A skeleton block to stand in for text, rings or cards while data loads. */
@Composable
fun ShimmerBox(modifier: Modifier = Modifier) {
    Box(modifier.clip(FocusTheme.shapes.small).shimmer())
}
