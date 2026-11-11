package com.focusforce.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.focusforce.app.domain.model.HeroTheme
import com.focusforce.app.ui.art.HeroMascot
import com.focusforce.app.ui.art.HeroPose
import com.focusforce.app.ui.theme.FocusTheme

/** Max readable content width: keeps phone layouts full-width and centres a column on tablets. */
val ContentMaxWidth: Dp = 640.dp

/** Scrolling, tablet-safe content column with the standard 16dp gutters. */
@Composable
fun ContentColumn(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(FocusTheme.spacing.lg),
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = ContentMaxWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FocusTheme.spacing.lg, vertical = FocusTheme.spacing.lg),
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = if (FocusTheme.hero == HeroTheme.DASH) text.uppercase() else text,
        style = FocusTheme.typography.headline,
        color = FocusTheme.colors.text,
        modifier = modifier.semantics { heading() },
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = FocusTheme.typography.label,
        color = FocusTheme.colors.textMuted,
        modifier = modifier.semantics { heading() },
    )
}

/**
 * Honest "this lands in a later phase" screen body. Used by tabs whose feature is not built yet,
 * so nothing pretends to work. Shows the guarding hero.
 */
@Composable
fun UpcomingFeature(
    title: String,
    phaseLabel: String,
    description: String,
    bullets: List<String>,
    modifier: Modifier = Modifier,
) {
    ContentColumn(modifier) {
        ScreenTitle(title)
        FocusCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeroMascot(FocusTheme.hero, Modifier.height(120.dp), pose = HeroPose.Guard)
                Column(Modifier.padding(start = FocusTheme.spacing.lg)) {
                    Text(phaseLabel, style = FocusTheme.typography.label, color = FocusTheme.colors.primary)
                    Spacer(Modifier.height(FocusTheme.spacing.xs))
                    Text(description, style = FocusTheme.typography.body, color = FocusTheme.colors.text)
                }
            }
        }
        FocusCard(Modifier.fillMaxWidth()) {
            bullets.forEach {
                Text("•  $it", style = FocusTheme.typography.body, color = FocusTheme.colors.textMuted, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}
