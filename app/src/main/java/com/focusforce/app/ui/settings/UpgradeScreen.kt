package com.focusforce.app.ui.settings

import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusforce.app.R
import com.focusforce.app.domain.pro.FreeLimits
import com.focusforce.app.ui.components.ButtonStyle
import com.focusforce.app.ui.components.ContentColumn
import com.focusforce.app.ui.components.FocusButton
import com.focusforce.app.ui.components.FocusCard
import com.focusforce.app.ui.components.ScreenTopBar
import com.focusforce.app.ui.components.SectionLabel
import com.focusforce.app.ui.components.ThemedBackground
import com.focusforce.app.ui.theme.FocusTheme
import com.focusforce.app.ui.util.findActivity
import kotlinx.coroutines.delay

/** What is free, what Pro adds, one button to buy and one to restore. A one-time purchase, handled by Google Play. */
@Composable
fun UpgradeScreen(onBack: () -> Unit, viewModel: UpgradeViewModel = hiltViewModel()) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val c = FocusTheme.colors
    val context = LocalContext.current

    ThemedBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            ScreenTopBar(stringResource(R.string.pro_title), onBack)
            ContentColumn {
                ui.notice?.let { res ->
                    LaunchedEffect(res) { delay(6_000L); viewModel.clearNotice() }
                    FocusCard(Modifier.fillMaxWidth(), selected = true) { Text(stringResource(res), style = FocusTheme.typography.body, color = c.text) }
                }
                if (ui.fakeStore) {
                    FocusCard(Modifier.fillMaxWidth()) { Text(stringResource(R.string.upgrade_fake_store), style = FocusTheme.typography.label, color = c.secondary) }
                }
                FocusCard(Modifier.fillMaxWidth()) {
                    SectionLabel(stringResource(R.string.upgrade_free_title))
                    Spacer(Modifier.height(FocusTheme.spacing.sm))
                    Text(
                        stringResource(R.string.upgrade_free_body, FreeLimits.GUARDED_APPS, FreeLimits.SCHEDULES, FreeLimits.USAGE_LIMITS),
                        style = FocusTheme.typography.body, color = c.textMuted,
                    )
                }
                FocusCard(Modifier.fillMaxWidth(), selected = true) {
                    SectionLabel(stringResource(R.string.upgrade_pro_title))
                    Spacer(Modifier.height(FocusTheme.spacing.sm))
                    Text(stringResource(R.string.upgrade_pro_body), style = FocusTheme.typography.body, color = c.text)
                    Spacer(Modifier.height(FocusTheme.spacing.sm))
                    Text(stringResource(R.string.upgrade_keep_note), style = FocusTheme.typography.label, color = c.textMuted)
                }
                if (ui.pro) {
                    Text(stringResource(R.string.pro_active), style = FocusTheme.typography.title, color = c.success)
                } else {
                    val price = ui.product?.priceText
                    FocusButton(
                        text = if (price != null) stringResource(R.string.upgrade_buy_price, price) else stringResource(R.string.upgrade_buy),
                        onClick = { context.findActivity()?.let(viewModel::buy) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = ui.product != null,
                    )
                    if (ui.product == null) Text(stringResource(R.string.upgrade_unavailable), style = FocusTheme.typography.label, color = c.textMuted)
                    FocusButton(stringResource(R.string.upgrade_restore), viewModel::restore, Modifier.fillMaxWidth(), style = ButtonStyle.Secondary)
                }
                Text(stringResource(R.string.upgrade_terms), style = FocusTheme.typography.label, color = c.textMuted)
            }
        }
    }
}
