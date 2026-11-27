package fi.vladi.tarkastuspoytakirja.model

object MeasurementLabels {
    private val languages = listOf("fi", "en", "sv", "nb", "da", "et", "lv", "lt", "ru", "fr", "es", "it", "de", "pl")
    private val labels = mapOf(
        "heading" to listOf("Mittaukset", "Measurements", "Mätningar", "Målinger", "Målinger", "Mõõtmised", "Mērījumi", "Matavimai", "Измерения", "Mesures", "Mediciones", "Misurazioni", "Messungen", "Pomiary"),
        "parameter" to listOf("Mitattava suure", "Measured parameter", "Mätparameter", "Måleparameter", "Måleparameter", "Mõõdetav parameeter", "Mērāmais parametrs", "Matuojamas parametras", "Измеряемый параметр", "Paramètre mesuré", "Parámetro medido", "Parametro misurato", "Messparameter", "Mierzony parametr"),
        "value" to listOf("Mittausarvo", "Measured value", "Mätvärde", "Måleverdi", "Måleværdi", "Mõõteväärtus", "Izmērītā vērtība", "Išmatuota vertė", "Измеренное значение", "Valeur mesurée", "Valor medido", "Valore misurato", "Messwert", "Wartość zmierzona"),
        "unit" to listOf("Yksikkö", "Unit", "Enhet", "Enhet", "Enhed", "Ühik", "Mērvienība", "Matavimo vienetas", "Единица измерения", "Unité", "Unidad", "Unità", "Einheit", "Jednostka"),
        "limit" to listOf("Sallittu arvo", "Permitted value", "Tillåtet värde", "Tillatt verdi", "Tilladt værdi", "Lubatud väärtus", "Pieļaujamā vērtība", "Leistina vertė", "Допустимое значение", "Valeur admissible", "Valor admisible", "Valore ammissibile", "Zulässiger Wert", "Wartość dopuszczalna"),
        "add" to listOf("Lisää mittaus", "Add measurement", "Lägg till mätning", "Legg til måling", "Tilføj måling", "Lisa mõõtmine", "Pievienot mērījumu", "Pridėti matavimą", "Добавить измерение", "Ajouter une mesure", "Añadir medición", "Aggiungi misurazione", "Messung hinzufügen", "Dodaj pomiar")
    )
    fun text(key: String, language: String): String =
        labels[key]?.get(languages.indexOf(language).takeIf { it >= 0 } ?: 0) ?: key
}
