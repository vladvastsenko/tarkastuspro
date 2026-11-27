package fi.vladi.valvontapoytakirja

internal enum class SubscriptionState { FREE, PENDING, ACTIVE, CANCELLED, GRACE_PERIOD, EXPIRED, ON_HOLD, PAUSED, UNKNOWN }
internal data class VerifiedSubscription(val uid: String, val state: SubscriptionState, val expiresAt: Long) {
    fun permitsPremium(currentUid: String?, now: Long): Boolean =
        !currentUid.isNullOrBlank() && uid == currentUid && expiresAt > now &&
            state in setOf(SubscriptionState.ACTIVE, SubscriptionState.CANCELLED, SubscriptionState.GRACE_PERIOD)
}
internal const val PREMIUM_PRODUCT = "tarkastus_cloud_premium"
internal const val PREMIUM_BASE_PLAN = "monthly"
