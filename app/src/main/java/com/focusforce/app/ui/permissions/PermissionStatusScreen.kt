package com.focusforce.app.ui.permissions

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusforce.app.R
import com.focusforce.app.permissions.Oem
import com.focusforce.app.permissions.PermissionKind
import com.focusforce.app.permissions.PermissionLauncher
import com.focusforce.app.ui.components.ButtonStyle
import com.focusforce.app.ui.components.ContentColumn
import com.focusforce.app.ui.components.FocusButton
import com.focusforce.app.ui.components.FocusCard
import com.focusforce.app.ui.components.FocusIcon
import com.focusforce.app.ui.components.FocusIconView
import com.focusforce.app.ui.components.ScreenTitle
import com.focusforce.app.ui.components.SectionLabel
import com.focusforce.app.ui.components.ThemedBackground
import com.focusforce.app.ui.theme.FocusTheme

/** Live status of every permission with deep links, plus OEM keep-alive tips. Refreshes on every resume. */
@Composable
fun PermissionStatusScreen(onBack: () -> Unit, viewModel: PermissionsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val context = LocalContext.current
    val c = FocusTheme.colors
    val backLabel = stringResource(R.string.action_back)

    ThemedBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = FocusTheme.spacing.sm, vertical = FocusTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(48.dp).clickable(role = Role.Button, onClick = onBack)
                        .semantics { contentDescription = backLabel },
                    contentAlignment = Alignment.Center,
                ) { FocusIconView(FocusIcon.Back, c.text) }
                Text(stringResource(R.string.permissions_title), style = FocusTheme.typography.title, color = c.text)
            }
            ContentColumn {
                Text(stringResource(R.string.permissions_intro), style = FocusTheme.typography.body, color = c.textMuted)
                state.forEach { (kind, granted) ->
                    PermissionRow(kind, granted) { PermissionLauncher.open(context, kind) }
                }
                Spacer(Modifier.height(FocusTheme.spacing.sm))
                SectionLabel(stringResource(R.string.oem_section_title))
                Text(stringResource(R.string.oem_section_intro), style = FocusTheme.typography.body, color = c.textMuted)
                OemTipsSection()
                FocusButton(
                    text = stringResource(R.string.open_app_settings),
                    onClick = { PermissionLauncher.openAppDetails(context) },
                    modifier = Modifier.fillMaxWidth(),
                    style = ButtonStyle.Secondary,
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(kind: PermissionKind, granted: Boolean, onOpen: () -> Unit) {
    val c = FocusTheme.colors
    val copy = kind.copy
    val title = stringResource(copy.title)
    val status = stringResource(if (granted) R.string.perm_granted else R.string.perm_not_granted)
    FocusCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FocusIconView(copy.icon, if (granted) c.success else c.textMuted, size = 28.dp)
            Column(Modifier.weight(1f).padding(horizontal = FocusTheme.spacing.md)) {
                Text(title, style = FocusTheme.typography.title, color = c.text)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (granted) {
                        FocusIconView(FocusIcon.Check, c.success, size = 16.dp)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(status, style = FocusTheme.typography.label, color = if (granted) c.success else c.danger)
                    if (kind.essential && !granted) {
                        Text("  •  " + stringResource(R.string.perm_required_badge), style = FocusTheme.typography.label, color = c.textMuted)
                    }
                }
            }
        }
        Spacer(Modifier.height(FocusTheme.spacing.md))
        FocusButton(
            text = stringResource(if (granted) R.string.perm_manage else R.string.perm_open_settings),
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth(),
            style = if (granted) ButtonStyle.Secondary else ButtonStyle.Primary,
        )
    }
}

@Composable
private fun OemTipsSection() {
    val detected = remember { Oem.detect() }
    var expanded by rememberSaveable { mutableStateOf(detected?.name) }
    val ordered = remember { Oem.entries.sortedBy { if (it == detected) 0 else 1 } }
    val c = FocusTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(FocusTheme.spacing.md)) {
        ordered.forEach { oem ->
            val open = expanded == oem.name
            FocusCard(Modifier.fillMaxWidth(), onClick = { expanded = if (open) null else oem.name }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(oem.labelRes), style = FocusTheme.typography.title, color = c.text, modifier = Modifier.weight(1f))
                    if (oem == detected) {
                        Text(stringResource(R.string.oem_your_device), style = FocusTheme.typography.label, color = c.primary)
                    }
                }
                if (open) {
                    Spacer(Modifier.height(FocusTheme.spacing.sm))
                    stringArrayResource(oem.tipsRes).forEachIndexed { i, tip ->
                        Text("${i + 1}.  $tip", style = FocusTheme.typography.body, color = c.textMuted, modifier = Modifier.padding(vertical = 3.dp))
                    }
                }
            }
        }
        FocusCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.oem_generic_title), style = FocusTheme.typography.title, color = c.text)
            Spacer(Modifier.height(FocusTheme.spacing.sm))
            Text(stringResource(R.string.oem_generic_tip), style = FocusTheme.typography.body, color = c.textMuted)
        }
    }
}
