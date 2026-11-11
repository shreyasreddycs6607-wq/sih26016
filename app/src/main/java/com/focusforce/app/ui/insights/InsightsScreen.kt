package com.focusforce.app.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusforce.app.R
import com.focusforce.app.domain.usage.AppCategory
import com.focusforce.app.ui.components.ButtonStyle
import com.focusforce.app.ui.components.ContentColumn
import com.focusforce.app.ui.components.FocusButton
import com.focusforce.app.ui.components.FocusCard
import com.focusforce.app.ui.components.FocusSegmented
import com.focusforce.app.ui.components.HeatMap
import com.focusforce.app.ui.components.RefreshWhileVisible
import com.focusforce.app.ui.components.ScreenTitle
import com.focusforce.app.ui.components.SectionLabel
import com.focusforce.app.ui.components.ShimmerBox
import com.focusforce.app.ui.components.SplitBar
import com.focusforce.app.ui.components.SplitPart
import com.focusforce.app.ui.components.UsageBarChart
import com.focusforce.app.ui.theme.FocusTheme
import com.focusforce.app.ui.util.durationText
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun InsightsScreen(
    onOpenApp: (String) -> Unit,
    onOpenCategories: () -> Unit,
    onOpenPermissions: () -> Unit,
    viewModel: InsightsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val period by viewModel.selectedPeriod.collectAsStateWithLifecycle()
    RefreshWhileVisible { viewModel.refresh() }
    val c = FocusTheme.colors
    val periods = listOf(R.string.period_daily, R.string.period_weekly, R.string.period_monthly).map { stringResource(it) }

    ContentColumn {
        ScreenTitle(stringResource(R.string.tab_insights))
        FocusSegmented(periods, period.ordinal, { viewModel.setPeriod(InsightsPeriod.entries[it]) }, Modifier.fillMaxWidth())

        when (val s = state) {
            InsightsState.Loading -> {
                ShimmerBox(Modifier.fillMaxWidth().height(220.dp))
                ShimmerBox(Modifier.fillMaxWidth().height(120.dp))
            }
            InsightsState.NeedsPermission -> FocusCard(Modifier.fillMaxWidth(), selected = true) {
                SectionLabel(stringResource(R.string.usage_needed_title))
                Spacer(Modifier.height(FocusTheme.spacing.sm))
                Text(stringResource(R.string.usage_needed_body), style = FocusTheme.typography.body, color = c.text)
                Spacer(Modifier.height(FocusTheme.spacing.md))
                FocusButton(stringResource(R.string.usage_needed_action), onOpenPermissions, Modifier.fillMaxWidth())
            }
            is InsightsState.Ready -> InsightsBody(s.ui, viewModel, onOpenApp, onOpenCategories)
        }
    }
}

@Composable
private fun InsightsBody(
    ui: InsightsUi,
    viewModel: InsightsViewModel,
    onOpenApp: (String) -> Unit,
    onOpenCategories: () -> Unit,
) {
    val c = FocusTheme.colors
    var selected by rememberSaveable(ui.period, ui.anchor) { mutableStateOf<Int?>(null) }
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    if (ui.period == InsightsPeriod.DAILY) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FocusButton("‹", { viewModel.shiftDay(-1) }, enabled = ui.canGoBack, style = ButtonStyle.Secondary)
            Text(
                ui.anchor.format(dateFormat),
                style = FocusTheme.typography.title,
                color = c.text,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            FocusButton("›", { viewModel.shiftDay(1) }, enabled = ui.canGoForward, style = ButtonStyle.Secondary)
        }
    }

    FocusCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FocusTheme.spacing.xl)) {
            Column {
                SectionLabel(stringResource(R.string.insights_total))
                Text(durationText(ui.totalMs), style = FocusTheme.typography.numeral, color = c.primary)
            }
            ui.averageMs?.let {
                Column {
                    SectionLabel(stringResource(R.string.insights_daily_avg))
                    Text(durationText(it), style = FocusTheme.typography.numeral, color = c.text)
                }
            }
            ui.unlocks?.let {
                Column {
                    SectionLabel(stringResource(R.string.insights_unlocks))
                    Text(it.toString(), style = FocusTheme.typography.numeral, color = c.text)
                }
            }
        }
        Spacer(Modifier.height(FocusTheme.spacing.md))
        if (!ui.hasData) {
            Text(stringResource(R.string.insights_no_data), style = FocusTheme.typography.body, color = c.textMuted)
        } else {
            val description = stringResource(
                if (ui.period == InsightsPeriod.DAILY) R.string.chart_desc_hourly else R.string.chart_desc_daily,
                durationText(ui.totalMs),
            )
            UsageBarChart(
                values = ui.values,
                labels = ui.labels,
                description = description,
                goalMs = ui.goalMs,
                selectedIndex = selected,
                onSelect = { selected = it },
            )
            val idx = selected
            val readout = if (idx != null && idx in ui.values.indices) {
                val v = ui.values[idx]
                ui.slotTitles[idx] + " · " + (if (v == null) stringResource(R.string.insights_slot_no_data) else durationText(v))
            } else {
                stringResource(R.string.insights_tap_hint)
            }
            Text(readout, style = FocusTheme.typography.label, color = c.textMuted)
            if (ui.goalMs != null) {
                Text(stringResource(R.string.insights_goal_line), style = FocusTheme.typography.label, color = c.tertiary)
            }
        }
    }

    // productive vs distracting
    FocusCard(Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.insights_categories))
        Spacer(Modifier.height(FocusTheme.spacing.sm))
        val total = ui.split.values.sum()
        if (total <= 0L) {
            Text(stringResource(R.string.insights_no_data_period), style = FocusTheme.typography.body, color = c.textMuted)
        } else {
            SplitBar(AppCategory.entries.map { SplitPart(categoryColor(it), ui.split.getValue(it)) })
            Spacer(Modifier.height(FocusTheme.spacing.sm))
            AppCategory.entries.forEach { cat ->
                val ms = ui.split.getValue(cat)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.layout.Box(
                        Modifier.size(10.dp).background(categoryColor(cat)),
                    )
                    Text(
                        stringResource(cat.labelRes),
                        style = FocusTheme.typography.body,
                        color = c.text,
                        modifier = Modifier.weight(1f).padding(start = FocusTheme.spacing.sm),
                    )
                    Text(
                        stringResource(R.string.category_share, durationText(ms), (ms * 100 / total).toInt()),
                        style = FocusTheme.typography.label,
                        color = c.textMuted,
                    )
                }
            }
        }
        Spacer(Modifier.height(FocusTheme.spacing.md))
        FocusButton(stringResource(R.string.categories_edit), onOpenCategories, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary)
    }

    // apps in this period
    FocusCard(Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.insights_apps))
        Spacer(Modifier.height(FocusTheme.spacing.sm))
        if (ui.apps.isEmpty()) {
            Text(stringResource(R.string.insights_no_data_period), style = FocusTheme.typography.body, color = c.textMuted)
        } else {
            val max = ui.apps.maxOf { it.foregroundMs }
            ui.apps.forEach { AppUsageRow(it, max) { onOpenApp(it.packageName) } }
        }
    }

    // peak-hours heat map (always the last 7 days)
    FocusCard(Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.insights_heatmap))
        Spacer(Modifier.height(FocusTheme.spacing.sm))
        val peak = ui.peakHour
        val peakText = if (peak == null) stringResource(R.string.insights_no_data_period) else stringResource(R.string.insights_peak_hour, "%d:00".format(peak))
        HeatMap(ui.heatGrid, ui.heatLabels, description = peakText)
        Spacer(Modifier.height(FocusTheme.spacing.sm))
        Text(peakText, style = FocusTheme.typography.body, color = c.textMuted)
    }
}
