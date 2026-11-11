package com.focusforce.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusforce.app.ui.theme.FocusTheme

data class BottomBarItem(val icon: FocusIcon, val label: String)

/**
 * Animated tab bar. The selected tab gets a spring-scaled icon and a themed highlight pill.
 * Safe-area handling is the caller's job (put it in a container that pads for navigation bars).
 */
@Composable
fun FocusBottomBar(
    items: List<BottomBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = FocusTheme.tokens
    val c = t.colors
    val haptic = rememberHaptics()
    Row(
        modifier
            .clip(t.shapes.card)
            .background(c.surface.copy(alpha = 0.94f))
            .border(t.strokeWidth, c.outline.copy(alpha = 0.7f), t.shapes.card)
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            BarItem(
                item = item,
                selected = index == selectedIndex,
                onClick = {
                    if (index != selectedIndex) haptic(Haptic.Tick)
                    onSelect(index)
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BarItem(item: BottomBarItem, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val t = FocusTheme.tokens
    val c = t.colors
    val reduce = FocusTheme.reduceMotion
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.18f else 1f,
        animationSpec = if (reduce) snap() else t.motion.spring(),
        label = "tabScale",
    )
    val pill by animateColorAsState(
        targetValue = if (selected) c.primary.copy(alpha = 0.18f) else Color.Transparent,
        animationSpec = if (reduce) snap() else t.motion.fast(),
        label = "tabPill",
    )
    val tint by animateColorAsState(
        targetValue = if (selected) c.primary else c.textMuted,
        animationSpec = if (reduce) snap() else t.motion.fast(),
        label = "tabTint",
    )
    Column(
        modifier
            .heightIn(min = 52.dp)
            .clip(t.shapes.small)
            .background(pill)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) {
            FocusIconView(item.icon, tint, size = 24.dp)
        }
        Box(Modifier.height(2.dp))
        Text(item.label, style = t.typography.label.copy(fontSize = 11.sp, lineHeight = 14.sp), color = tint, maxLines = 1)
    }
}
