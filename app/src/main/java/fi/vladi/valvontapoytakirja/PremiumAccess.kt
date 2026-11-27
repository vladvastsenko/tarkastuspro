package fi.vladi.valvontapoytakirja
import android.os.SystemClock
import com.google.firebase.auth.FirebaseAuth

internal fun permitsCloudLease(preview: Boolean, verified: VerifiedSubscription?, uid: String?, wallNow: Long, elapsedNow: Long, deadline: Long): Boolean =
    !preview && elapsedNow < deadline && verified?.permitsPremium(uid, wallNow) == true

// Temporary authenticated test access; never enabled in release or checkout builds.
internal fun permitsDeveloperTest(debug: Boolean, preview: Boolean, checkout: Boolean, verified: Boolean, email: String?): Boolean =
    debug && !preview && !checkout && verified && email.equals("vlad.vastsenko@gmail.com", ignoreCase = true)

internal fun isDeveloperTestAccount(): Boolean {
    if (!BuildConfig.DEBUG || BuildConfig.OFFLINE_PREVIEW || BuildConfig.PREMIUM_CHECKOUT_ENABLED) return false
    val user = FirebaseAuth.getInstance().currentUser ?: return false
    return permitsDeveloperTest(BuildConfig.DEBUG, BuildConfig.OFFLINE_PREVIEW, BuildConfig.PREMIUM_CHECKOUT_ENABLED, user.isEmailVerified, user.email)
}

internal object PremiumAccess {
    private data class Lease(val verified: VerifiedSubscription, val deadline: Long)
    @Volatile private var lease: Lease? = null
    private val developerMode = DeveloperTestMode()
    fun resetDeveloperMode() = developerMode.reset()
    fun setDeveloperFree(free: Boolean) {
        if (isDeveloperTestAccount()) developerMode.select(FirebaseAuth.getInstance().currentUser?.uid, free)
    }
    fun developerPremium(): Boolean = isDeveloperTestAccount() &&
        developerMode.premium(FirebaseAuth.getInstance().currentUser?.uid)
    fun clear() { lease = null }
    fun update(verified: VerifiedSubscription?, deadline: Long) {
        lease = if (verified == null || BuildConfig.OFFLINE_PREVIEW) null else Lease(verified, deadline)
    }
    fun requireCloud() {
        check(!BuildConfig.OFFLINE_PREVIEW) { "Premium required" }
        if (isDeveloperTestAccount()) { check(developerPremium()) { "Premium required" }; return }
        val current = lease
        check(current != null && permitsCloudLease(BuildConfig.OFFLINE_PREVIEW, current.verified,
            FirebaseAuth.getInstance().currentUser?.uid, System.currentTimeMillis(), SystemClock.elapsedRealtime(), current.deadline)) { "Premium required" }
    }
}

internal class DeveloperTestMode {
    private data class Selection(val uid: String, val free: Boolean)
    @Volatile private var selection: Selection? = null
    fun reset() { selection = null }
    fun select(uid: String?, free: Boolean) { selection = uid?.let { Selection(it, free) } }
    fun premium(uid: String?): Boolean {
        val current = selection
        return uid != null && !(current?.uid == uid && current.free)
    }
}
