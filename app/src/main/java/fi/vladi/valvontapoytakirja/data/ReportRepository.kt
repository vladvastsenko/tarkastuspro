package fi.vladi.valvontapoytakirja.data

import android.content.Context
import android.net.Uri
import fi.vladi.valvontapoytakirja.model.AppSettings
import fi.vladi.valvontapoytakirja.model.InspectorProfile
import fi.vladi.valvontapoytakirja.model.CustomerProfile
import fi.vladi.valvontapoytakirja.model.CustomerSite
import fi.vladi.tarkastuspoytakirja.storage.SharedDocumentsArchive
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant

class ReportRepository(private val context: Context) {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private fun settingsFile(): File = File(context.filesDir, "app_settings.json")

    fun loadSettings(): AppSettings =
        runCatching { json.decodeFromString(AppSettings.serializer(), settingsFile().readText()) }
            .getOrDefault(AppSettings())

    fun saveSettings(settings: AppSettings) {
        settingsFile().writeAtomicText(json.encodeToString(AppSettings.serializer(), settings))
        SharedDocumentsArchive.archiveText(context, json.encodeToString(AppSettings.serializer(), settings), "Asetukset", "app_settings.json")
        File(settings.logoPath).takeIf { it.isFile }?.let { logo ->
            SharedDocumentsArchive.archiveFile(context, logo, "Asetukset", logo.name)
        }
        File(settings.signatureImagePath).takeIf { it.isFile }?.let { signature ->
            SharedDocumentsArchive.archiveFile(context, signature, "Asetukset", signature.name)
        }
    }

    private fun inspectorsDirectory(): File =
        File(context.filesDir, "inspectors").apply { mkdirs() }

    fun listInspectors(): List<InspectorProfile> =
        inspectorsDirectory().listFiles { it.extension == "json" }
            ?.mapNotNull { file ->
                runCatching { json.decodeFromString(InspectorProfile.serializer(), file.readText()) }.getOrNull()
            }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()

    fun saveInspector(inspector: InspectorProfile) {
        require(inspector.name.isNotBlank()) { "Inspector name is required" }
        require(inspector.id.matches(Regex("[a-zA-Z0-9-]{1,64}")))
        val file = File(inspectorsDirectory(), "${inspector.id}.json")
        file.writeAtomicText(json.encodeToString(InspectorProfile.serializer(), inspector))
        SharedDocumentsArchive.archiveFile(context, file, "Asetukset/Tarkastajat", file.name, "application/json")
        File(inspector.signatureImagePath).takeIf { it.isFile }?.let { signature ->
            SharedDocumentsArchive.archiveFile(context, signature, "Asetukset/Tarkastajat/Allekirjoitukset", signature.name)
        }
    }

    fun deleteInspector(inspectorId: String) {
        require(inspectorId.matches(Regex("[a-zA-Z0-9-]{1,64}")))
        File(inspectorsDirectory(), "${inspectorId}.json").delete()
    }

    private fun customersDirectory(): File =
        File(context.filesDir, "customers").apply { mkdirs() }

    fun listCustomers(): List<CustomerProfile> =
        customersDirectory().listFiles { it.extension == "json" }
            ?.mapNotNull { file ->
                runCatching { json.decodeFromString(CustomerProfile.serializer(), file.readText()) }.getOrNull()
            }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()

    fun saveCustomer(customer: CustomerProfile) {
        val file = File(customersDirectory(), "${customer.id}.json")
        file.writeAtomicText(json.encodeToString(CustomerProfile.serializer(), customer))
        SharedDocumentsArchive.archiveFile(context, file, "Asetukset/Asiakkaat", file.name, "application/json")
    }

    fun deleteCustomer(customerId: String) {
        File(customersDirectory(), "${customerId}.json").delete()
        listSites().filter { it.customerId == customerId }.forEach { deleteSite(it.id) }
    }

    private fun sitesDirectory(): File =
        File(context.filesDir, "customer_sites").apply { mkdirs() }

    fun listSites(customerId: String? = null): List<CustomerSite> =
        sitesDirectory().listFiles { it.extension == "json" }
            ?.mapNotNull { file ->
                runCatching { json.decodeFromString(CustomerSite.serializer(), file.readText()) }.getOrNull()
            }
            ?.filter { customerId == null || it.customerId == customerId }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()

    fun saveSite(site: CustomerSite) {
        val file = File(sitesDirectory(), "${site.id}.json")
        file.writeAtomicText(json.encodeToString(CustomerSite.serializer(), site))
        SharedDocumentsArchive.archiveFile(context, file, "Asetukset/Kohteet", file.name, "application/json")
    }

    fun deleteSite(siteId: String) {
        File(sitesDirectory(), "${siteId}.json").delete()
    }

    fun importInspectorSignature(inspectorId: String, uri: Uri): File? = runCatching {
        require(inspectorId.matches(Regex("[a-zA-Z0-9-]{1,64}")))
        val directory = File(inspectorsDirectory(), "signatures").apply { mkdirs() }
        val extension = when (context.contentResolver.getType(uri)) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val destination = File(directory, "signature_${inspectorId}_${Instant.now().toEpochMilli()}.$extension")
        val input = context.contentResolver.openInputStream(uri) ?: error("Unable to open inspector signature")
        input.use { source -> destination.outputStream().use { output -> source.copyTo(output) } }
        destination
    }.getOrNull()

    fun importSettingsSignature(uri: Uri): File? = runCatching {
        val directory = File(context.filesDir, "settings").apply { mkdirs() }
        val extension = when (context.contentResolver.getType(uri)) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val destination = File(directory, "signature_${Instant.now().toEpochMilli()}.$extension")
        val input = context.contentResolver.openInputStream(uri) ?: error("Unable to open signature")
        input.use { source -> destination.outputStream().use { output -> source.copyTo(output) } }
        destination
    }.getOrNull()

    fun importSettingsLogo(uri: Uri): File? {
        return runCatching {
            val directory = File(context.filesDir, "settings").apply { mkdirs() }
            val extension = when (context.contentResolver.getType(uri)) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "jpg"
            }
            val destination = File(directory, "logo_${Instant.now().toEpochMilli()}.$extension")
            val input = context.contentResolver.openInputStream(uri) ?: error("Unable to open selected logo")
            input.use { source -> destination.outputStream().use { output -> source.copyTo(output) } }
            destination
        }.getOrNull()
    }

}

private fun File.writeAtomicText(value: String) {
    val atomic = android.util.AtomicFile(this)
    val stream = atomic.startWrite()
    try {
        stream.write(value.toByteArray(Charsets.UTF_8))
        atomic.finishWrite(stream)
    } catch (error: Throwable) {
        atomic.failWrite(stream)
        throw error
    }
}
