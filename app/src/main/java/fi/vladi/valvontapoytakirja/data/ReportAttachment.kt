package fi.vladi.valvontapoytakirja.data

import java.io.File

/** Resolve only attachments belonging to this report, without rewriting shared JSON on read. */
internal fun resolveReportAttachment(root: File, reportId: String, value: String): String {
    if (value.isBlank() || !reportId.matches(Regex("[a-zA-Z0-9_-]{1,128}"))) return value
    val prefix = "reports/$reportId/"
    val relative = when {
        value.startsWith(prefix) -> value
        value.contains("/files/$prefix") -> prefix + value.substringAfter("/files/$prefix")
        else -> return value
    }
    if (relative.split('/').any { it.isBlank() || it == "." || it == ".." }) return value
    val directory = File(root, "reports/$reportId").canonicalFile
    val candidate = File(root, relative).canonicalFile
    return if (candidate.path.startsWith(directory.path + File.separator) && candidate.isFile) candidate.path else value
}
