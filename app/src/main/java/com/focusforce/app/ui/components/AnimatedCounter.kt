package com.focusforce.app.ui.components

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.focusforce.app.ui.theme.FocusTheme

/** A number that rolls to its new value with the hero's spring. Snaps when reduce-motion is on. */
@Composable
fun AnimatedCounter(
    value: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = FocusTheme.typography.numeralLarge,
    color: Color = FocusTheme.colors.text,
    format: (Int) -> String = { it.toString() },
) {
    val reduce = FocusTheme.reduceMotion
    val shown by animateIntAsState(
        targetValue = value,
        animationSpec = if (reduce) snap() else FocusTheme.motion.medium(),
        label = "counter",
    )
    Text(format(shown), modifier = modifier, style = style, color = color)
}

/** "2h 05m" as two rolling counters. [totalMinutes] changes roll the digits. */
@Composable
fun DurationCounter(
    totalMinutes: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = FocusTheme.typography.numeral,
    color: Color = FocusTheme.colors.text,
) {
    androidx.compose.foundation.layout.Row(modifier, verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
        if (totalMinutes >= 60) {
            AnimatedCounter(totalMinutes / 60, style = style, color = color)
            Text("h ", style = style, color = color)
            AnimatedCounter(totalMinutes % 60, style = style, color = color, format = { "%02d".format(it) })
            Text("m", style = style, color = color)
        } else {
            AnimatedCounter(totalMinutes, style = style, color = color)
            Text("m", style = style, color = color)
        }
    }
}
