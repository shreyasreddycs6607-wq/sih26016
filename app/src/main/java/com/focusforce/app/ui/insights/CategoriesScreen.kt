package com.focusforce.app.ui.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusforce.app.R
import com.focusforce.app.ui.components.AppIcon
import com.focusforce.app.ui.components.ContentMaxWidth
import com.focusforce.app.ui.components.FocusCard
import com.focusforce.app.ui.components.ScreenTopBar
import com.focusforce.app.ui.components.ShimmerBox
import com.focusforce.app.ui.components.ThemedBackground
import com.focusforce.app.ui.theme.FocusTheme
import com.focusforce.app.ui.util.durationText

/** Edit which apps count as productive, distracting or neutral. A lazy list: handles hundreds of apps without jank. */
@Composable
fun CategoriesScreen(onBack: () -> Unit, viewModel: CategoriesViewModel = hiltViewModel()) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val c = FocusTheme.colors
    ThemedBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            ScreenTopBar(stringResource(R.string.categories_title), onBack)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                val list = apps
                when {
                    list == null -> ShimmerBox(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(FocusTheme.spacing.lg).height(200.dp))
                    list.isEmpty() -> Text(
                        stringResource(R.string.categories_empty),
                        style = FocusTheme.typography.body,
                        color = c.textMuted,
                        modifier = Modifier.padding(FocusTheme.spacing.lg),
                    )
                    else -> LazyColumn(
                        Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(FocusTheme.spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(FocusTheme.spacing.md),
                    ) {
                        item {
                            Text(stringResource(R.string.categories_intro), style = FocusTheme.typography.body, color = c.textMuted)
                        }
                        items(list, key = { it.packageName }) { row ->
                            FocusCard(Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AppIcon(row.packageName)
                                    Column(Modifier.weight(1f).padding(start = FocusTheme.spacing.md)) {
                                        Text(row.label, style = FocusTheme.typography.body, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(durationText(row.foregroundMs), style = FocusTheme.typography.label, color = c.textMuted)
                                    }
                                }
                                androidx.compose.foundation.layout.Spacer(Modifier.height(FocusTheme.spacing.sm))
                                CategoryPicker(row.category) { viewModel.setCategory(row.packageName, it) }
                            }
                        }
                    }
                }
            }
        }
    }
}
