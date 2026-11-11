package com.focusforce.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusforce.app.BuildConfig
import com.focusforce.app.R
import com.focusforce.app.domain.model.HeroTheme
import com.focusforce.app.ui.art.HeroMascot
import com.focusforce.app.ui.components.ButtonStyle
import com.focusforce.app.ui.components.ContentColumn
import com.focusforce.app.ui.components.FocusButton
import com.focusforce.app.ui.components.FocusCard
import com.focusforce.app.ui.components.ScreenTopBar
import com.focusforce.app.ui.components.SectionLabel
import com.focusforce.app.ui.components.ThemedBackground
import com.focusforce.app.ui.theme.FocusTheme
import com.focusforce.app.ui.theme.nameRes
import com.focusforce.app.ui.util.findActivity
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenUpgrade: () -> Unit,
    onOpenPolicy: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val c = FocusTheme.colors
    val context = LocalContext.current
    var pending by rememberSaveable { mutableStateOf<ExportKind?>(null) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        val kind = pending
        pending = null
        if (uri != null && kind != null) viewModel.export(kind, uri)
    }
    // After a reset the app starts over: recreating the activity makes the navigation host pick onboarding again.
    LaunchedEffect(ui.resetDone) { if (ui.resetDone) context.findActivity()?.recreate() }

    ThemedBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            ScreenTopBar(stringResource(R.string.settings_title), onBack)
            ContentColumn {
                ui.notice?.let { res ->
                    LaunchedEffect(res) { delay(6_000L); viewModel.clearNotice() }
                    FocusCard(Modifier.fillMaxWidth(), selected = true) { Text(stringResource(res), style = FocusTheme.typography.body, color = c.text) }
                }

                FocusCard(Modifier.fillMaxWidth(), selected = !ui.pro) {
                    SectionLabel(stringResource(R.string.pro_title))
                    Spacer(Modifier.height(FocusTheme.spacing.sm))
                    Text(stringResource(if (ui.pro) R.string.pro_active else R.string.pro_free_summary), style = FocusTheme.typography.body, color = c.text)
                    Spacer(Modifier.height(FocusTheme.spacing.md))
                    FocusButton(stringResource(if (ui.pro) R.string.pro_manage else R.string.pro_see), onOpenUpgrade, Modifier.fillMaxWidth(), style = if (ui.pro) ButtonStyle.Secondary else ButtonStyle.Primary)
                }

                SectionLabel(stringResource(R.string.settings_appearance))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FocusTheme.spacing.md)) {
                    HeroTheme.entries.forEach { hero ->
                        FocusCard(Modifier.weight(1f), onClick = { viewModel.setHero(hero) }, selected = hero == ui.hero, contentPadding = FocusTheme.spacing.sm) {
                            HeroMascot(hero, Modifier.height(72.dp).align(Alignment.CenterHorizontally))
                            Text(stringResource(hero.nameRes), style = FocusTheme.typography.label, color = c.text, modifier = Modifier.align(Alignment.CenterHorizontally))
                        }
                    }
                }

                FocusCard(Modifier.fillMaxWidth()) {
                    SectionLabel(stringResource(R.string.settings_language))
                    Spacer(Modifier.height(FocusTheme.spacing.sm))
                    Text(stringResource(R.string.settings_language_body), style = FocusTheme.typography.body, color = c.textMuted)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Spacer(Modifier.height(FocusTheme.spacing.md))
                        FocusButton(
                            stringResource(R.string.settings_language_open),
                            { context.startActivity(Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null))) },
                            Modifier.fillMaxWidth(), style = ButtonStyle.Secondary,
                        )
                    }
                }

                FocusCard(Modifier.fillMaxWidth()) {
                    SectionLabel(stringResource(R.string.settings_export))
                    Spacer(Modifier.height(FocusTheme.spacing.sm))
                    Text(stringResource(R.string.settings_export_body), style = FocusTheme.typography.body, color = c.textMuted)
                    Spacer(Modifier.height(FocusTheme.spacing.md))
                    FocusButton(stringResource(R.string.settings_export_usage), {
                        if (viewModel.canExport()) { pending = ExportKind.USAGE; picker.launch("focusforce-usage.csv") }
                    }, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary)
                    Spacer(Modifier.height(FocusTheme.spacing.sm))
                    FocusButton(stringResource(R.string.settings_export_sessions), {
                        if (viewModel.canExport()) { pending = ExportKind.SESSIONS; picker.launch("focusforce-focus-sessions.csv") }
                    }, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary)
                }

                FocusCard(Modifier.fillMaxWidth()) {
                    SectionLabel(stringResource(R.string.settings_reset))
                    Spacer(Modifier.height(FocusTheme.spacing.sm))
                    Text(stringResource(R.string.settings_reset_body), style = FocusTheme.typography.body, color = c.textMuted)
                    Spacer(Modifier.height(FocusTheme.spacing.md))
                    if (!confirmReset) {
                        FocusButton(stringResource(R.string.settings_reset_button), { confirmReset = true }, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary)
                    } else {
                        Text(stringResource(R.string.settings_reset_confirm), style = FocusTheme.typography.body, color = c.danger)
                        Spacer(Modifier.height(FocusTheme.spacing.sm))
                        FocusButton(stringResource(R.string.settings_reset_yes), { viewModel.resetAll(); confirmReset = false }, Modifier.fillMaxWidth())
                        Spacer(Modifier.height(FocusTheme.spacing.sm))
                        FocusButton(stringResource(R.string.strict_cancel), { confirmReset = false }, Modifier.fillMaxWidth(), style = ButtonStyle.Ghost)
                    }
                }

                FocusButton(stringResource(R.string.settings_policy), onOpenPolicy, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary)
                Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), style = FocusTheme.typography.label, color = c.textMuted)
            }
        }
    }
}
