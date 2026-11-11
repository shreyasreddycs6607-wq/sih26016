package com.focusforce.app.ui.web

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.LaunchedEffect
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
import com.focusforce.app.data.web.RulesSource
import com.focusforce.app.ui.components.AppIcon
import com.focusforce.app.ui.components.ButtonStyle
import com.focusforce.app.ui.components.ContentColumn
import com.focusforce.app.ui.components.FocusButton
import com.focusforce.app.ui.components.FocusCard
import com.focusforce.app.ui.components.FocusSwitch
import com.focusforce.app.ui.components.FocusTextField
import com.focusforce.app.ui.components.RefreshWhileVisible
import com.focusforce.app.ui.components.ScreenTopBar
import com.focusforce.app.ui.components.SectionLabel
import com.focusforce.app.ui.components.ShimmerBox
import com.focusforce.app.ui.components.ThemedBackground
import com.focusforce.app.ui.theme.FocusTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.delay

/** Short-video sections and websites. The privacy statement comes first, on purpose. */
@Composable
fun WebBlockingScreen(onBack: () -> Unit, onOpenPermissions: () -> Unit, viewModel: WebBlockingViewModel = hiltViewModel()) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    RefreshWhileVisible(intervalMs = 15_000L) { viewModel.refresh() }
    val c = FocusTheme.colors
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) viewModel.importFrom(uri) }

    ThemedBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            ScreenTopBar(stringResource(R.string.web_title), onBack)
            ContentColumn {
                notice?.let { n ->
                    LaunchedEffect(n) { delay(6_000L); viewModel.clearNotice() }
                    FocusCard(Modifier.fillMaxWidth(), selected = true) { Text(stringResource(n.res, n.arg), style = FocusTheme.typography.body, color = c.text) }
                }

                FocusCard(Modifier.fillMaxWidth(), selected = true) {
                    SectionLabel(stringResource(R.string.privacy_title))
                    Spacer(Modifier.height(FocusTheme.spacing.sm))
                    Text(stringResource(R.string.privacy_body), style = FocusTheme.typography.body, color = c.text)
                }

                val s = ui
                if (s == null) { ShimmerBox(Modifier.fillMaxWidth().height(200.dp)); return@ContentColumn }

                if (!s.accessibilityOn || !s.blockingOn) {
                    FocusCard(Modifier.fillMaxWidth()) {
                        Text(stringResource(if (!s.accessibilityOn) R.string.web_needs_accessibility else R.string.web_blocking_off), style = FocusTheme.typography.body, color = c.danger)
                        if (!s.accessibilityOn) {
                            Spacer(Modifier.height(FocusTheme.spacing.md))
                            FocusButton(stringResource(R.string.blocks_fix_detection), onOpenPermissions, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary)
                        }
                    }
                }

                // short-video sections
                FocusCard(Modifier.fillMaxWidth()) {
                    SectionLabel(stringResource(R.string.feed_title))
                    Spacer(Modifier.height(FocusTheme.spacing.sm))
                    Text(stringResource(R.string.feed_intro), style = FocusTheme.typography.body, color = c.textMuted)
                    Spacer(Modifier.height(FocusTheme.spacing.sm))
                    if (s.feeds.isEmpty()) {
                        Text(stringResource(R.string.feed_none), style = FocusTheme.typography.body, color = c.textMuted)
                    }
                    s.feeds.forEach { f ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(f.packageName, size = 36.dp)
                            Column(Modifier.weight(1f).padding(start = FocusTheme.spacing.md)) {
                                FocusSwitch(
                                    checked = f.enabled,
                                    onCheckedChange = { viewModel.setFeed(f.packageName, it) },
                                    label = f.label,
                                    description = if (f.verified) null else stringResource(R.string.feed_experimental),
                                )
                            }
                        }
                    }
                }

                // websites
                FocusCard(Modifier.fillMaxWidth()) {
                    FocusSwitch(
                        checked = s.websitesEnabled,
                        onCheckedChange = viewModel::setWebsites,
                        label = stringResource(R.string.site_master),
                        description = if (s.browsers.isEmpty()) stringResource(R.string.site_no_browsers) else stringResource(R.string.site_browsers, s.browsers.joinToString(", ")),
                    )
                    Spacer(Modifier.height(FocusTheme.spacing.sm))
                    SectionLabel(stringResource(R.string.site_categories))
                    s.categories.forEach { cat ->
                        FocusSwitch(
                            checked = cat.enabled,
                            onCheckedChange = { viewModel.setCategory(cat.id, it) },
                            label = categoryName(cat.id),
                            description = stringResource(R.string.site_category_count, cat.domainCount),
                        )
                    }
                    Text(stringResource(R.string.site_lists_note), style = FocusTheme.typography.label, color = c.textMuted)
                }

                ListEditor(
                    title = stringResource(R.string.site_domains),
                    hint = stringResource(R.string.site_domain_hint),
                    items = s.domains,
                    onAdd = viewModel::addDomain,
                    onRemove = viewModel::removeDomain,
                )
                ListEditor(
                    title = stringResource(R.string.site_keywords),
                    hint = stringResource(R.string.site_keyword_hint),
                    items = s.keywords,
                    onAdd = viewModel::addKeyword,
                    onRemove = viewModel::removeKeyword,
                )
                Text(stringResource(R.string.site_limits_note), style = FocusTheme.typography.label, color = c.textMuted)

                // rules
                FocusCard(Modifier.fillMaxWidth()) {
                    SectionLabel(stringResource(R.string.rules_title))
                    Spacer(Modifier.height(FocusTheme.spacing.sm))
                    Text(
                        stringResource(
                            when (s.rulesSource) {
                                RulesSource.BUNDLED -> R.string.rules_source_bundled
                                RulesSource.IMPORTED -> R.string.rules_source_imported
                                RulesSource.UPDATED -> R.string.rules_source_updated
                            },
                            s.rulesVersion,
                        ),
                        style = FocusTheme.typography.body,
                        color = c.text,
                    )
                    Text(stringResource(R.string.rules_explain), style = FocusTheme.typography.label, color = c.textMuted)
                    Spacer(Modifier.height(FocusTheme.spacing.md))
                    FocusButton(stringResource(R.string.rules_import), { picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary)
                    Spacer(Modifier.height(FocusTheme.spacing.md))
                    if (s.remoteConfigured) {
                        FocusSwitch(s.autoUpdate, viewModel::setAutoUpdate, stringResource(R.string.rules_auto), description = stringResource(R.string.rules_auto_desc))
                        if (s.lastCheckedMs > 0) {
                            val whenText = Instant.ofEpochMilli(s.lastCheckedMs).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT))
                            Text(stringResource(R.string.rules_checked, whenText), style = FocusTheme.typography.label, color = c.textMuted)
                        }
                        Spacer(Modifier.height(FocusTheme.spacing.sm))
                        FocusButton(stringResource(R.string.rules_check_now), viewModel::checkForUpdates, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary)
                    } else {
                        Text(stringResource(R.string.rules_remote_off), style = FocusTheme.typography.label, color = c.textMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun categoryName(id: String): String = when (id) {
    "adult" -> stringResource(R.string.cat_adult)
    "social" -> stringResource(R.string.cat_social)
    "gambling" -> stringResource(R.string.cat_gambling)
    else -> id.replaceFirstChar { it.uppercase() }
}

/** A small "add item" field plus the current items with a Remove button each. */
@Composable
private fun ListEditor(title: String, hint: String, items: List<String>, onAdd: (String) -> Unit, onRemove: (String) -> Unit) {
    val c = FocusTheme.colors
    var draft by rememberSaveable { mutableStateOf("") }
    FocusCard(Modifier.fillMaxWidth()) {
        SectionLabel(title)
        Spacer(Modifier.height(FocusTheme.spacing.sm))
        FocusTextField(draft, { draft = it }, hint)
        Spacer(Modifier.height(FocusTheme.spacing.sm))
        FocusButton(stringResource(R.string.action_add), { onAdd(draft); draft = "" }, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary, enabled = draft.isNotBlank())
        items.forEach { item ->
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FocusTheme.spacing.sm)) {
                Text(item, style = FocusTheme.typography.body, color = c.text, modifier = Modifier.weight(1f))
                FocusButton(stringResource(R.string.blocks_remove), { onRemove(item) }, style = ButtonStyle.Ghost)
            }
        }
    }
}
