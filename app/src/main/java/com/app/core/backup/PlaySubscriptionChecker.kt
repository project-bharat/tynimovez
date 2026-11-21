package com.app.core.backup

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
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

interface SubscriptionChecker {
    val isProUser: StateFlow<Boolean>
    suspend fun refresh(): Boolean
}

internal class BillingUnavailable : Exception("Google Play could not verify your subscription.")

class PlaySubscriptionChecker(context: Context, private val productId: String) : SubscriptionChecker {
    private val pro = MutableStateFlow(false)
    override val isProUser = pro.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()

    private val client = BillingClient.newBuilder(context.applicationContext)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .setListener { result, _ ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                scope.launch { backupResult { refresh() } }
            }
        }.build()

    override suspend fun refresh(): Boolean = withContext(Dispatchers.Main.immediate) {
        mutex.withLock {
            val result = withTimeoutOrNull(15_000) {
                connect()
                val params = QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS).build()
                val purchases = suspendCancellableCoroutine<List<Purchase>?> { continuation ->
                    client.queryPurchasesAsync(params) { status, values ->
                        if (continuation.isActive) continuation.resume(
                            values.takeIf { status.responseCode == BillingClient.BillingResponseCode.OK })
                    }
                } ?: throw BillingUnavailable()
                val eligible = purchases.filter {
                    productId in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                var verified = false
                for (purchase in eligible) {
                    if (purchase.isAcknowledged || acknowledge(purchase.purchaseToken)) verified = true
                }
                if (eligible.isNotEmpty() && !verified) throw BillingUnavailable()
                verified
            } ?: throw BillingUnavailable()
            pro.value = result
            result
        }
    }

    data class Offer internal constructor(
        internal val details: ProductDetails,
        internal val subscription: ProductDetails.SubscriptionOfferDetails,
        val price: String
    )

    suspend fun offer(): Offer = withContext(Dispatchers.Main.immediate) {
        mutex.withLock {
            withTimeoutOrNull(15_000) {
                connect()
                val product = QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(productId).setProductType(BillingClient.ProductType.SUBS).build()
                val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()
                val details = suspendCancellableCoroutine<ProductDetails?> { continuation ->
                    client.queryProductDetailsAsync(params) { status, result ->
                        if (continuation.isActive) continuation.resume(
                            result.productDetailsList.firstOrNull()
                                .takeIf { status.responseCode == BillingClient.BillingResponseCode.OK })
                    }
                } ?: throw BillingUnavailable()
                val plan = details.subscriptionOfferDetails?.firstOrNull { it.offerId == null }
                    ?: throw BillingUnavailable()
                val price = plan.pricingPhases.pricingPhaseList.lastOrNull()?.formattedPrice
                    ?: throw BillingUnavailable()
                Offer(details, plan, price)
            } ?: throw BillingUnavailable()
        }
    }

    fun purchase(activity: Activity, offer: Offer) {
        val product = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(offer.details).setOfferToken(offer.subscription.offerToken).build()
        val result = client.launchBillingFlow(activity,
            BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(product)).build())
        if (result.responseCode != BillingClient.BillingResponseCode.OK) throw BillingUnavailable()
    }

    private suspend fun connect() {
        if (client.isReady) return
        val connected = suspendCancellableCoroutine<Boolean> { continuation ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (continuation.isActive) continuation.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                }

                override fun onBillingServiceDisconnected() {
                    if (continuation.isActive) continuation.resume(false)
                }
            })
        }
        if (!connected) throw BillingUnavailable()
    }

    private suspend fun acknowledge(token: String): Boolean = suspendCancellableCoroutine { continuation ->
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build()
        client.acknowledgePurchase(params) { result ->
            if (continuation.isActive) continuation.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
        }
    }
}
