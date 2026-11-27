package fi.vladi.tarkastuspoytakirja.pdf

/** A human-readable filename, with only characters forbidden by file systems removed. */
internal object PdfFileNames {
    fun name(language: String, kind: String, site: String): String {
        val prefix = kind.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]+"), "_")
            .trim().trim('_', '.', ' ')
            .replace(Regex("\\s+"), "_")
            .take(65).ifBlank { when(language) { "ru" -> "объект"; "en" -> "site"; "sv" -> "objekt"; "et" -> "objekt"; "lt" -> "objektas"; "lv" -> "objekts"; "nb" -> "objekt"; "da" -> "objekt"; else -> "kohde" } }
        val detail = site.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]+"), "_")
            .trim().trim('_', '.', ' ')
            .replace(Regex("\\s+"), "_")
            .take(100).ifBlank { when(language) { "ru" -> "объект"; "en" -> "site"; "sv" -> "objekt"; "et" -> "objekt"; "lt" -> "objektas"; "lv" -> "objekts"; "nb" -> "objekt"; "da" -> "objekt"; else -> "kohde" } }
        return "${prefix}_${detail}.pdf"
    }
}
