package com.focusforce.app.ui.insights

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.focusforce.app.R
import com.focusforce.app.ui.components.AppIcon
import com.focusforce.app.ui.components.FocusProgressBar
import com.focusforce.app.ui.theme.FocusTheme
import com.focusforce.app.ui.util.durationText

/** One app: icon, name, time, a bar relative to [maxMs], and "category · N opens". */
@Composable
fun AppUsageRow(row: AppRow, maxMs: Long, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val c = FocusTheme.colors
    val time = durationText(row.foregroundMs)
    val category = stringResource(row.category.labelRes)
    val opens = stringResource(R.string.opens_count, row.launches)
    Row(
        modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(row.packageName)
        Column(Modifier.weight(1f).padding(start = FocusTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.label, style = FocusTheme.typography.body, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(time, style = FocusTheme.typography.label, color = c.text, modifier = Modifier.padding(start = FocusTheme.spacing.sm))
            }
            FocusProgressBar(
                progress = if (maxMs <= 0) 0f else row.foregroundMs.toFloat() / maxMs,
                color = categoryColor(row.category),
                barHeight = 6.dp,
            )
            Text("$category · $opens", style = FocusTheme.typography.label, color = c.textMuted)
        }
    }
}
