package fi.vladi.valvontapoytakirja.data

import java.io.File

/** Resolve portable image paths without trusting paths outside the app's attachment roots. */
internal fun resolveReportAttachment(root: File, reportId: String, value: String, allowSettings: Boolean = false): String {
    if (value.isBlank() || !reportId.matches(Regex("[a-zA-Z0-9_-]{1,128}"))) return ""
    val base = root.canonicalFile
    val relative = when {
        value.startsWith(base.path + File.separator) -> value.removePrefix(base.path + File.separator)
        value.startsWith("/") && value.contains("/files/") -> value.substringAfterLast("/files/")
        else -> value
    }
    if (relative.split('/').any { it.isBlank() || it == "." || it == ".." || it.contains('\\') }) return ""
    val allowed = listOf("reports/$reportId/") + if (allowSettings) listOf("settings/", "inspectors/") else emptyList()
    val prefix = allowed.firstOrNull { relative.startsWith(it) } ?: return ""
    if (File(relative).extension.lowercase() !in setOf("png", "jpg", "jpeg", "webp")) return ""
    val directory = File(base, prefix).canonicalFile
    val candidate = File(base, relative).canonicalFile
    if (!directory.path.startsWith(base.path + File.separator) || !candidate.path.startsWith(directory.path + File.separator)) return ""
    return if (candidate.isFile) candidate.path else relative
}
