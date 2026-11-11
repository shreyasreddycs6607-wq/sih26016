package com.focusforce.app.ui.insights

import com.focusforce.app.data.apps.AppInfoProvider
import com.focusforce.app.domain.usage.AppCategory
import com.focusforce.app.domain.usage.AppUsage

/** An app as shown in lists: label and effective category already resolved. */
data class AppRow(
    val packageName: String,
    val label: String,
    val foregroundMs: Long,
    val launches: Int,
    val category: AppCategory,
    val overridden: Boolean,
)

fun List<AppUsage>.toRows(info: AppInfoProvider, overrides: Map<String, AppCategory>): List<AppRow> = map {
    AppRow(
        packageName = it.packageName,
        label = info.label(it.packageName),
        foregroundMs = it.foregroundMs,
        launches = it.launches,
        category = info.resolveCategory(it.packageName, overrides),
        overridden = it.packageName in overrides,
    )
}
