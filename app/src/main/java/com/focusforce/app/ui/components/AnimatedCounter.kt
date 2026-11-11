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
