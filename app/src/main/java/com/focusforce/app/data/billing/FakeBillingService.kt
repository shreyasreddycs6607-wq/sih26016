package com.focusforce.app.data.billing

import android.app.Activity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * DEBUG BUILDS ONLY (the DI module never selects it for release). "Buying" simply flips a flag in memory, so the paywall and
 * every Pro gate can be tried without Play Console. Nothing is charged and nothing is stored: restarting the app resets it.
 */
@Singleton
class FakeBillingService @Inject constructor() : BillingService {
    override val available = true
    private val entitled = MutableStateFlow<Boolean?>(false)
    override val storeEntitlement: StateFlow<Boolean?> = entitled
    override val product: StateFlow<ProProduct?> = MutableStateFlow(ProProduct("debug_fake_pro", "Debug: free, fake purchase"))

    override suspend fun purchase(activity: Activity): PurchaseLaunch {
        entitled.value = true
        return PurchaseLaunch.LAUNCHED
    }

    override suspend fun restore() = Unit
}
