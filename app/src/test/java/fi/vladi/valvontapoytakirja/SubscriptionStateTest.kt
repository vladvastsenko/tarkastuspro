package fi.vladi.valvontapoytakirja

import org.junit.Assert.*
import org.junit.Test

class SubscriptionStateTest {
    @Test fun onlyVerifiedEligibleStatesGrantAccess() {
        for (state in SubscriptionState.values()) {
            assertEquals(state in setOf(SubscriptionState.ACTIVE, SubscriptionState.CANCELLED, SubscriptionState.GRACE_PERIOD),
                VerifiedSubscription("a", state, 200).permitsPremium("a", 100))
        }
    }
    @Test fun expiredOrDifferentAccountDenied() {
        val subscription = VerifiedSubscription("a", SubscriptionState.ACTIVE, 200)
        assertFalse(subscription.permitsPremium("a", 200))
        assertFalse(subscription.permitsPremium("b", 100))
        assertFalse(subscription.permitsPremium(null, 100))
    }
}
