package com.focusforce.app.ui.blocks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusforce.app.R
import com.focusforce.app.domain.usage.AppCategory
import com.focusforce.app.ui.components.AppIcon
import com.focusforce.app.ui.components.ButtonStyle
import com.focusforce.app.ui.components.ContentMaxWidth
import com.focusforce.app.ui.components.FocusButton
import com.focusforce.app.ui.components.FocusCard
import com.focusforce.app.ui.components.FocusCheckMark
import com.focusforce.app.ui.components.FocusSegmented
import com.focusforce.app.ui.components.FocusTextField
import com.focusforce.app.ui.components.ScreenTopBar
import com.focusforce.app.ui.components.ShimmerBox
import com.focusforce.app.ui.components.ThemedBackground
import com.focusforce.app.ui.insights.labelRes
import com.focusforce.app.ui.theme.FocusTheme
import kotlinx.coroutines.delay

/** Searchable app picker with icons, category filter and "select everything shown" (select-by-category). */
@Composable
fun AppPickerScreen(onBack: () -> Unit, viewModel: AppPickerViewModel = hiltViewModel()) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val c = FocusTheme.colors
    val filters = listOf<AppCategory?>(null) + AppCategory.entries
    val filterLabels = filters.map { if (it == null) stringResource(R.string.picker_all) else stringResource(it.labelRes) }

    ThemedBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            ScreenTopBar(stringResource(R.string.picker_title), onBack)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxSize().padding(horizontal = FocusTheme.spacing.lg)) {
                    FocusTextField(ui.query, viewModel::setQuery, stringResource(R.string.picker_search))
                    Spacer(Modifier.height(FocusTheme.spacing.sm))
                    FocusSegmented(filterLabels, filters.indexOf(ui.filter), { viewModel.setFilter(filters[it]) }, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(FocusTheme.spacing.sm))
                    ui.notice?.let {
                        LaunchedEffect(it) { delay(5_000L); viewModel.clearNotice() }
                        Text(stringResource(it), style = FocusTheme.typography.body, color = c.danger)
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.picker_selected_count, ui.selectedCount),
                            style = FocusTheme.typography.label,
                            color = c.textMuted,
                            modifier = Modifier.weight(1f),
                        )
                        if (ui.apps.isNotEmpty() && !ui.single) {
                            FocusButton(
                                stringResource(if (ui.allShownSelected) R.string.picker_unselect_shown else R.string.picker_select_shown, ui.apps.size),
                                viewModel::toggleAllShown,
                                style = ButtonStyle.Ghost,
                            )
                        }
                    }
                    when {
                        ui.loading -> ShimmerBox(Modifier.fillMaxWidth().height(200.dp))
                        ui.apps.isEmpty() -> Text(stringResource(R.string.picker_empty), style = FocusTheme.typography.body, color = c.textMuted)
                        else -> LazyColumn(
                            Modifier.weight(1f),
                            contentPadding = PaddingValues(bottom = FocusTheme.spacing.xl),
                            verticalArrangement = Arrangement.spacedBy(FocusTheme.spacing.sm),
                        ) {
                            items(ui.apps, key = { it.packageName }) { app ->
                                FocusCard(
                                    Modifier.fillMaxWidth().toggleable(value = app.selected, role = if (ui.single) Role.RadioButton else Role.Checkbox) {
                                        viewModel.toggle(app)
                                        if (ui.single) onBack()
                                    },
                                    selected = app.selected,
                                    contentPadding = FocusTheme.spacing.md,
                                ) {
                                    Row(Modifier.heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                                        AppIcon(app.packageName)
                                        Column(Modifier.weight(1f).padding(horizontal = FocusTheme.spacing.md)) {
                                            Text(app.label, style = FocusTheme.typography.body, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(stringResource(app.category.labelRes), style = FocusTheme.typography.label, color = c.textMuted)
                                        }
                                        FocusCheckMark(app.selected)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
