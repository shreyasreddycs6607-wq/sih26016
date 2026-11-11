package com.focusforce.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.focusforce.app.R
import com.focusforce.app.ui.components.ContentColumn
import com.focusforce.app.ui.components.ScreenTopBar
import com.focusforce.app.ui.components.ThemedBackground
import com.focusforce.app.ui.theme.FocusTheme

/** The policy text lives in res/raw/privacy_policy.txt ("# " = title, "## " = heading, blank line = paragraph). */
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val c = FocusTheme.colors
    val blocks = remember {
        context.resources.openRawResource(R.raw.privacy_policy).bufferedReader().use { it.readText() }.trim().split("\n\n")
    }
    ThemedBackground(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            ScreenTopBar(stringResource(R.string.settings_policy), onBack)
            ContentColumn {
                blocks.forEach { block ->
                    when {
                        block.startsWith("## ") -> Text(block.removePrefix("## "), style = FocusTheme.typography.title, color = c.primary, modifier = Modifier.semantics { heading() })
                        block.startsWith("# ") -> Text(block.removePrefix("# "), style = FocusTheme.typography.headline, color = c.text, modifier = Modifier.semantics { heading() })
                        else -> Text(block, style = FocusTheme.typography.body, color = c.text)
                    }
                }
            }
        }
    }
}
