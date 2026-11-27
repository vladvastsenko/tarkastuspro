package fi.vladi.tarkastuspoytakirja.model

/** Stable preset IDs; user-entered titles are never translated. */
object ReportTypes {
    val ids = listOf("inspection", "defects", "handover", "warranty", "visit", "supervision", "custom", "freeform")
    private val languages = listOf("fi", "en", "sv", "nb", "da", "et", "lv", "lt", "ru", "fr", "es", "it", "de", "pl")
    private val titles = mapOf(
        "inspection" to listOf("Tarkastuspöytäkirja", "Inspection report", "Besiktningsprotokoll", "Befaringsrapport", "Inspektionsrapport", "Ülevaatusakt", "Pārbaudes akts", "Patikros aktas", "Протокол осмотра", "Rapport d’inspection", "Informe de inspección", "Verbale di ispezione", "Prüfbericht", "Protokół kontroli"),
        "defects" to listOf("Virhe- ja puuteluettelo", "Defects and deficiencies list", "Fel- och bristlista", "Feil- og mangelliste", "Fejl- og mangelliste", "Puuduste nimekiri", "Defektu saraksts", "Defektų sąrašas", "Перечень дефектов", "Liste des défauts", "Lista de defectos", "Elenco dei difetti", "Mängelliste", "Lista usterek"),
        "handover" to listOf("Vastaanottotarkastus", "Handover inspection", "Slutbesiktning", "Overtakelsesbefaring", "Afleveringsforretning", "Vastuvõtuakt", "Pieņemšanas pārbaude", "Priėmimo patikra", "Приемочная проверка", "Inspection de réception", "Inspección de recepción", "Verifica di consegna", "Abnahmeprüfung", "Odbiór techniczny"),
        "warranty" to listOf("Takuutarkastus", "Warranty inspection", "Garantibesiktning", "Garantibefaring", "Garantieftersyn", "Garantiikontroll", "Garantijas pārbaude", "Garantinė patikra", "Гарантийная проверка", "Inspection de garantie", "Inspección de garantía", "Verifica in garanzia", "Gewährleistungsprüfung", "Przegląd gwarancyjny"),
        "visit" to listOf("Työmaakäyntiraportti", "Site visit report", "Byggplatsbesöksrapport", "Byggeplassrapport", "Byggepladsrapport", "Objekti külastuse aruanne", "Objekta apmeklējuma pārskats", "Objekto vizito ataskaita", "Отчет о посещении объекта", "Rapport de visite de chantier", "Informe de visita de obra", "Rapporto di visita in cantiere", "Baustellenbesuchsbericht", "Raport z wizyty na budowie"),
        "supervision" to listOf("Valvontapöytäkirja", "Supervision report", "Kontrollprotokoll", "Kontrollrapport", "Kontrolrapport", "Järelevalveakt", "Uzraudzības protokols", "Priežiūros protokolas", "Протокол строительного контроля", "Rapport de surveillance", "Informe de supervisión", "Verbale di supervisione", "Überwachungsbericht", "Protokół nadzoru"),
        "freeform" to listOf("Vapaamuotoinen asiakirja", "Free-form document", "Dokument i fri form", "Dokument i fri form", "Dokument i fri form", "Vabas vormis dokument", "Brīvas formas dokuments", "Laisvos formos dokumentas", "Свободная форма документа", "Document libre", "Documento de formato libre", "Documento in forma libera", "Freiformdokument", "Dokument o dowolnej formie"),
        "custom" to listOf("Oma raportti", "Custom report", "Egen rapport", "Egen rapport", "Egen rapport", "Oma aruanne", "Pielāgots pārskats", "Individuali ataskaita", "Собственный отчет", "Rapport personnalisé", "Informe personalizado", "Rapporto personalizzato", "Eigener Bericht", "Raport własny")
    )
    fun title(id: String, language: String): String =
        (titles[id] ?: titles.getValue("inspection"))[languages.indexOf(language).takeIf { it >= 0 } ?: 0]
    fun title(report: InspectionReport, language: String): String =
        report.documentTitle.trim().ifBlank { title(report.reportType, language) }
}
