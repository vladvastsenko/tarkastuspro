package fi.vladi.valvontapoytakirja
internal fun subscriptionLabel(language: String, state: SubscriptionState): String {
    val labels = when(language) {
        "fi" -> "Maksuton|Odottaa|Aktiivinen|Peruttu|Lisäaika|Vanhentunut|Maksu keskeytynyt|Tauolla|Tuntematon"
        "ru" -> "Бесплатная|Ожидание оплаты|Активная|Отменена|Льготный период|Истекла|Проблема с оплатой|Приостановлена|Неизвестно"
        "sv" -> "Gratis|Väntar|Aktiv|Uppsagd|Respitperiod|Utgången|Betalningsspärr|Pausad|Okänd"
        "nb" -> "Gratis|Venter|Aktiv|Oppsagt|Fristperiode|Utløpt|Betalingsstans|På pause|Ukjent"
        "da" -> "Gratis|Afventer|Aktiv|Opsagt|Henstandsperiode|Udløbet|Betalingsstop|Sat på pause|Ukendt"
        "et" -> "Tasuta|Ootel|Aktiivne|Tühistatud|Ajapikendus|Aegunud|Makse peatatud|Pausil|Teadmata"
        "lv" -> "Bezmaksas|Gaida maksājumu|Aktīvs|Atcelts|Labvēlības periods|Beidzies|Maksājums aizturēts|Pauzēts|Nezināms"
        "lt" -> "Nemokama|Laukiama|Aktyvi|Atšaukta|Atidėjimo laikotarpis|Pasibaigusi|Mokėjimas sustabdytas|Pristabdyta|Nežinoma"
        "fr" -> "Gratuit|En attente|Actif|Annulé|Délai de grâce|Expiré|Paiement suspendu|En pause|Inconnu"
        "es" -> "Gratis|Pendiente|Activa|Cancelada|Periodo de gracia|Caducada|Pago retenido|En pausa|Desconocida"
        "it" -> "Gratuito|In attesa|Attivo|Annullato|Periodo di tolleranza|Scaduto|Pagamento sospeso|In pausa|Sconosciuto"
        "de" -> "Kostenlos|Ausstehend|Aktiv|Gekündigt|Kulanzzeit|Abgelaufen|Zahlung ausgesetzt|Pausiert|Unbekannt"
        "pl" -> "Bezpłatna|Oczekująca|Aktywna|Anulowana|Okres karencji|Wygasła|Wstrzymana płatność|Wstrzymana|Nieznana"
        else -> "Free|Pending|Active|Cancelled|Grace period|Expired|On hold|Paused|Unknown"
    }
    return labels.split('|')[state.ordinal]
}
