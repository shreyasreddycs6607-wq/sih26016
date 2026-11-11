package com.focusforce.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusforce.app.ui.theme.FocusTheme

/** Labelled on/off switch in the hero's style. The whole row is the touch target (min 48dp tall). */
@Composable
fun FocusSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
) {
    val t = FocusTheme.tokens
    val c = t.colors
    val haptic = rememberHaptics()
    val reduce = FocusTheme.reduceMotion
    val thumbX by animateDpAsState(if (checked) 22.dp else 2.dp, if (reduce) snap() else t.motion.spring(), label = "switchThumb")
    val track by animateColorAsState(if (checked) c.primary else c.surfaceHigh, if (reduce) snap() else t.motion.fast(), label = "switchTrack")
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .toggleable(value = checked, enabled = true, role = Role.Switch) {
                haptic(Haptic.Tick)
                onCheckedChange(!checked)
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FocusTheme.spacing.md),
    ) {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(label, style = t.typography.body, color = c.text)
            if (description != null) Text(description, style = t.typography.label, color = c.textMuted)
        }
        Box(
            Modifier.size(width = 48.dp, height = 28.dp).clip(t.shapes.button).background(track)
                .border(t.strokeWidth.coerceAtLeast(1.dp), c.outline, t.shapes.button),
        ) {
            Box(
                Modifier.offset(x = thumbX, y = 3.dp).size(22.dp).clip(CircleShape)
                    .background(if (checked) c.onPrimary else c.textMuted),
            )
        }
    }
}

/** Single-line search field in the hero's style. [placeholder] doubles as the accessibility label. */
@Composable
fun FocusTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val t = FocusTheme.tokens
    val c = t.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = t.typography.body.copy(color = c.text),
        cursorBrush = SolidColor(c.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = modifier.fillMaxWidth().semantics { contentDescription = placeholder },
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(t.shapes.button).background(c.surfaceHigh)
                    .border(t.strokeWidth, c.outline, t.shapes.button).padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) Text(placeholder, style = t.typography.body, color = c.textMuted)
                inner()
            }
        },
    )
}

/** Checkbox row control: a themed box with a drawn check mark. Pair with a label in the same toggleable row. */
@Composable
fun FocusCheckMark(checked: Boolean, modifier: Modifier = Modifier) {
    val t = FocusTheme.tokens
    val c = t.colors
    Box(
        modifier.size(26.dp).clip(t.shapes.small)
            .background(if (checked) c.primary else c.surfaceHigh)
            .border(t.strokeWidth.coerceAtLeast(1.dp), c.outline, t.shapes.small),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) FocusIconView(FocusIcon.Check, c.onPrimary, size = 18.dp)
    }
}

/** Seven weekday toggles (Mon..Sun). Each reads its full day name to TalkBack. */
@Composable
fun DayPicker(mask: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val t = FocusTheme.tokens
    val c = t.colors
    val haptic = rememberHaptics()
    val locale = java.util.Locale.getDefault()
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        java.time.DayOfWeek.entries.forEach { day ->
            val on = com.focusforce.app.domain.limits.DaysMask.has(mask, day)
            val full = day.getDisplayName(java.time.format.TextStyle.FULL, locale)
            Box(
                Modifier.weight(1f).heightIn(min = 44.dp).clip(t.shapes.small)
                    .background(if (on) c.primary else c.surfaceHigh)
                    .border(t.strokeWidth.coerceAtLeast(1.dp), c.outline, t.shapes.small)
                    .toggleable(value = on, role = Role.Checkbox) {
                        haptic(Haptic.Tick)
                        onChange(com.focusforce.app.domain.limits.DaysMask.toggle(mask, day))
                    }
                    .semantics { contentDescription = full },
                contentAlignment = Alignment.Center,
            ) {
                Text(day.getDisplayName(java.time.format.TextStyle.NARROW, locale), style = t.typography.label, color = if (on) c.onPrimary else c.text)
            }
        }
    }
}

/** "Label    [-]  value  [+]" row for numbers that move in fixed steps. */
@Composable
fun StepperRow(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
    minusEnabled: Boolean = true,
    plusEnabled: Boolean = true,
) {
    val t = FocusTheme.tokens
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FocusTheme.spacing.sm)) {
        Text(label, style = t.typography.body, color = t.colors.text, modifier = Modifier.weight(1f))
        FocusButton("−", onMinus, enabled = minusEnabled, style = ButtonStyle.Secondary)
        Text(value, style = t.typography.numeral.copy(fontSize = 18.sp), color = t.colors.primary, modifier = Modifier.widthIn(min = 64.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        FocusButton("+", onPlus, enabled = plusEnabled, style = ButtonStyle.Secondary)
    }
}
