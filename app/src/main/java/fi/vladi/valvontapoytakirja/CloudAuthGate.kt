package fi.vladi.valvontapoytakirja

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.auth.GoogleAuthProvider

@Composable
internal fun CloudAuthGate(content: @Composable (onSignOut: () -> Unit, registrationCheckout: Boolean, onCheckoutFinished: () -> Unit, onCheckoutDeclined: () -> Unit) -> Unit) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    var signedIn by remember { mutableStateOf(auth.currentUser != null) }
    val guestPrefs = remember { context.getSharedPreferences("tarkastus_guest_mode", android.content.Context.MODE_PRIVATE) }
    var guest by remember { mutableStateOf(auth.currentUser == null && guestPrefs.getBoolean("enabled", true)) }
    var registrationCheckout by remember { mutableStateOf(guestPrefs.getBoolean("registration_checkout", false) && auth.currentUser != null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var register by remember { mutableStateOf(false) }
    var showPremiumTerms by remember { mutableStateOf(false) }
    var simulatedCheckout by remember { mutableStateOf(false) }
    var googleRegistrationIdentity by remember {
        mutableStateOf(guestPrefs.getString("pending_google_subject", null))
    }
    fun clearGoogleRegistration() {
        googleRegistrationIdentity = null
        guestPrefs.edit().remove("pending_google_subject").apply()
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!signedIn && !guest && googleRegistrationIdentity != null &&
            BuildConfig.PREMIUM_CHECKOUT_ENABLED) register = true
    }
    var message by remember { mutableStateOf<String?>(null) }
    val declineCheckout: () -> Unit = {
        auth.signOut()
        registrationCheckout = false
        guestPrefs.edit().remove("registration_checkout").putBoolean("enabled", true).apply()
        signedIn = false
        guest = true
        register = false
        password = ""
    }
    val finishCheckout: () -> Unit = {
        registrationCheckout = false
        guestPrefs.edit().remove("registration_checkout").apply()
    }
    val registrationPurchase = remember { PremiumRegistrationPurchase(context) }
    var registrationBilling by remember { mutableStateOf<PremiumBilling?>(null) }
    var paidPurchase by remember { mutableStateOf<com.android.billingclient.api.Purchase?>(null) }
    var pendingPurchase by remember { mutableStateOf(false) }
    var billingReady by remember { mutableStateOf(false) }
    androidx.compose.runtime.DisposableEffect(register) {
        if (register && BuildConfig.PREMIUM_CHECKOUT_ENABLED) {
            fun accept(purchases: List<com.android.billingclient.api.Purchase>) {
                pendingPurchase = purchases.any {
                    it.purchaseState == com.android.billingclient.api.Purchase.PurchaseState.PENDING &&
                    PREMIUM_PRODUCT in it.products &&
                    it.accountIdentifiers?.obfuscatedAccountId == registrationPurchase.accountHash
                }
                paidPurchase = purchases.firstOrNull {
                    it.purchaseState == com.android.billingclient.api.Purchase.PurchaseState.PURCHASED &&
                    !it.isSuspended && PREMIUM_PRODUCT in it.products &&
                    it.accountIdentifiers?.obfuscatedAccountId == registrationPurchase.accountHash
                }
            }
            val billing = PremiumBilling(context) { result, purchases ->
                busy = false
                if (result.responseCode == com.android.billingclient.api.BillingClient.BillingResponseCode.OK) {
                    accept(purchases)
                } else if (result.responseCode != com.android.billingclient.api.BillingClient.BillingResponseCode.USER_CANCELED) {
                    error = context.getString(R.string.auth_failed)
                }
            }
            registrationBilling = billing
            billing.connect { result ->
                billingReady = result.responseCode == com.android.billingclient.api.BillingClient.BillingResponseCode.OK
                if (billingReady) {
                    billingReady = false
                    billing.queryPurchases { query, purchases ->
                        billingReady = query.responseCode == com.android.billingclient.api.BillingClient.BillingResponseCode.OK
                        if (billingReady) accept(purchases)
                        else error = context.getString(R.string.auth_failed)
                    }
                } else error = context.getString(R.string.auth_failed)
            }
            onDispose { billing.close(); registrationBilling = null; billingReady = false; paidPurchase = null; pendingPurchase = false }
        } else onDispose {}
    }
    val developerGoogle = BuildConfig.DEBUG && !BuildConfig.OFFLINE_PREVIEW && !BuildConfig.PREMIUM_CHECKOUT_ENABLED
    val webClientId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName).let { if (it != 0) context.getString(it) else "" }
    val options = remember(webClientId) {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .apply { if (webClientId.isNotBlank()) requestIdToken(webClientId) }
            .requestEmail()
            .build()
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        run {
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    .getResult(ApiException::class.java)
                val token = account.idToken ?: throw IllegalStateException("Missing Google ID token")
                if (!developerGoogle) {
                    if (register && googleRegistrationIdentity != null && paidPurchase != null) {
                        if (account.id != googleRegistrationIdentity) {
                            busy = false
                            error = context.getString(R.string.google_failed)
                            return@rememberLauncherForActivityResult
                        }
                        registrationPurchase.completeGoogle(paidPurchase!!, token) { customToken ->
                            if (customToken == null) {
                                busy = false
                                error = context.getString(R.string.google_failed)
                            } else auth.signInWithCustomToken(customToken).addOnCompleteListener { task ->
                                busy = false
                                if (task.isSuccessful) {
                                    clearGoogleRegistration()
                                    register = false
                                    guest = false
                                    guestPrefs.edit().putBoolean("enabled", false).apply()
                                    signedIn = true
                                } else error = localizedAuthError(context, task.exception, R.string.google_failed)
                            }
                        }
                    } else FirebaseFunctions.getInstance("europe-west1").getHttpsCallable("signInExistingGoogle")
                        .call(mapOf("idToken" to token)).addOnCompleteListener { result ->
                            val data = if (result.isSuccessful) result.result.data as? Map<*, *> else null
                            val customToken = data?.get("customToken") as? String
                            if (data?.get("registrationRequired") == true) {
                                googleRegistrationIdentity = account.id ?: throw IllegalStateException("Missing Google identity")
                                check(guestPrefs.edit().putString("pending_google_subject", googleRegistrationIdentity).commit())
                                busy = false
                                showPremiumTerms = true
                            } else if (customToken != null) {
                                auth.signInWithCustomToken(customToken).addOnCompleteListener { task ->
                                    busy = false
                                    if (task.isSuccessful) {
                                        guest = false
                                        guestPrefs.edit().putBoolean("enabled", false).apply()
                                        signedIn = true
                                    } else error = localizedAuthError(context, task.exception, R.string.google_failed)
                                }
                            } else {
                                busy = false
                                error = context.getString(R.string.google_failed)
                            }
                        }
                    return@rememberLauncherForActivityResult
                }
                val login = run {
                    if (!account.email.equals("vlad.vastsenko@gmail.com", ignoreCase = true)) {
                        busy = false
                        showPremiumTerms = true
                        return@rememberLauncherForActivityResult
                    }
                    auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null))
                }
                login.addOnCompleteListener { task ->
                    busy = false
                    if (task.isSuccessful) {
                        guest = false
                        guestPrefs.edit().putBoolean("enabled", false).apply()
                        signedIn = true
                        error = null
                    } else {
                        error = localizedAuthError(context, task.exception, R.string.google_failed) + (if (developerGoogle) (task.exception as? FirebaseAuthException)?.errorCode?.let { " ($it)" }.orEmpty() else "")
                    }
                }
            } catch (exception: Exception) {
                busy = false
                error = localizedAuthError(context, exception, R.string.google_failed) + (if (exception is ApiException) " (${exception.statusCode})" else "")
            }
        }
    }
    if (showPremiumTerms && !signedIn && !guest) {
        PremiumRegistrationScreen(onContinue = { showPremiumTerms = false; if (developerGoogle) simulatedCheckout = true else register = true },
            onBack = { showPremiumTerms = false; clearGoogleRegistration() },
            onFree = { showPremiumTerms = false; clearGoogleRegistration(); guestPrefs.edit().putBoolean("enabled", true).apply(); guest = true })
        return
    }
    if (simulatedCheckout && developerGoogle && !signedIn && !guest) {
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(testCheckoutText(context, 0), style = MaterialTheme.typography.headlineSmall)
            Text(testCheckoutText(context, 1))
            Button(onClick = {
                simulatedCheckout = false
                message = testCheckoutText(context, 3)
            }, modifier = Modifier.fillMaxWidth()) { Text(testCheckoutText(context, 2)) }
            OutlinedButton(onClick = {
                simulatedCheckout = false
                message = testCheckoutText(context, 4)
            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.back)) }
        }
        return
    }
    if (guest && !signedIn) {
        content({ guest = false; guestPrefs.edit().putBoolean("enabled", false).apply() }, false, {}, {})
    } else if (signedIn) {
        BiometricSessionGate(onSignOut = {
            finishCheckout()
            auth.signOut()
            GoogleSignIn.getClient(context, options).signOut()
            signedIn = false
            password = ""
            error = null
            message = null
        }) { content({
            auth.signOut()
            GoogleSignIn.getClient(context, options).signOut()
            signedIn = false
            password = ""
            error = null
            message = null
        }, registrationCheckout, finishCheckout, declineCheckout) }
    } else {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Tarkastus Cloud", style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.guest_explanation), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = {
                clearGoogleRegistration()
                guestPrefs.edit().putBoolean("enabled", true).apply()
                guest = true
            }, enabled = !busy, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC8E6C9), contentColor = Color(0xFF173D20)), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.guest_continue))
            }
            Button(
                onClick = {
                    if ((!developerGoogle && !BuildConfig.PREMIUM_CHECKOUT_ENABLED) || webClientId.isBlank()) { error = context.getString(R.string.google_failed) }
                    else {
                        busy = true
                        error = null
                        try {
                            val client = GoogleSignIn.getClient(context, options)
                            client.signOut().addOnCompleteListener {
                                try { launcher.launch(client.signInIntent) }
                                catch (e: Exception) { busy = false; error = localizedAuthError(context, e, R.string.google_failed) }
                            }
                        }
                        catch (e: Exception) { busy = false; error = localizedAuthError(context, e, R.string.google_failed) }
                    }
                },
                enabled = !busy && (developerGoogle || BuildConfig.PREMIUM_CHECKOUT_ENABLED),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.sign_in))
            }
            Text(stringResource(R.string.auth_title))
            OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text(stringResource(R.string.email)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.password)) },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = stringResource(if (passwordVisible) R.string.password_hide else R.string.password_show)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            message?.let { Text(it) }
            Button(onClick = {
                busy = true
                error = null
                message = null
                val address = email.trim()
                if (googleRegistrationIdentity == null && (address.isBlank() || password.length < (if (register) 8 else 6))) {
                    busy = false
                    error = context.getString(R.string.password_short)
                } else {
                    if (register && pendingPurchase && paidPurchase == null) {
                        registrationBilling?.queryPurchases { query, purchases ->
                            if (query.responseCode != com.android.billingclient.api.BillingClient.BillingResponseCode.OK) {
                                busy = false
                                error = context.getString(R.string.auth_failed)
                                return@queryPurchases
                            }
                            paidPurchase = purchases.firstOrNull {
                                it.purchaseState == com.android.billingclient.api.Purchase.PurchaseState.PURCHASED &&
                                !it.isSuspended && PREMIUM_PRODUCT in it.products &&
                                it.accountIdentifiers?.obfuscatedAccountId == registrationPurchase.accountHash
                            }
                            pendingPurchase = purchases.any {
                                it.purchaseState == com.android.billingclient.api.Purchase.PurchaseState.PENDING &&
                                PREMIUM_PRODUCT in it.products &&
                                it.accountIdentifiers?.obfuscatedAccountId == registrationPurchase.accountHash
                            }
                            busy = false
                        } ?: run { busy = false }
                        return@Button
                    }
                    if (register && paidPurchase == null) {
                        val billing = registrationBilling
                        val activity = context as? android.app.Activity
                        if (!billingReady || billing == null || activity == null) {
                            busy = false
                            error = context.getString(R.string.auth_failed)
                        } else billing.queryOffer { query, details, offer ->
                            if (query.responseCode != com.android.billingclient.api.BillingClient.BillingResponseCode.OK || details == null || offer == null ||
                                registrationPurchase.launch(billing, activity, details, offer).responseCode != com.android.billingclient.api.BillingClient.BillingResponseCode.OK) {
                                busy = false
                                error = context.getString(R.string.auth_failed)
                            }
                        }
                        return@Button
                    }
                    if (register && googleRegistrationIdentity != null) {
                        try { launcher.launch(GoogleSignIn.getClient(context, options).signInIntent) }
                        catch (e: Exception) { busy = false; error = localizedAuthError(context, e, R.string.google_failed) }
                        return@Button
                    }
                    fun signIn() {
                    val task = auth.signInWithEmailAndPassword(address, password)
                    task.addOnCompleteListener { result ->
                        busy = false
                        if (result.isSuccessful) {
                            guest = false
                            guestPrefs.edit().putBoolean("enabled", false).apply()
                            signedIn = true
                            if (register) {
                                auth.currentUser?.sendEmailVerification()
                            }
                            register = false
                        } else error = localizedAuthError(context, result.exception, R.string.auth_failed)
                    }
                    }
                    if (register) {
                        val purchase = paidPurchase
                        if (purchase == null) { busy = false; error = context.getString(R.string.auth_failed) }
                        else registrationPurchase.complete(purchase, address, password) { created ->
                            if (created) signIn() else { busy = false; error = context.getString(R.string.auth_failed) }
                        }
                    } else signIn()
                }
            }, enabled = !busy && (!register || billingReady), modifier = Modifier.fillMaxWidth()) { Text(if (register) stringResource(R.string.create_account) else stringResource(R.string.email_sign_in)) }
            // Separate rows prevent long translations from clipping the password-reset action.
            OutlinedButton(onClick = { if (register) { register = false; clearGoogleRegistration() } else showPremiumTerms = true; error = null }, enabled = !busy,
                modifier = Modifier.fillMaxWidth()) {
                Text(if (register) stringResource(R.string.already_registered) else stringResource(R.string.new_user))
            }
            TextButton(onClick = {
                if (email.trim().isBlank()) error = context.getString(R.string.email_required)
                else {
                    busy = true
                    error = null
                    auth.sendPasswordResetEmail(email.trim()).addOnCompleteListener { result ->
                        busy = false
                        if (result.isSuccessful) message = context.getString(R.string.reset_sent)
                        else error = localizedAuthError(context, result.exception, R.string.reset_failed)
                    }
                }
            }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.forgot_password))
            }
            if (busy) CircularProgressIndicator()
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

private fun localizedAuthError(context: android.content.Context, error: Exception?, fallback: Int): String {
    val resource = when {
        error is FirebaseNetworkException -> R.string.auth_error_network
        error is FirebaseAuthException -> when (error.errorCode) {
            "ERROR_INVALID_EMAIL" -> R.string.auth_error_invalid_email
            "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL", "ERROR_USER_NOT_FOUND",
            "ERROR_USER_DISABLED", "ERROR_INVALID_LOGIN_CREDENTIALS" -> R.string.auth_error_invalid_credentials
            "ERROR_EMAIL_ALREADY_IN_USE" -> R.string.auth_error_email_in_use
            "ERROR_WEAK_PASSWORD" -> R.string.auth_error_weak_password
            "ERROR_TOO_MANY_REQUESTS" -> R.string.auth_error_too_many
            "ERROR_NETWORK_REQUEST_FAILED" -> R.string.auth_error_network
            else -> fallback
        }
        else -> fallback
    }
    return context.getString(resource)
}



private fun testCheckoutText(context: android.content.Context, index: Int): String {
    val language = context.resources.configuration.locales[0].language
    val labels = when (language) {
        "fi" -> listOf("Testimaksu", "Maksua simuloidaan. Ei veloitusta, Firebase-tiliä eikä pilvipalvelua.", "Vahvista testimaksu", "Testimaksu onnistui. Tiliä ei luotu.", "Testimaksu peruttu")
        "ru" -> listOf("Тестовая оплата", "Имитация оплаты. Без списания денег, аккаунта Firebase и доступа к облаку.", "Подтвердить тестовую оплату", "Тестовая оплата успешна. Аккаунт не создан.", "Тестовая оплата отменена")
        "sv" -> listOf("Testbetalning", "Simulerad betalning. Ingen debitering, inget Firebase-konto eller molnåtkomst.", "Bekräfta testbetalning", "Testbetalningen lyckades. Inget konto skapades.", "Testbetalningen avbröts")
        "nb" -> listOf("Testbetaling", "Simulert betaling. Ingen belastning, Firebase-konto eller skytilgang.", "Bekreft testbetaling", "Testbetalingen lyktes. Ingen konto opprettet.", "Testbetalingen ble avbrutt")
        "da" -> listOf("Testbetaling", "Simuleret betaling. Ingen opkrævning, Firebase-konto eller skyadgang.", "Bekræft testbetaling", "Testbetalingen lykkedes. Ingen konto oprettet.", "Testbetalingen blev annulleret")
        "et" -> listOf("Testmakse", "Simuleeritud makse. Raha ei võeta, Firebase’i kontot ega pilvejuurdepääsu ei looda.", "Kinnita testmakse", "Testmakse õnnestus. Kontot ei loodud.", "Testmakse tühistati")
        "lv" -> listOf("Testa maksājums", "Simulēts maksājums. Nav maksas, Firebase konta vai mākoņa piekļuves.", "Apstiprināt testa maksājumu", "Testa maksājums veiksmīgs. Konts nav izveidots.", "Testa maksājums atcelts")
        "lt" -> listOf("Bandomasis mokėjimas", "Imituojamas mokėjimas. Nėra mokesčio, Firebase paskyros ar prieigos prie debesijos.", "Patvirtinti bandomąjį mokėjimą", "Bandomasis mokėjimas sėkmingas. Paskyra nesukurta.", "Bandomasis mokėjimas atšauktas")
        "fr" -> listOf("Paiement de test", "Paiement simulé. Aucun débit, compte Firebase ni accès au cloud.", "Confirmer le paiement de test", "Paiement de test réussi. Aucun compte créé.", "Paiement de test annulé")
        "es" -> listOf("Pago de prueba", "Pago simulado. Sin cargo, cuenta de Firebase ni acceso a la nube.", "Confirmar pago de prueba", "Pago de prueba correcto. No se creó ninguna cuenta.", "Pago de prueba cancelado")
        "it" -> listOf("Pagamento di prova", "Pagamento simulato. Nessun addebito, account Firebase o accesso al cloud.", "Conferma pagamento di prova", "Pagamento di prova riuscito. Nessun account creato.", "Pagamento di prova annullato")
        "de" -> listOf("Testzahlung", "Simulierte Zahlung. Keine Abbuchung, kein Firebase-Konto und kein Cloud-Zugriff.", "Testzahlung bestätigen", "Testzahlung erfolgreich. Kein Konto erstellt.", "Testzahlung abgebrochen")
        "pl" -> listOf("Płatność testowa", "Symulowana płatność. Bez obciążenia, konta Firebase ani dostępu do chmury.", "Potwierdź płatność testową", "Płatność testowa udana. Nie utworzono konta.", "Płatność testowa anulowana")
        else -> listOf("Test payment", "Simulated payment. No charge, Firebase account or cloud access.", "Confirm test payment", "Test payment successful. No account created.", "Test payment cancelled")
    }
    return labels[index]
}
