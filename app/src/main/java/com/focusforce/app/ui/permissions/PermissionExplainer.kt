package com.focusforce.app.ui.permissions

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.focusforce.app.R
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
import com.focusforce.app.ui.theme.FocusTheme

/**
 * One permission, explained BEFORE the system page opens. For Accessibility this is the prominent
 * disclosure required by Google Play: what is accessed, why, and what is never done, with an
 * explicit "I agree" action and an equally visible "No thanks".
 */
@Composable
fun PermissionExplainer(
    kind: PermissionKind,
    granted: Boolean,
    stepLabel: String,
    onRefresh: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val copy = kind.copy
    val c = FocusTheme.colors
    var notificationsDenied by remember(kind) { mutableStateOf(false) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        onRefresh()
        if (!ok) notificationsDenied = true
    }

    ContentColumn(modifier) {
        Text(stepLabel, style = FocusTheme.typography.label, color = c.textMuted)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FocusTheme.spacing.md)) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(c.surfaceHigh),
                contentAlignment = Alignment.Center,
            ) { FocusIconView(copy.icon, c.primary, size = 28.dp) }
            ScreenTitle(stringResource(copy.title))
        }

        if (kind == PermissionKind.ACCESSIBILITY) {
            FocusCard(Modifier.fillMaxWidth(), selected = true) {
                SectionLabel(stringResource(R.string.perm_accessibility_disclosure_label))
                Spacer(Modifier.height(FocusTheme.spacing.sm))
                Text(stringResource(R.string.perm_accessibility_disclosure), style = FocusTheme.typography.body, color = c.text)
            }
        }
        FocusCard(Modifier.fillMaxWidth()) {
            SectionLabel(stringResource(R.string.perm_why_label))
            Spacer(Modifier.height(FocusTheme.spacing.sm))
            Text(stringResource(copy.why), style = FocusTheme.typography.body, color = c.text)
        }
        FocusCard(Modifier.fillMaxWidth()) {
            SectionLabel(stringResource(R.string.perm_never_label))
            Spacer(Modifier.height(FocusTheme.spacing.sm))
            Text(stringResource(copy.never), style = FocusTheme.typography.body, color = c.text)
        }

        if (granted) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FocusTheme.spacing.sm)) {
                FocusIconView(FocusIcon.Check, c.success, size = 24.dp)
                Text(stringResource(R.string.perm_granted), style = FocusTheme.typography.title, color = c.success)
            }
            FocusButton(stringResource(R.string.action_continue), onNext, Modifier.fillMaxWidth())
        } else {
            val actionLabel = if (kind == PermissionKind.NOTIFICATIONS && notificationsDenied) {
                stringResource(R.string.perm_notifications_open_settings)
            } else {
                stringResource(copy.action)
            }
            FocusButton(
                text = actionLabel,
                onClick = {
                    if (kind == PermissionKind.NOTIFICATIONS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsDenied) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        PermissionLauncher.open(context, kind)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            FocusButton(
                text = stringResource(if (kind.essential) R.string.perm_skip_essential else R.string.perm_skip),
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth(),
                style = ButtonStyle.Ghost,
            )
        }
    }
}
