package com.focusforce.app.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Google Play Billing, one-time product [PRO_PRODUCT_ID] (create it in Play Console as a managed "In-app product").
 * Entitlement = a PURCHASED, acknowledged purchase of that product. Pending purchases are not entitlements until they complete.
 * Without Play services or the product the screen shows "unavailable" and the app simply stays on the free tier.
 */
@Singleton
class PlayBillingService @Inject constructor(
    @ApplicationContext private val context: Context,
) : BillingService {

    override val available = true

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val entitled = MutableStateFlow<Boolean?>(null)
    private val productState = MutableStateFlow<ProProduct?>(null)
    override val storeEntitlement: StateFlow<Boolean?> = entitled
    override val product: StateFlow<ProProduct?> = productState

    @Volatile private var details: ProductDetails? = null

    private val listener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) scope.launch { apply(purchases) }
    }

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(listener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    init {
        scope.launch {
            if (connect()) {
                loadProduct()
                refreshPurchases()
            }
        }
    }

    private suspend fun connect(): Boolean {
        if (client.isReady) return true
        return suspendCancellableCoroutine { cont ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (cont.isActive) cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                }
                override fun onBillingServiceDisconnected() = Unit // reconnected lazily by the next call
            })
        }
    }

    private suspend fun loadProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(QueryProductDetailsParams.Product.newBuilder().setProductId(PRO_PRODUCT_ID).setProductType(BillingClient.ProductType.INAPP).build()))
            .build()
        val result = client.queryProductDetails(params)
        val found = result.productDetailsList?.firstOrNull()
        details = found
        productState.value = found?.oneTimePurchaseOfferDetails?.let { ProProduct(found.productId, it.formattedPrice) }
    }

    private suspend fun refreshPurchases() {
        val result = client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build())
        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) apply(result.purchasesList)
        // on a failure we keep the previous value: null (unknown) lets the repository use its cached, time-limited value
    }

    private suspend fun apply(purchases: List<Purchase>) {
        val pro = purchases.filter { PRO_PRODUCT_ID in it.products }
        for (p in pro.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged }) {
            // Unacknowledged purchases are refunded by Google after 3 days, so acknowledge as soon as it is granted.
            client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build())
        }
        entitled.value = pro.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
    }

    override suspend fun purchase(activity: Activity): PurchaseLaunch {
        if (!connect()) return PurchaseLaunch.UNAVAILABLE
        if (details == null) loadProduct()
        val d = details ?: return PurchaseLaunch.UNAVAILABLE
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build()))
            .build()
        val result = client.launchBillingFlow(activity, params)
        return if (result.responseCode == BillingClient.BillingResponseCode.OK) PurchaseLaunch.LAUNCHED else PurchaseLaunch.ERROR
    }

    override suspend fun restore() {
        if (connect()) refreshPurchases()
    }

    companion object {
        /** Must match the in-app product id in Play Console. */
        const val PRO_PRODUCT_ID = "focusforce_pro"
    }
}
