package com.focusforce.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.focusforce.app.domain.model.HeroTheme
import com.focusforce.app.ui.theme.FocusTheme

/** Themed segmented control (Daily / Weekly / Monthly, category pickers, ...). */
@Composable
fun FocusSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = FocusTheme.tokens
    val c = t.colors
    val haptic = rememberHaptics()
    Row(
        modifier
            .clip(t.shapes.button)
            .background(c.surface)
            .border(t.strokeWidth, c.outline.copy(alpha = 0.7f), t.shapes.button)
            .selectableGroup(),
    ) {
        options.forEachIndexed { i, label ->
            val selected = i == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .background(if (selected) c.primary else Color.Transparent)
                    .selectable(selected = selected, role = Role.Tab) {
                        if (!selected) haptic(Haptic.Tick)
                        onSelect(i)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (t.hero == HeroTheme.DASH) label.uppercase() else label,
                    style = t.typography.label,
                    color = if (selected) c.onPrimary else c.text,
                )
            }
        }
    }
}

/**
 * Horizontal progress bar that animates to its value. [progress] is 0f..1f.
 * [description] is read by TalkBack (e.g. "Instagram, 40 percent of target").
 */
@Composable
fun FocusProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = FocusTheme.colors.primary,
    description: String? = null,
    barHeight: androidx.compose.ui.unit.Dp = 10.dp,
) {
    val t = FocusTheme.tokens
    val target = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = if (FocusTheme.reduceMotion) snap() else t.motion.spring(),
        label = "progressBar",
    )
    val sem = if (description != null) {
        Modifier.semantics(mergeDescendants = true) {
            contentDescription = description
            progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f)
        }
    } else {
        Modifier
    }
    Box(
        modifier.then(sem).fillMaxWidth().height(barHeight)
            .clip(t.shapes.small)
            .background(t.colors.surfaceHigh)
            .then(if (t.hero == HeroTheme.DASH) Modifier.border(2.dp, t.colors.outline, t.shapes.small) else Modifier),
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(animated.coerceIn(0f, 1f)).background(color))
    }
}
