package com.focusforce.app.data.apps

import android.content.Context
import android.content.pm.PackageManager
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.focusforce.app.domain.usage.AppCategory
import com.focusforce.app.domain.usage.CategoryDefaults
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class AppMeta(val label: String, val androidCategory: Int, val installed: Boolean)

/**
 * Resolves app names, icons and default categories from PackageManager, with caches.
 * Everything here is local; names and icons never leave the device.
 */
@Singleton
class AppInfoProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val pm: PackageManager = context.packageManager
    private val meta = ConcurrentHashMap<String, AppMeta>()
    private val icons = LruCache<String, ImageBitmap>(200)

    fun meta(packageName: String): AppMeta = meta.getOrPut(packageName) {
        try {
            val info = pm.getApplicationInfo(packageName, 0)
            AppMeta(pm.getApplicationLabel(info).toString(), info.category, installed = true)
        } catch (_: PackageManager.NameNotFoundException) {
            AppMeta(packageName, -1, installed = false)
        }
    }

    fun label(packageName: String): String = meta(packageName).label

    fun defaultCategory(packageName: String): AppCategory =
        CategoryDefaults.classify(packageName, meta(packageName).androidCategory)

    fun resolveCategory(packageName: String, overrides: Map<String, AppCategory>): AppCategory =
        overrides[packageName] ?: defaultCategory(packageName)

    /** Call off the main thread: decoding an icon can take a few milliseconds. */
    fun icon(packageName: String): ImageBitmap? {
        icons.get(packageName)?.let { return it }
        if (!meta(packageName).installed) return null
        return try {
            pm.getApplicationIcon(packageName).toBitmap(ICON_PX, ICON_PX).asImageBitmap().also { icons.put(packageName, it) }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    private companion object {
        const val ICON_PX = 96
    }
}
