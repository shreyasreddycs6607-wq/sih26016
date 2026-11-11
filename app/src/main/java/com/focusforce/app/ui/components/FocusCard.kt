package com.focusforce.app.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.focusforce.app.domain.model.HeroTheme
import com.focusforce.app.ui.theme.FocusTheme

/**
 * The themed panel. VECTOR: chamfered glass with corner brackets. NOCTIS: soft moonlit card.
 * DASH: slanted comic panel with a thick outline and a hard offset shadow.
 */
@Composable
fun FocusCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    contentPadding: Dp = FocusTheme.spacing.lg,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = FocusTheme.tokens
    val c = t.colors
    val shape = t.shapes.card
    val haptic = rememberHaptics()
    val borderColor = if (selected) c.primary else when (t.hero) {
        HeroTheme.VECTOR -> c.primary.copy(alpha = 0.45f)
        HeroTheme.NOCTIS -> c.text.copy(alpha = 0.18f)
        HeroTheme.DASH -> c.outline
    }
    val borderWidth = if (selected) maxOf(t.strokeWidth, 2.dp) else t.strokeWidth
    val fill = when (t.hero) {
        HeroTheme.VECTOR -> Brush.verticalGradient(listOf(c.surfaceHigh.copy(alpha = 0.78f), c.surface.copy(alpha = 0.66f)))
        HeroTheme.NOCTIS -> Brush.linearGradient(listOf(c.surfaceHigh, c.surface))
        HeroTheme.DASH -> Brush.linearGradient(listOf(c.surface, c.surface))
    }
    val interaction = remember { MutableInteractionSource() }
    val ripple = LocalIndication.current

    val decorated = modifier
        .drawBehind {
            if (t.hero == HeroTheme.DASH) {
                // hard comic drop shadow
                val outline = shape.createOutline(size, layoutDirection, this)
                translate(left = 5.dp.toPx(), top = 5.dp.toPx()) {
                    drawOutline(outline, if (selected) c.secondary else c.primary.copy(alpha = 0.85f))
                }
            }
        }
        .clip(shape)
        .background(fill)
        .border(borderWidth, borderColor, shape)
        .drawBehind {
            if (t.hero == HeroTheme.VECTOR) {
                // HUD corner brackets
                val len = 14.dp.toPx()
                val w = 2.dp.toPx()
                val col = c.primary
                drawLine(col, Offset(0f, 0f), Offset(len, 0f), w)
                drawLine(col, Offset(0f, 0f), Offset(0f, len), w)
                drawLine(col, Offset(size.width, size.height), Offset(size.width - len, size.height), w)
                drawLine(col, Offset(size.width, size.height), Offset(size.width, size.height - len), w)
            }
        }
        .then(
            if (onClick != null) {
                Modifier.clickable(interactionSource = interaction, indication = ripple, role = Role.Button) {
                    haptic(Haptic.Tick)
                    onClick()
                }
            } else {
                Modifier
            },
        )
    Column(decorated.padding(contentPadding), content = content)
}
