package fi.vladi.valvontapoytakirja

import android.content.Context
import com.android.billingclient.api.*

/** Queries Play only. Purchase callbacks never grant an entitlement locally. */
internal class PremiumBilling(context: Context, onPurchases: (BillingResult, List<Purchase>) -> Unit) {
    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener { result, purchases -> onPurchases(result, purchases.orEmpty()) }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    fun connect(onResult: (BillingResult) -> Unit) {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) = onResult(result)
            override fun onBillingServiceDisconnected() = Unit
        })
    }

    fun queryPurchases(onResult: (BillingResult, List<Purchase>) -> Unit) {
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS).includeSuspendedSubscriptions(true).build()) { result, purchases ->
            onResult(result, purchases.filter { PREMIUM_PRODUCT in it.products })
        }
    }

    fun queryOffer(onResult: (BillingResult, ProductDetails?, ProductDetails.SubscriptionOfferDetails?) -> Unit) {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PREMIUM_PRODUCT).setProductType(BillingClient.ProductType.SUBS).build()
        client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()) { result, response ->
            val details = response.productDetailsList.firstOrNull { it.productId == PREMIUM_PRODUCT }
            val offer = details?.subscriptionOfferDetails?.firstOrNull {
                it.basePlanId == PREMIUM_BASE_PLAN && it.offerId == null
            }
            onResult(result, details, offer)
        }
    }

    // Caller must have a verified backend configured before enabling checkout.
    fun launchPurchase(activity: android.app.Activity, details: ProductDetails,
        offer: ProductDetails.SubscriptionOfferDetails, accountHash: String): BillingResult {
        require(details.productId == PREMIUM_PRODUCT && offer.basePlanId == PREMIUM_BASE_PLAN)
        require(accountHash.matches(Regex("[a-f0-9]{64}")))
        return client.launchBillingFlow(activity, BillingFlowParams.newBuilder()
            .setObfuscatedAccountId(accountHash)
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details).setOfferToken(offer.offerToken).build())).build())
    }

    fun close() = client.endConnection()
}
