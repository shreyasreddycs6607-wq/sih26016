package com.focusforce.app.ui.insights

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.focusforce.app.R
import com.focusforce.app.domain.usage.AppCategory
import com.focusforce.app.ui.theme.FocusTheme

val AppCategory.labelRes: Int @StringRes get() = when (this) {
    AppCategory.PRODUCTIVE -> R.string.category_productive
    AppCategory.DISTRACTING -> R.string.category_distracting
    AppCategory.NEUTRAL -> R.string.category_neutral
}

/** Bar/legend color for a category. Always paired with the category name so color is never the only cue. */
@Composable
fun categoryColor(category: AppCategory): Color = when (category) {
    AppCategory.PRODUCTIVE -> FocusTheme.colors.success
    AppCategory.DISTRACTING -> FocusTheme.colors.danger
    AppCategory.NEUTRAL -> FocusTheme.colors.secondary
}
