package fi.vladi.valvontapoytakirja

import android.content.Context
import android.app.Activity
import com.android.billingclient.api.*
import com.google.firebase.functions.FirebaseFunctions
import java.security.SecureRandom

/** Does not create a Firebase user or grant Premium. */
internal class PremiumRegistrationPurchase(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("premium_registration_private", Context.MODE_PRIVATE)
    val session: String = synchronized(lock) {
        prefs.getString("session", null)?.takeIf { it.matches(Regex("[a-f0-9]{64}")) }
            ?: ByteArray(32).also { SecureRandom().nextBytes(it) }
                .joinToString("") { "%02x".format(it.toInt() and 255) }
                .also { check(prefs.edit().putString("session", it).commit()) }
    }
    val accountHash: String get() = premiumAccountHash(session)
    fun launch(billing: PremiumBilling, activity: Activity, details: ProductDetails,
        offer: ProductDetails.SubscriptionOfferDetails): BillingResult {
        check(BuildConfig.PREMIUM_CHECKOUT_ENABLED && !BuildConfig.OFFLINE_PREVIEW)
        return billing.launchPurchase(activity, details, offer, accountHash)
    }
    fun prepare(purchase: Purchase, onResult: (String?) -> Unit) {
        if (!BuildConfig.PREMIUM_CHECKOUT_ENABLED || BuildConfig.OFFLINE_PREVIEW ||
            purchase.purchaseState != Purchase.PurchaseState.PURCHASED || purchase.isSuspended ||
            PREMIUM_PRODUCT !in purchase.products || purchase.accountIdentifiers?.obfuscatedAccountId != accountHash) {
            onResult(null)
            return
        }
        val key = "permit_" + premiumAccountHash(purchase.purchaseToken)
        // Retain valid permits across retries; the server revalidates the purchase even after expiry.
        prefs.getString(key, null)?.takeIf { it.matches(Regex("[a-f0-9]{64}")) }?.let { onResult(it); return }
        FirebaseFunctions.getInstance("europe-west1").getHttpsCallable("preparePremiumRegistration")
            .call(mapOf("purchaseToken" to purchase.purchaseToken, "session" to session))
            .addOnCompleteListener { task ->
                val permit = if (task.isSuccessful) (task.result.data as? Map<*, *>)?.get("permit") as? String else null
                val valid = permit?.takeIf { it.matches(Regex("[a-f0-9]{64}")) }
                onResult(valid?.takeIf { prefs.edit().putString(key, it).commit() })
            }
    }
    fun complete(purchase: Purchase, email: String, password: String, onResult: (Boolean) -> Unit) {
        if (email.isBlank() || password.length !in 8..128) { onResult(false); return }
        prepare(purchase) { permit ->
            if (permit == null) { onResult(false); return@prepare }
            FirebaseFunctions.getInstance("europe-west1").getHttpsCallable("completePremiumEmailRegistration")
                .call(mapOf("purchaseToken" to purchase.purchaseToken, "session" to session,
                    "permit" to permit, "email" to email, "password" to password))
                .addOnCompleteListener { task ->
                    onResult(task.isSuccessful && (task.result.data as? Map<*, *>)?.get("created") == true)
                }
        }
    }
    fun completeGoogle(purchase: Purchase, idToken: String, onResult: (String?) -> Unit) {
        prepare(purchase) { permit ->
            if (permit == null) { onResult(null); return@prepare }
            FirebaseFunctions.getInstance("europe-west1").getHttpsCallable("completePremiumGoogleRegistration")
                .call(mapOf("purchaseToken" to purchase.purchaseToken, "session" to session,
                    "permit" to permit, "idToken" to idToken))
                .addOnCompleteListener { task ->
                    onResult(if (task.isSuccessful) (task.result.data as? Map<*, *>)?.get("customToken") as? String else null)
                }
        }
    }
    private companion object { val lock = Any() }
}
