package fi.vladi.tarkastuspoytakirja.model

object FollowUpLabels {
    private val languages = listOf("fi", "en", "sv", "nb", "da", "et", "lv", "lt", "ru", "fr", "es", "it", "de", "pl")
    val statuses = listOf("", "open", "progress", "resolved", "verified")
    val priorities = listOf("", "low", "normal", "high", "urgent")
    private val labels = mapOf(
        "details" to listOf("Seurantatiedot", "Follow-up details", "Uppföljningsuppgifter", "Oppfølgingsdetaljer", "Opfølgningsoplysninger", "Järelkontrolli andmed", "Pēcpārbaudes dati", "Tolesnės patikros duomenys", "Контроль устранения", "Suivi", "Seguimiento", "Monitoraggio", "Nachverfolgung", "Działania następcze"),
        "status" to listOf("Tila", "Status", "Status", "Status", "Status", "Olek", "Statuss", "Būsena", "Статус", "Statut", "Estado", "Stato", "Status", "Status"),
        "priority" to listOf("Kiireellisyys", "Priority", "Prioritet", "Prioritet", "Prioritet", "Prioriteet", "Prioritāte", "Prioritetas", "Приоритет", "Priorité", "Prioridad", "Priorità", "Priorität", "Priorytet"),
        "responsible" to listOf("Vastuuhenkilö", "Responsible person", "Ansvarig", "Ansvarlig", "Ansvarlig", "Vastutaja", "Atbildīgā persona", "Atsakingas asmuo", "Ответственный", "Responsable", "Responsable", "Responsabile", "Verantwortliche Person", "Osoba odpowiedzialna"),
        "due" to listOf("Korjaamisen määräaika", "Remediation deadline", "Åtgärdsfrist", "Utbedringsfrist", "Udbedringsfrist", "Parandamise tähtaeg", "Novēršanas termiņš", "Šalinimo terminas", "Срок устранения", "Délai de correction", "Plazo de subsanación", "Termine di riparazione", "Behebungsfrist", "Termin usunięcia usterki"),
        "recheck" to listOf("Uusintatarkastus", "Reinspection date", "Efterbesiktning", "Ny kontroll", "Efterkontrol", "Järelkontroll", "Atkārtota pārbaude", "Pakartotinė patikra", "Дата повторной проверки", "Date de contre-visite", "Fecha de reinspección", "Data di nuova verifica", "Nachprüfdatum", "Data ponownej kontroli"),
        "open" to listOf("Avoin", "Open", "Öppen", "Åpen", "Åben", "Avatud", "Atvērts", "Atvira", "Открыто", "Ouvert", "Abierto", "Aperto", "Offen", "Otwarte"),
        "progress" to listOf("Työn alla", "In progress", "Pågående", "Pågår", "I gang", "Töös", "Procesā", "Vykdoma", "В работе", "En cours", "En curso", "In corso", "In Bearbeitung", "W trakcie"),
        "resolved" to listOf("Korjattu", "Resolved", "Åtgärdad", "Utbedret", "Udbedret", "Parandatud", "Novērsts", "Pašalinta", "Устранено", "Corrigé", "Subsanado", "Risolto", "Behoben", "Usunięte"),
        "verified" to listOf("Tarkastettu", "Verified", "Verifierad", "Verifisert", "Kontrolleret", "Kontrollitud", "Pārbaudīts", "Patikrinta", "Проверено", "Vérifié", "Verificado", "Verificato", "Geprüft", "Sprawdzone"),
        "low" to listOf("Matala", "Low", "Låg", "Lav", "Lav", "Madal", "Zema", "Žemas", "Низкий", "Faible", "Baja", "Bassa", "Niedrig", "Niski"),
        "normal" to listOf("Normaali", "Normal", "Normal", "Normal", "Normal", "Tavaline", "Normāla", "Įprastas", "Обычный", "Normale", "Normal", "Normale", "Normal", "Normalny"),
        "high" to listOf("Korkea", "High", "Hög", "Høy", "Høj", "Kõrge", "Augsta", "Aukštas", "Высокий", "Élevée", "Alta", "Alta", "Hoch", "Wysoki"),
        "urgent" to listOf("Kiireellinen", "Urgent", "Brådskande", "Haster", "Haster", "Kiire", "Steidzama", "Skubus", "Срочный", "Urgente", "Urgente", "Urgente", "Dringend", "Pilny"),
        "none" to listOf("Ei valittu", "Not selected", "Inte valt", "Ikke valgt", "Ikke valgt", "Valimata", "Nav izvēlēts", "Nepasirinkta", "Не выбрано", "Non sélectionné", "Sin seleccionar", "Non selezionato", "Nicht ausgewählt", "Nie wybrano"),
        "clear" to listOf("Tyhjennä", "Clear", "Rensa", "Tøm", "Ryd", "Tühjenda", "Notīrīt", "Išvalyti", "Очистить", "Effacer", "Borrar", "Cancella", "Leeren", "Wyczyść")
    )
    fun text(key: String, language: String): String =
        labels[key]?.get(languages.indexOf(language).takeIf { it >= 0 } ?: 0) ?: key
    fun value(key: String, language: String): String =
        if (key.isBlank()) "" else text(key, language)
    fun date(value: String): String = if (value.isBlank()) "" else
        runCatching { java.time.LocalDate.parse(value).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")) }.getOrDefault(value)
}
