package com.focusforce.app.data.billing

import android.app.Activity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class ProProduct(val productId: String, val priceText: String)

enum class PurchaseLaunch { LAUNCHED, UNAVAILABLE, ERROR }

/**
 * Everything the app needs from a store, behind an interface so the rest of the code never sees Google Play Billing.
 * [PlayBillingService] is the real one; [FakeBillingService] is used by debug builds only.
 */
interface BillingService {
    val available: Boolean

    /** true/false = what the store says right now; null = the store could not be reached (callers fall back to the cached value). */
    val storeEntitlement: Flow<Boolean?>

    /** The Pro product with its localised price, or null while loading / when the store has no such product. */
    val product: StateFlow<ProProduct?>

    /** Opens the store's purchase sheet. The outcome arrives through [storeEntitlement]. */
    suspend fun purchase(activity: Activity): PurchaseLaunch

    /** Re-reads purchases from the store (e.g. after reinstalling or on a new phone). */
    suspend fun restore()
}
