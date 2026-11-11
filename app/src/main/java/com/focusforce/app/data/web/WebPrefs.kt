package com.focusforce.app.data.web

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.focusforce.app.domain.rules.WebSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.webStore: DataStore<Preferences> by preferencesDataStore("focusforce_web")

private object W {
    val FEEDS = stringSetPreferencesKey("feed_packages")
    val SITES_ON = booleanPreferencesKey("websites_enabled")
    val CATEGORIES = stringSetPreferencesKey("categories")
    val DOMAINS = stringSetPreferencesKey("custom_domains")
    val KEYWORDS = stringSetPreferencesKey("keywords")
    val AUTO_UPDATE = booleanPreferencesKey("auto_update_rules")
    val CHECKED_AT = longPreferencesKey("rules_checked_at")
}

/**
 * The user's short-video and website choices. Only choices live here. What a browser address bar or a
 * screen showed is never written anywhere: it is checked in memory and dropped.
 */
@Singleton
class WebPrefs @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store get() = context.webStore

    val settings: Flow<WebSettings> = store.data.map { p ->
        WebSettings(
            feedPackages = p[W.FEEDS] ?: emptySet(),
            websitesEnabled = p[W.SITES_ON] ?: false,
            categories = p[W.CATEGORIES] ?: emptySet(),
            customDomains = p[W.DOMAINS] ?: emptySet(),
            keywords = p[W.KEYWORDS] ?: emptySet(),
            autoUpdateRules = p[W.AUTO_UPDATE] ?: true,
        )
    }.distinctUntilChanged()

    val rulesCheckedAtMs: Flow<Long> = store.data.map { it[W.CHECKED_AT] ?: 0L }.distinctUntilChanged()

    suspend fun current(): WebSettings = settings.first()

    suspend fun update(transform: (WebSettings) -> WebSettings) {
        store.edit { p ->
            val n = transform(
                WebSettings(
                    p[W.FEEDS] ?: emptySet(), p[W.SITES_ON] ?: false, p[W.CATEGORIES] ?: emptySet(),
                    p[W.DOMAINS] ?: emptySet(), p[W.KEYWORDS] ?: emptySet(), p[W.AUTO_UPDATE] ?: true,
                ),
            )
            p[W.FEEDS] = n.feedPackages
            p[W.SITES_ON] = n.websitesEnabled
            p[W.CATEGORIES] = n.categories
            p[W.DOMAINS] = n.customDomains
            p[W.KEYWORDS] = n.keywords
            p[W.AUTO_UPDATE] = n.autoUpdateRules
        }
    }

    suspend fun reset() { store.edit { it.clear() } }

    suspend fun setRulesCheckedAt(ms: Long) { store.edit { it[W.CHECKED_AT] = ms } }
}
