package com.focusforce.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.CornerRadius

/** Original line icons drawn on a 24x24 grid. No icon font or asset dependency. */
enum class FocusIcon { Home, Focus, Insights, Blocks, Profile, Back, Check, Clock, Layers, Eye, Bell, Battery, Alarm }

@Composable
fun FocusIconView(
    icon: FocusIcon,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    contentDescription: String? = null,
) {
    val sem = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }
    Canvas(modifier.size(size).then(sem)) {
        val unit = this.size.minDimension / 24f
        withTransform({ scale(unit, unit, pivot = Offset.Zero) }) { drawIcon(icon, tint) }
    }
}

private fun path(block: Path.() -> Unit) = Path().apply(block)

private fun DrawScope.drawIcon(icon: FocusIcon, tint: Color) {
    val stroke = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun line(p: Path) = drawPath(p, tint, style = stroke)
    when (icon) {
        FocusIcon.Home -> {
            line(path { moveTo(3f, 11f); lineTo(12f, 3f); lineTo(21f, 11f) })
            line(path { moveTo(5f, 10f); lineTo(5f, 20f); lineTo(19f, 20f); lineTo(19f, 10f) })
            line(path { moveTo(10f, 20f); lineTo(10f, 14f); lineTo(14f, 14f); lineTo(14f, 20f) })
        }
        FocusIcon.Focus -> {
            drawCircle(tint, 9f, Offset(12f, 12f), style = stroke)
            drawCircle(tint, 5f, Offset(12f, 12f), style = stroke)
            drawCircle(tint, 1.6f, Offset(12f, 12f), style = Fill)
        }
        FocusIcon.Insights -> {
            line(path { moveTo(6f, 20f); lineTo(6f, 12f) })
            line(path { moveTo(12f, 20f); lineTo(12f, 5f) })
            line(path { moveTo(18f, 20f); lineTo(18f, 9f) })
        }
        FocusIcon.Blocks -> line(path {
            moveTo(12f, 3f); lineTo(20f, 6f); lineTo(20f, 12f)
            cubicTo(20f, 17f, 16f, 20f, 12f, 21f)
            cubicTo(8f, 20f, 4f, 17f, 4f, 12f)
            lineTo(4f, 6f); close()
        })
        FocusIcon.Profile -> {
            drawCircle(tint, 4f, Offset(12f, 8f), style = stroke)
            line(path { moveTo(4f, 21f); cubicTo(4f, 16f, 8f, 14f, 12f, 14f); cubicTo(16f, 14f, 20f, 16f, 20f, 21f) })
        }
        FocusIcon.Back -> line(path { moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f) })
        FocusIcon.Check -> line(path { moveTo(4f, 12f); lineTo(10f, 18f); lineTo(20f, 6f) })
        FocusIcon.Clock -> {
            drawCircle(tint, 9f, Offset(12f, 12f), style = stroke)
            line(path { moveTo(12f, 7f); lineTo(12f, 12f); lineTo(16f, 14f) })
        }
        FocusIcon.Layers -> {
            drawRoundRect(tint, Offset(3f, 3f), Size(12f, 12f), CornerRadius(2f), style = stroke)
            drawRoundRect(tint, Offset(9f, 9f), Size(12f, 12f), CornerRadius(2f), style = stroke)
        }
        FocusIcon.Eye -> {
            line(path { moveTo(2f, 12f); quadraticTo(12f, 2f, 22f, 12f); quadraticTo(12f, 22f, 2f, 12f); close() })
            drawCircle(tint, 3f, Offset(12f, 12f), style = stroke)
        }
        FocusIcon.Bell -> {
            line(path {
                moveTo(7f, 18f); lineTo(7f, 11f)
                cubicTo(7f, 8f, 9.2f, 6f, 12f, 6f)
                cubicTo(14.8f, 6f, 17f, 8f, 17f, 11f)
                lineTo(17f, 18f)
            })
            line(path { moveTo(5f, 18f); lineTo(19f, 18f) })
            line(path { moveTo(10f, 21f); lineTo(14f, 21f) })
        }
        FocusIcon.Battery -> {
            drawRoundRect(tint, Offset(3f, 7f), Size(16f, 10f), CornerRadius(2f), style = stroke)
            line(path { moveTo(21f, 10f); lineTo(21f, 14f) })
            line(path { moveTo(12f, 9f); lineTo(9.5f, 12.5f); lineTo(13f, 12.5f); lineTo(10.5f, 16f) })
        }
        FocusIcon.Alarm -> {
            drawCircle(tint, 7f, Offset(12f, 13f), style = stroke)
            line(path { moveTo(12f, 9f); lineTo(12f, 13f); lineTo(14.5f, 14.5f) })
            line(path { moveTo(3f, 7f); lineTo(6f, 4f) })
            line(path { moveTo(21f, 7f); lineTo(18f, 4f) })
        }
    }
}
