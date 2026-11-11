package com.focusforce.app.data.billing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.focusforce.app.domain.pro.EntitlementResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.proStore: DataStore<Preferences> by preferencesDataStore("focusforce_pro")
private val CACHED = booleanPreferencesKey("cached_pro")
private val VERIFIED_AT = longPreferencesKey("verified_at_ms")

/**
 * The single answer to "is this user Pro?". Follows the store while it is reachable, remembers the last verified answer, and
 * honours that remembered "Pro" for 30 days when offline ([EntitlementResolver]). Starts as free until the cached value is read
 * (a few milliseconds), which can only ever err on the side of showing a Pro gate briefly, never of unlocking by mistake.
 */
@Singleton
class EntitlementRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    val billing: BillingService,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _pro = MutableStateFlow(false)
    val pro: StateFlow<Boolean> = _pro.asStateFlow()

    fun isPro(): Boolean = _pro.value

    init {
        scope.launch {
            val prefs = context.proStore.data.first()
            var cached = prefs[CACHED] ?: false
            var verifiedAt = prefs[VERIFIED_AT] ?: 0L
            _pro.value = EntitlementResolver.resolve(null, cached, verifiedAt, System.currentTimeMillis())
            billing.storeEntitlement.collect { storeSays ->
                val now = System.currentTimeMillis()
                if (storeSays != null) {
                    cached = storeSays
                    verifiedAt = now
                    context.proStore.edit { it[CACHED] = storeSays; it[VERIFIED_AT] = now }
                }
                _pro.value = EntitlementResolver.resolve(storeSays, cached, verifiedAt, now)
            }
        }
    }
}
