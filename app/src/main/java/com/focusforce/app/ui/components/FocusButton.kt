package com.focusforce.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusforce.app.domain.model.HeroTheme
import com.focusforce.app.ui.theme.FocusTheme

enum class ButtonStyle { Primary, Secondary, Ghost }

/** Hero-themed button: spring press-scale (bouncy for DASH, crisp for VECTOR, soft for NOCTIS) and haptic tick. */
@Composable
fun FocusButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.Primary,
    enabled: Boolean = true,
    icon: FocusIcon? = null,
) {
    val t = FocusTheme.tokens
    val c = t.colors
    val haptic = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduce = FocusTheme.reduceMotion
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = if (reduce) snap() else t.motion.spring(),
        label = "buttonScale",
    )
    val container: Color = when (style) {
        ButtonStyle.Primary -> c.primary
        ButtonStyle.Secondary -> c.surfaceHigh
        ButtonStyle.Ghost -> Color.Transparent
    }
    val contentColor: Color = when (style) {
        ButtonStyle.Primary -> c.onPrimary
        ButtonStyle.Secondary -> c.text
        ButtonStyle.Ghost -> c.primary
    }
    val border: BorderStroke? = when {
        style == ButtonStyle.Primary && t.hero == HeroTheme.DASH -> BorderStroke(t.strokeWidth, c.outline)
        style == ButtonStyle.Primary -> null
        style == ButtonStyle.Secondary -> BorderStroke(t.strokeWidth, c.outline)
        else -> null
    }
    val label = if (t.hero == HeroTheme.DASH) text.uppercase() else text

    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .alpha(if (enabled) 1f else 0.4f)
            .heightIn(min = 48.dp)
            .clip(t.shapes.button)
            .background(container)
            .then(if (border != null) Modifier.border(border, t.shapes.button) else Modifier)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
            ) {
                haptic(Haptic.Confirm)
                onClick()
            }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            FocusIconView(icon, contentColor, size = 20.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(label, style = t.typography.title.copy(fontSize = 16.sp, lineHeight = 22.sp), color = contentColor)
    }
}
