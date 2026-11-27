package fi.vladi.valvontapoytakirja

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.android.billingclient.api.Purchase
import java.security.MessageDigest

internal fun premiumAccountHash(uid: String): String =
    MessageDigest.getInstance("SHA-256").digest(uid.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }

internal fun verifyPremiumPurchase(purchase: Purchase, onResult: (VerifiedSubscription?) -> Unit) {
    if (BuildConfig.OFFLINE_PREVIEW || purchase.purchaseState != Purchase.PurchaseState.PURCHASED ||
        PREMIUM_PRODUCT !in purchase.products) { onResult(null); return }
    val auth = FirebaseAuth.getInstance()
    val uid = auth.currentUser?.uid ?: run { onResult(null); return }
    FirebaseFunctions.getInstance("europe-west1").getHttpsCallable("verifyPremium")
        .call(mapOf("purchaseToken" to purchase.purchaseToken))
        .addOnCompleteListener { task ->
            val verified = if (task.isSuccessful && auth.currentUser?.uid == uid) {
                runCatching {
                    val data = task.result.data as Map<*, *>
                    require(data["uid"] == uid)
                    val eligible = data["eligible"] as Boolean
                    val state = SubscriptionState.valueOf(data["state"] as String)
                    val expiry = (data["expiresAt"] as Number).toLong()
                    VerifiedSubscription(uid, state, minOf(expiry, System.currentTimeMillis() + 300_000))
                        .let { if (eligible) it.takeIf { sub -> sub.permitsPremium(uid, System.currentTimeMillis()) } else it.copy(expiresAt = 0L) }
                }.getOrNull()
            } else null
            onResult(verified)
        }
}
