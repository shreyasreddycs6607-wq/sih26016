package com.focusforce.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.focusforce.app.data.apps.AppInfoProvider
import com.focusforce.app.ui.theme.FocusTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val LocalAppInfoProvider = staticCompositionLocalOf<AppInfoProvider> { error("AppInfoProvider not provided") }

/** An installed app's launcher icon, loaded off the main thread. Decorative: the label sits next to it. */
@Composable
fun AppIcon(packageName: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val provider = LocalAppInfoProvider.current
    val icon by produceState<ImageBitmap?>(initialValue = null, packageName) {
        value = withContext(Dispatchers.Default) { provider.icon(packageName) }
    }
    val c = FocusTheme.colors
    Box(
        modifier.size(size).clip(FocusTheme.shapes.small).background(c.surfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = icon
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(size))
        } else {
            Text(
                provider.label(packageName).take(1).uppercase(),
                style = FocusTheme.typography.title,
                color = c.textMuted,
            )
        }
    }
}
