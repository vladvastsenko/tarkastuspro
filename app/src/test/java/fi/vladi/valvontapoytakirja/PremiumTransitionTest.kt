package fi.vladi.valvontapoytakirja
import org.junit.Assert.*
import org.junit.Test
class PremiumTransitionTest {
    @Test fun developerModeTransitionsAndLogout() {
        val mode = DeveloperTestMode()
        assertFalse(mode.premium(null))
        assertTrue(mode.premium("owner"))
        mode.select("owner", true)
        assertFalse(mode.premium("owner"))
        assertFalse(mode.premium(null))
        mode.select("owner", false)
        assertTrue(mode.premium("owner"))
        mode.select("owner", true)
        mode.reset()
        assertFalse(mode.premium(null))
        assertTrue(mode.premium("owner"))
        assertFalse(permitsDeveloperTest(true,false,false,true,"other@example.com"))
    }

    @Test fun transitionsAndAccountSwitch() {
        val uid = "owner"
        val states = listOf(SubscriptionState.FREE, SubscriptionState.PENDING, SubscriptionState.ACTIVE,
            SubscriptionState.CANCELLED, SubscriptionState.EXPIRED, SubscriptionState.ACTIVE, SubscriptionState.ON_HOLD)
        assertEquals(listOf(false,false,true,true,false,true,false),
            states.map { VerifiedSubscription(uid,it,200).permitsPremium(uid,100) })
        val active = VerifiedSubscription(uid,SubscriptionState.ACTIVE,200)
        assertFalse(active.permitsPremium("another",100))
        assertFalse(active.permitsPremium(uid,200))
    }
}
