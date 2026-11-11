package com.focusforce.app.ui.block

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.focusforce.app.R
import com.focusforce.app.domain.blocking.BlockReason
import com.focusforce.app.domain.model.HeroTheme
import com.focusforce.app.ui.art.HeroMascot
import com.focusforce.app.ui.art.HeroPose
import com.focusforce.app.ui.components.AppIcon
import com.focusforce.app.ui.components.ButtonStyle
import com.focusforce.app.ui.components.ContentMaxWidth
import com.focusforce.app.ui.components.FocusButton
import com.focusforce.app.ui.components.Haptic
import com.focusforce.app.ui.components.ThemedBackground
import com.focusforce.app.ui.components.rememberHaptics
import com.focusforce.app.ui.theme.FocusColors
import com.focusforce.app.ui.theme.FocusTheme
import com.focusforce.app.ui.theme.nameRes
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The showpiece. The active hero stands guard in a Guard pose, bursts in with the theme's spring,
 * and speaks a short in-character line. VECTOR: HUD rings and "ACCESS RESTRICTED". NOCTIS: a pale moon
 * and a quiet "Rest now". DASH: a comic starburst and "HALT!".
 */
@Composable
fun BlockScreen(ui: BlockUiState, onHome: () -> Unit, onPass: () -> Unit, onLeave: () -> Unit) {
    val t = FocusTheme.tokens
    val c = t.colors
    val reduce = FocusTheme.reduceMotion
    val haptic = rememberHaptics()

    val entrance = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        haptic(Haptic.Confirm)
        if (!reduce) entrance.animateTo(1f, t.motion.spring())
    }

    val lines = stringArrayResource(
        when (ui.hero) {
            HeroTheme.VECTOR -> R.array.block_lines_vector
            HeroTheme.NOCTIS -> R.array.block_lines_noctis
            HeroTheme.DASH -> R.array.block_lines_dash
        },
    )
    // Stable for this appearance, different from app to app and over time.
    val line = remember(ui.packageName) { lines[(ui.packageName.hashCode() and 0x7fffffff) % lines.size] }
    val banner = stringResource(
        when (ui.hero) {
            HeroTheme.VECTOR -> R.string.block_banner_vector
            HeroTheme.NOCTIS -> R.string.block_banner_noctis
            HeroTheme.DASH -> R.string.block_banner_dash
        },
    )
    val isAppReason = ui.reason != BlockReason.STRICT_SETTINGS && ui.reason != BlockReason.STRICT_UNINSTALL
    val title = when (ui.reason) {
        BlockReason.BLOCKED_APP -> stringResource(R.string.block_title, ui.label)
        BlockReason.LIMIT_REACHED -> stringResource(R.string.block_title_limit, ui.label)
        BlockReason.SCHEDULE -> stringResource(R.string.block_title_schedule, ui.label)
        BlockReason.POMODORO -> stringResource(R.string.block_title_pomodoro)
        BlockReason.SHORT_VIDEO -> stringResource(R.string.block_title_feed, ui.label)
        BlockReason.WEBSITE -> stringResource(R.string.block_title_site, ui.detail ?: ui.label)
        BlockReason.STRICT_SETTINGS -> stringResource(R.string.block_title_settings)
        BlockReason.STRICT_UNINSTALL -> stringResource(R.string.block_title_uninstall)
    }

    ThemedBackground(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) { drawBackdrop(ui.hero, c, entrance.value) }
            Column(
                Modifier
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .widthIn(max = ContentMaxWidth)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = FocusTheme.spacing.xl, vertical = FocusTheme.spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(banner, style = t.typography.label, color = c.secondary, textAlign = TextAlign.Center)
                Spacer(Modifier.height(FocusTheme.spacing.md))
                HeroMascot(
                    ui.hero,
                    Modifier
                        .height(220.dp)
                        .graphicsLayer {
                            val e = entrance.value
                            scaleX = 0.5f + 0.5f * e
                            scaleY = 0.5f + 0.5f * e
                            translationY = (1f - e) * 80f
                        },
                    pose = HeroPose.Guard,
                    contentDescription = stringResource(R.string.block_mascot_description, stringResource(ui.hero.nameRes)),
                )
                Spacer(Modifier.height(FocusTheme.spacing.md))
                if (isAppReason) {
                    AppIcon(ui.packageName, size = 40.dp)
                    Spacer(Modifier.height(FocusTheme.spacing.sm))
                }
                Text(title, style = t.typography.headline, color = c.text, textAlign = TextAlign.Center)
                Spacer(Modifier.height(FocusTheme.spacing.sm))
                Text(
                    "“$line”",
                    style = t.typography.body,
                    color = c.text,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(FocusTheme.spacing.sm))
                Text(
                    stringResource(
                        when {
                            ui.reason == BlockReason.STRICT_SETTINGS -> R.string.block_reason_settings
                            ui.reason == BlockReason.STRICT_UNINSTALL -> R.string.block_reason_uninstall
                            ui.reason == BlockReason.LIMIT_REACHED -> R.string.block_limit_note
                            ui.reason == BlockReason.SCHEDULE -> R.string.block_schedule_note
                            ui.reason == BlockReason.POMODORO -> R.string.block_pomodoro_note
                            ui.reason == BlockReason.SHORT_VIDEO -> R.string.block_feed_note
                            ui.reason == BlockReason.WEBSITE -> R.string.block_site_note
                            ui.strictActive -> R.string.block_strict_note
                            else -> R.string.block_default_note
                        },
                    ),
                    style = t.typography.body,
                    color = c.textMuted,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(FocusTheme.spacing.xl))
                val canLeave = ui.reason == BlockReason.SHORT_VIDEO || ui.reason == BlockReason.WEBSITE
                if (canLeave) {
                    FocusButton(
                        stringResource(if (ui.reason == BlockReason.SHORT_VIDEO) R.string.block_leave_feed else R.string.block_leave_page),
                        onLeave,
                        Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(FocusTheme.spacing.md))
                    FocusButton(stringResource(R.string.block_go_home), onHome, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary)
                } else {
                    FocusButton(stringResource(R.string.block_go_home), onHome, Modifier.fillMaxWidth())
                }
                if (isAppReason) {
                    Spacer(Modifier.height(FocusTheme.spacing.md))
                    if (ui.canPass) {
                        FocusButton(
                            stringResource(R.string.block_pass, ui.passesLeft),
                            onPass,
                            Modifier.fillMaxWidth(),
                            style = if (canLeave) ButtonStyle.Ghost else ButtonStyle.Secondary,
                        )
                    } else {
                        Text(
                            stringResource(
                                when {
                                    ui.reason == BlockReason.POMODORO -> R.string.block_no_pass_pomodoro
                                    ui.strictActive -> R.string.block_no_pass_strict
                                    else -> R.string.block_no_pass_left
                                },
                            ),
                            style = t.typography.label,
                            color = c.textMuted,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawBackdrop(hero: HeroTheme, c: FocusColors, e: Float) {
    val center = Offset(size.width / 2, size.height * 0.30f)
    val base = size.minDimension
    when (hero) {
        HeroTheme.VECTOR -> {
            for (i in 1..4) {
                drawCircle(c.primary.copy(alpha = 0.16f / i), base * (0.16f + 0.12f * i) * e, center, style = Stroke(1.5.dp.toPx()))
            }
        }
        HeroTheme.NOCTIS -> {
            drawCircle(c.tertiary.copy(alpha = 0.10f), base * 0.42f * e, center)
            drawCircle(c.tertiary.copy(alpha = 0.06f), base * 0.58f * e, center)
        }
        HeroTheme.DASH -> {
            val spikes = 14
            val outer = base * 0.52f * e
            val inner = outer * 0.62f
            val burst = Path()
            for (i in 0 until spikes * 2) {
                val r = if (i % 2 == 0) outer else inner
                val a = (PI * i / spikes).toFloat()
                val p = Offset(center.x + cos(a) * r, center.y + sin(a) * r)
                if (i == 0) burst.moveTo(p.x, p.y) else burst.lineTo(p.x, p.y)
            }
            burst.close()
            drawPath(burst, c.secondary.copy(alpha = 0.22f))
            drawPath(burst, c.outline.copy(alpha = 0.5f), style = Stroke(3.dp.toPx()))
        }
    }
}
