package fi.vladi.tarkastuspoytakirja.model

object ChecklistLabels {
    val results = listOf("", "pass", "fail", "na")
    private val languages = listOf("fi", "en", "sv", "nb", "da", "et", "lv", "lt", "ru", "fr", "es", "it", "de", "pl")
    private val labels = mapOf(
        "heading" to listOf("Tarkistuslista", "Checklist", "Checklista", "Sjekkliste", "Tjekliste", "Kontrollnimekiri", "Kontrolsaraksts", "Kontrolinis sąrašas", "Чек-лист", "Liste de contrôle", "Lista de comprobación", "Lista di controllo", "Checkliste", "Lista kontrolna"),
        "item" to listOf("Tarkistettava asia", "Check item", "Kontrollpunkt", "Kontrollpunkt", "Kontrolpunkt", "Kontrollpunkt", "Pārbaudes punkts", "Patikros punktas", "Пункт проверки", "Point de contrôle", "Punto de control", "Voce di controllo", "Prüfpunkt", "Punkt kontroli"),
        "add" to listOf("Lisää tarkistuskohta", "Add check item", "Lägg till kontrollpunkt", "Legg til kontrollpunkt", "Tilføj kontrolpunkt", "Lisa kontrollpunkt", "Pievienot pārbaudes punktu", "Pridėti patikros punktą", "Добавить пункт проверки", "Ajouter un point", "Añadir punto", "Aggiungi voce", "Prüfpunkt hinzufügen", "Dodaj punkt"),
        "result" to listOf("Tulos", "Result", "Resultat", "Resultat", "Resultat", "Tulemus", "Rezultāts", "Rezultatas", "Результат", "Résultat", "Resultado", "Esito", "Ergebnis", "Wynik"),
        "none" to listOf("Ei valittu", "Not selected", "Inte valt", "Ikke valgt", "Ikke valgt", "Valimata", "Nav izvēlēts", "Nepasirinkta", "Не выбрано", "Non sélectionné", "Sin seleccionar", "Non selezionato", "Nicht ausgewählt", "Nie wybrano"),
        "pass" to listOf("Vaatimusten mukainen", "Compliant", "Uppfyller kraven", "Oppfyller kravene", "Opfylder kravene", "Vastab nõuetele", "Atbilst prasībām", "Atitinka reikalavimus", "Соответствует", "Conforme", "Conforme", "Conforme", "Konform", "Zgodne"),
        "fail" to listOf("Ei vaatimusten mukainen", "Non-compliant", "Uppfyller inte kraven", "Oppfyller ikke kravene", "Opfylder ikke kravene", "Ei vasta nõuetele", "Neatbilst prasībām", "Neatitinka reikalavimų", "Не соответствует", "Non conforme", "No conforme", "Non conforme", "Nicht konform", "Niezgodne"),
        "na" to listOf("Ei sovellu", "Not applicable", "Ej tillämpligt", "Ikke relevant", "Ikke relevant", "Ei kohaldu", "Nav piemērojams", "Netaikoma", "Не применимо", "Sans objet", "No aplicable", "Non applicabile", "Nicht anwendbar", "Nie dotyczy")
    )
    fun text(key: String, language: String): String =
        labels[key]?.get(languages.indexOf(language).takeIf { it >= 0 } ?: 0) ?: key
}
