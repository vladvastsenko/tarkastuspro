package fi.vladi.valvontapoytakirja
import org.junit.Assert.*
import org.junit.Test
class PremiumAccessTest {
    @Test fun leaseRequiresCorrectAccountAndBothClocks() {
        val v = VerifiedSubscription("a", SubscriptionState.ACTIVE, 500)
        assertTrue(permitsCloudLease(false,v,"a",100,100,200))
        assertFalse(permitsCloudLease(true,v,"a",100,100,200))
        assertFalse(permitsCloudLease(false,v,"b",100,100,200))
        assertFalse(permitsCloudLease(false,v,null,100,100,200))
        assertFalse(permitsCloudLease(false,v,"a",100,200,200))
        assertFalse(permitsCloudLease(false,v,"a",500,100,200))
        assertFalse(permitsCloudLease(false,null,"a",100,100,200))
        for (state in SubscriptionState.values()) {
            assertEquals(state in setOf(SubscriptionState.ACTIVE,SubscriptionState.CANCELLED,SubscriptionState.GRACE_PERIOD),
                permitsCloudLease(false,v.copy(state=state),"a",100,100,200))
        }
    }
    @Test fun everyLanguageHasEveryState() {
        for (lang in listOf("en","fi","ru","sv","nb","da","et","lv","lt","fr","es","it","de","pl")) {
            assertEquals(9, SubscriptionState.values().map { subscriptionLabel(lang,it) }.toSet().size)
        }
    }
}

class DeveloperAccessTest {
    @org.junit.Test fun onlyVerifiedDeveloperInTestBuild() {
        val email = "vlad.vastsenko@gmail.com"
        for (debug in listOf(false,true)) for (preview in listOf(false,true))
            for (checkout in listOf(false,true)) for (verified in listOf(false,true))
                for (candidate in listOf(null,email,email.uppercase(),"other@example.com")) {
                    val expected = debug && !preview && !checkout && verified && candidate?.lowercase() == email
                    org.junit.Assert.assertEquals(expected, permitsDeveloperTest(debug,preview,checkout,verified,candidate))
                }
    }
}
