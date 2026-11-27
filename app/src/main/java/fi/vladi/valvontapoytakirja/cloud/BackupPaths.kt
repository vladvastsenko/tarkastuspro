package fi.vladi.valvontapoytakirja.cloud
import java.io.File
import kotlinx.serialization.json.*

/** Rewrite only known attachment fields and only paths with an existing staged counterpart. */
internal fun relocateBackupPaths(staging: File, destination: File) {
    val pathKeys = setOf("filePath","annotatedPath","logoPath","signatureImagePath")
    val roots = setOf("settings","inspectors","tarkastus_reports","tarkastus_templates","custom_reports")
    fun relocate(value: String): String {
        if (!value.startsWith("/")) return value
        val parts = value.split('/')
        val index = parts.indices.firstOrNull { i -> parts[i] == "files" && i+1 < parts.size && parts[i+1] in roots } ?: return value
        val relative = parts.drop(index+1).joinToString("/")
        val candidate = File(staging,relative).canonicalFile
        require(candidate.path.startsWith(staging.canonicalPath + File.separator))
        return if (candidate.isFile) File(destination,relative).path else value
    }
    fun transform(node: JsonElement): JsonElement = when(node) {
        is JsonObject -> JsonObject(node.mapValues { (key,value) ->
            if (key in pathKeys && value is JsonPrimitive && value.isString) JsonPrimitive(relocate(value.content))
            else transform(value)
        })
        is JsonArray -> JsonArray(node.map(::transform))
        else -> node
    }
    staging.walkTopDown().filter { it.isFile && (it.name.endsWith(".json") || it.name.endsWith(".json.bak")) }.forEach { file ->
        val relative = file.relativeTo(staging).invariantSeparatorsPath
        if (relative == "app_settings.json" || relative == "app_settings.json.bak" || relative.substringBefore('/') in roots) {
            val before = Json.parseToJsonElement(file.readText())
            val after = transform(before)
            if (before != after) file.writeText(after.toString())
        }
    }
}
