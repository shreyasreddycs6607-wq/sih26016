package com.focusforce.app.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.focusforce.app.domain.model.HeroTheme
import com.focusforce.app.ui.art.HeroMascot
import com.focusforce.app.ui.art.HeroPose
import com.focusforce.app.ui.components.AnimatedCounter
import com.focusforce.app.ui.components.BottomBarItem
import com.focusforce.app.ui.components.ButtonStyle
import com.focusforce.app.ui.components.ContentColumn
import com.focusforce.app.ui.components.FocusBottomBar
import com.focusforce.app.ui.components.FocusButton
import com.focusforce.app.ui.components.FocusCard
import com.focusforce.app.ui.components.FocusIcon
import com.focusforce.app.ui.components.FocusIconView
import com.focusforce.app.ui.components.ScreenTitle
import com.focusforce.app.ui.components.SectionLabel
import com.focusforce.app.ui.components.ShimmerBox
import com.focusforce.app.ui.components.StatRing
import com.focusforce.app.ui.components.ThemedBackground
import com.focusforce.app.ui.theme.FocusForceTheme
import com.focusforce.app.ui.theme.FocusTheme

/**
 * Debug-only component gallery (reachable from Profile in debug builds). Switch hero at the top to
 * review every component under every theme. Strings here are intentionally literal: this screen is
 * never shipped in release builds.
 */
@Composable
fun GalleryScreen(onBack: () -> Unit) {
    var hero by rememberSaveable { mutableStateOf(HeroTheme.VECTOR) }
    FocusForceTheme(hero) {
        val c = FocusTheme.colors
        ThemedBackground(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = FocusTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clickable(role = Role.Button, onClick = onBack), contentAlignment = Alignment.Center) {
                        FocusIconView(FocusIcon.Back, c.text, contentDescription = "Back")
                    }
                    Text("Component gallery (debug)", style = FocusTheme.typography.title, color = c.text)
                }
                GalleryBody(hero) { hero = it }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GalleryBody(hero: HeroTheme, onHero: (HeroTheme) -> Unit) {
    val c = FocusTheme.colors
    val t = FocusTheme.typography
    var progress by rememberSaveable { mutableFloatStateOf(0.42f) }
    var counter by rememberSaveable { mutableIntStateOf(120) }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    ContentColumn {
        SectionLabel("Hero")
        Row(horizontalArrangement = Arrangement.spacedBy(FocusTheme.spacing.sm)) {
            HeroTheme.entries.forEach {
                FocusButton(it.name, { onHero(it) }, Modifier.weight(1f), style = if (it == hero) ButtonStyle.Primary else ButtonStyle.Secondary)
            }
        }

        SectionLabel("Color tokens")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "bg" to c.background, "surface" to c.surface, "surfaceHigh" to c.surfaceHigh, "primary" to c.primary,
                "secondary" to c.secondary, "tertiary" to c.tertiary, "text" to c.text, "muted" to c.textMuted,
                "outline" to c.outline, "danger" to c.danger, "success" to c.success,
            ).forEach { (name, color) -> Swatch(name, color) }
        }

        SectionLabel("Type scale")
        FocusCard(Modifier.fillMaxWidth()) {
            Text("Display 36", style = t.display, color = c.text)
            Text("Headline 28", style = t.headline, color = c.text)
            Text("Title 20", style = t.title, color = c.text)
            Text("Body 16: the quick hero guards your focus.", style = t.body, color = c.text)
            Text("Label 13 · muted", style = t.label, color = c.textMuted)
            Text("0123456789", style = t.numeral, color = c.primary)
        }

        SectionLabel("Buttons")
        FocusButton("Primary", {}, Modifier.fillMaxWidth(), icon = FocusIcon.Focus)
        FocusButton("Secondary", {}, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary)
        FocusButton("Ghost", {}, Modifier.fillMaxWidth(), style = ButtonStyle.Ghost)
        FocusButton("Disabled", {}, Modifier.fillMaxWidth(), enabled = false)

        SectionLabel("Cards")
        FocusCard(Modifier.fillMaxWidth()) { Text("Static card", style = t.body, color = c.text) }
        FocusCard(Modifier.fillMaxWidth(), onClick = {}) { Text("Clickable card", style = t.body, color = c.text) }
        FocusCard(Modifier.fillMaxWidth(), selected = true) { Text("Selected card", style = t.body, color = c.text) }

        SectionLabel("Stat ring + counter")
        FocusCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                StatRing(progress, "Progress ${(progress * 100).toInt()} percent", size = 150.dp) {
                    Text("${(progress * 100).toInt()}%", style = t.numeral, color = c.text)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedCounter(counter, color = c.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FocusButton("-15", { counter = (counter - 15).coerceAtLeast(0) }, style = ButtonStyle.Secondary)
                        FocusButton("+15", { counter += 15 }, style = ButtonStyle.Secondary)
                    }
                }
            }
            Slider(value = progress, onValueChange = { progress = it })
        }

        SectionLabel("Shimmer")
        ShimmerBox(Modifier.fillMaxWidth().height(20.dp))
        ShimmerBox(Modifier.fillMaxWidth(0.6f).height(20.dp))
        ShimmerBox(Modifier.fillMaxWidth().height(80.dp))

        SectionLabel("Mascot poses")
        FocusCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                HeroPose.entries.forEach { pose ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        HeroMascot(hero, Modifier.height(130.dp), pose = pose)
                        Text(pose.name, style = t.label, color = c.textMuted)
                    }
                }
            }
        }

        SectionLabel("Icons")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            FocusIcon.entries.forEach { FocusIconView(it, c.primary, size = 32.dp) }
        }

        SectionLabel("Bottom bar")
        FocusBottomBar(
            items = listOf(
                BottomBarItem(FocusIcon.Home, "Home"), BottomBarItem(FocusIcon.Focus, "Focus"),
                BottomBarItem(FocusIcon.Insights, "Insights"), BottomBarItem(FocusIcon.Blocks, "Blocks"),
                BottomBarItem(FocusIcon.Profile, "Profile"),
            ),
            selectedIndex = tab,
            onSelect = { tab = it },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(FocusTheme.spacing.xl))
    }
}

@Composable
private fun Swatch(name: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(48.dp).background(color, RoundedCornerShape(8.dp)),
        )
        Text(name, style = FocusTheme.typography.label, color = FocusTheme.colors.textMuted)
    }
}
