package fi.vladi.valvontapoytakirja

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.android.billingclient.api.BillingClient
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fi.vladi.tarkastuspoytakirja.AppLanguage

@Composable
internal fun PremiumRegistrationScreen(onContinue: () -> Unit, onBack: () -> Unit, onFree: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val terms = premiumTerms(AppLanguage.selected(context))
    var price by remember { mutableStateOf<String?>(null) }
    DisposableEffect(context) {
        val billing = PremiumBilling(context) { _, _ -> }
        billing.connect { result ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                billing.queryOffer { status, _, offer ->
                    if (status.responseCode == BillingClient.BillingResponseCode.OK) {
                        price = offer?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
                    }
                }
            }
        }
        onDispose { billing.close() }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Tarkastus Cloud", style = MaterialTheme.typography.headlineLarge)
        Text("Premium", style = MaterialTheme.typography.headlineMedium)
        price?.let { Text(it, style = MaterialTheme.typography.headlineSmall) }
        Text(terms.first)
        Text(stringResource(R.string.cloud_sync))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text(terms.second) }
        Button(onClick = onFree, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC8E6C9), contentColor = Color(0xFF173D20)), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.guest_continue)) }
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.back)) }
    }
}
