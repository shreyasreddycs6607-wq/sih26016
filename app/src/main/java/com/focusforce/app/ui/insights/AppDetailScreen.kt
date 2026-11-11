package com.focusforce.app.ui.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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
import com.focusforce.app.ui.components.AppIcon
import com.focusforce.app.ui.components.ButtonStyle
import com.focusforce.app.ui.components.ContentColumn
import com.focusforce.app.ui.components.FocusButton
import com.focusforce.app.ui.components.FocusCard
import com.focusforce.app.ui.components.FocusSegmented
import com.focusforce.app.ui.components.ScreenTopBar
import com.focusforce.app.ui.components.SectionLabel
import com.focusforce.app.ui.components.ShimmerBox
import com.focusforce.app.ui.components.ThemedBackground
import com.focusforce.app.ui.components.UsageBarChart
import com.focusforce.app.ui.theme.FocusTheme
import com.focusforce.app.ui.util.durationText

/** Per-app drill-down: last 14 days, today / average / opens, and the editable category. */
@Composable
fun AppDetailScreen(onBack: () -> Unit, viewModel: AppDetailViewModel = hiltViewModel()) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val c = FocusTheme.colors
    ThemedBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            ScreenTopBar(ui?.label ?: stringResource(R.string.app_detail_title), onBack)
            val detail = ui
            if (detail == null) {
                ContentColumn { ShimmerBox(Modifier.fillMaxWidth().height(240.dp)) }
            } else {
                ContentColumn {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(detail.packageName, size = 56.dp)
                        Column(Modifier.padding(start = FocusTheme.spacing.md)) {
                            Text(detail.label, style = FocusTheme.typography.headline, color = c.text)
                            Text(detail.packageName, style = FocusTheme.typography.label, color = c.textMuted)
                            if (!detail.installed) Text(stringResource(R.string.app_uninstalled), style = FocusTheme.typography.label, color = c.danger)
                        }
                    }

                    FocusCard(Modifier.fillMaxWidth()) {
                        SectionLabel(stringResource(R.string.app_category_title))
                        Spacer(Modifier.height(FocusTheme.spacing.sm))
                        CategoryPicker(detail.category) { viewModel.setCategory(it) }
                        Spacer(Modifier.height(FocusTheme.spacing.sm))
                        Text(
                            stringResource(if (detail.overridden) R.string.category_set_by_you else R.string.category_automatic),
                            style = FocusTheme.typography.label,
                            color = c.textMuted,
                        )
                        if (detail.overridden) {
                            Spacer(Modifier.height(FocusTheme.spacing.sm))
                            FocusButton(stringResource(R.string.category_reset), { viewModel.setCategory(null) }, Modifier.fillMaxWidth(), style = ButtonStyle.Ghost)
                        }
                    }

                    FocusCard(Modifier.fillMaxWidth()) {
                        var selected by rememberSaveable { mutableStateOf<Int?>(null) }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FocusTheme.spacing.xl)) {
                            Column {
                                SectionLabel(stringResource(R.string.app_today))
                                Text(durationText(detail.todayMs), style = FocusTheme.typography.numeral, color = c.primary)
                            }
                            detail.averageMs?.let {
                                Column {
                                    SectionLabel(stringResource(R.string.insights_daily_avg))
                                    Text(durationText(it), style = FocusTheme.typography.numeral, color = c.text)
                                }
                            }
                            Column {
                                SectionLabel(stringResource(R.string.app_opens_14d))
                                Text(detail.launches.toString(), style = FocusTheme.typography.numeral, color = c.text)
                            }
                        }
                        Spacer(Modifier.height(FocusTheme.spacing.md))
                        UsageBarChart(
                            values = detail.values,
                            labels = detail.labels,
                            description = stringResource(R.string.chart_desc_app, detail.label, durationText(detail.todayMs)),
                            selectedIndex = selected,
                            onSelect = { selected = it },
                        )
                        val idx = selected
                        Text(
                            if (idx != null && idx in detail.values.indices) {
                                detail.slotTitles[idx] + " · " + (detail.values[idx]?.let { durationText(it) } ?: stringResource(R.string.insights_slot_no_data))
                            } else {
                                stringResource(R.string.insights_tap_hint)
                            },
                            style = FocusTheme.typography.label,
                            color = c.textMuted,
                        )
                    }
                }
            }
        }
    }
}

/** Productive / Distracting / Neutral selector shared by the detail and categories screens. */
@Composable
fun CategoryPicker(current: AppCategory, modifier: Modifier = Modifier, onPick: (AppCategory) -> Unit) {
    val labels = AppCategory.entries.map { stringResource(it.labelRes) }
    FocusSegmented(labels, current.ordinal, { onPick(AppCategory.entries[it]) }, modifier.fillMaxWidth())
}
