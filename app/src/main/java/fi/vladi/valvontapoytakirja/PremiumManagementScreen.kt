package fi.vladi.valvontapoytakirja

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fi.vladi.tarkastuspoytakirja.AppLanguage

private fun labels(language: String): List<String> = when(language) {
    "fi" -> listOf("Tilaa", "Palauta tilaus", "Maksu ei ole käytettävissä", "Tarkistetaan…")
    "ru" -> listOf("Оформить подписку", "Восстановить подписку", "Оплата недоступна", "Проверка…")
    "sv" -> listOf("Prenumerera", "Återställ prenumeration", "Betalning inte tillgänglig", "Kontrollerar…")
    "nb" -> listOf("Abonner", "Gjenopprett abonnement", "Betaling utilgjengelig", "Kontrollerer…")
    "da" -> listOf("Abonner", "Gendan abonnement", "Betaling ikke tilgængelig", "Kontrollerer…")
    "et" -> listOf("Telli", "Taasta tellimus", "Makse pole saadaval", "Kontrollimine…")
    "lv" -> listOf("Abonēt", "Atjaunot abonementu", "Maksājums nav pieejams", "Pārbaude…")
    "lt" -> listOf("Prenumeruoti", "Atkurti prenumeratą", "Mokėjimas nepasiekiamas", "Tikrinama…")
    "fr" -> listOf("S’abonner", "Restaurer l’abonnement", "Paiement indisponible", "Vérification…")
    "es" -> listOf("Suscribirse", "Restaurar suscripción", "Pago no disponible", "Comprobando…")
    "it" -> listOf("Abbonati", "Ripristina abbonamento", "Pagamento non disponibile", "Verifica…")
    "de" -> listOf("Abonnieren", "Abo wiederherstellen", "Zahlung nicht verfügbar", "Wird geprüft…")
    "pl" -> listOf("Subskrybuj", "Przywróć subskrypcję", "Płatność niedostępna", "Sprawdzanie…")
    else -> listOf("Subscribe", "Restore subscription", "Payment unavailable", "Checking…")
}
@Composable
internal fun PremiumManagementScreen(controller: PremiumController, onBack: () -> Unit, onSignIn: () -> Unit, registrationCheckout: Boolean = false) {
    val context = LocalContext.current
    val lang = AppLanguage.selected(context)
    val l = labels(lang)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Tarkastus Cloud Premium", style = MaterialTheme.typography.headlineMedium)
        Text(if (controller.active) "Premium" else subscriptionLabel(lang, SubscriptionState.FREE))
        Text(subscriptionLabel(lang, controller.subscriptionState))
        Text(premiumTerms(lang).first)
        controller.offer?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice?.let { Text(it) }
        if (BuildConfig.DEBUG && BuildConfig.OFFLINE_PREVIEW) {
            Text("TEST · Free / Premium")
            SubscriptionState.values().forEach { state ->
                OutlinedButton(onClick = { controller.simulate(state) }, modifier = Modifier.fillMaxWidth()) {
                    Text(subscriptionLabel(lang, state) + if (controller.simulatedState == state) " ✓" else "")
                }
            }
        } else if (isDeveloperTestAccount()) {
            Text("DEVELOPER · TEST")
            OutlinedButton(onClick = { controller.setDeveloperFree(true) }, modifier = Modifier.fillMaxWidth()) { Text("Free" + if (!controller.active) " ✓" else "") }
            OutlinedButton(onClick = { controller.setDeveloperFree(false) }, modifier = Modifier.fillMaxWidth()) { Text("Premium" + if (controller.active) " ✓" else "") }
        } else if (controller.uid == null && !BuildConfig.OFFLINE_PREVIEW) {
            Button(onClick = onSignIn) { Text(stringResource(R.string.guest_sign_in)) }
        } else {
            Button(onClick = { (context as? android.app.Activity)?.let(controller::buy) },
                enabled = BuildConfig.PREMIUM_CHECKOUT_ENABLED && controller.ready && controller.offer != null && !controller.active && !controller.busy) { Text(l[0]) }
            OutlinedButton(onClick = controller::refresh, enabled = controller.ready && !controller.busy) { Text(l[1]) }
        }
        if (controller.busy) Text(l[3])
        if (controller.error) Text(l[2], color = MaterialTheme.colorScheme.error)
        if (!BuildConfig.PREMIUM_CHECKOUT_ENABLED || !controller.ready) Text(l[2])
        if (registrationCheckout && !controller.active) {
            Button(onClick = onBack, enabled = !controller.busy, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFFC8E6C9), contentColor = androidx.compose.ui.graphics.Color(0xFF173D20))) {
                Text(stringResource(R.string.guest_continue))
            }
        } else TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
    }
}
