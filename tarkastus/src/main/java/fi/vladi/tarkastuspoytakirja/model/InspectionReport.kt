package fi.vladi.tarkastuspoytakirja.model

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.UUID

@Serializable
data class InspectionReport(
    val id: String = UUID.randomUUID().toString(),
    val createdAt: String = LocalDate.now().toString(),
    val folderPath: String = "",
    val pdfLanguage: String = "",
    val customerId: String = "",
    val siteId: String = "",
    val customerName: String = "",
    val customerBusinessId: String = "",
    val customerAddress: String = "",
    val customerPostalCode: String = "",
    val customerCity: String = "",
    val siteName: String = "",
    val headerText: String = "",
    val settingsSnapshotCreated: Boolean = false,
    val templateName: String = "",
    val siteAddress: String = "",
    val clientOrRepresentative: String = "",
    val clientPhone: String = "",
    val clientEmail: String = "",
    val purposeAndScope: String = "",
    val generalObservations: String = "",
    val customFieldsTitle: String = "OMAT LISÄKENTÄT",
    val customFields: List<CustomReportField> = emptyList(),
    val observations: List<InspectionObservation> = emptyList(),
    val summary: String = "",
    val inspectorName: String = "",
    val inspectorCertificateNumber: String = "",
    val inspectorPhone: String = "",
    val inspectorEmail: String = "",
    val inspectionDate: String = LocalDate.now().toString(),
    val signatureStrokes: List<SignatureStroke> = emptyList(),
    val signatureImagePath: String = "",
    val logoPath: String = "",
    val footerText: String = "",
    val reportType: String = "inspection",
    val documentTitle: String = "",
    val checklist: List<ChecklistItem> = emptyList(),
    val measurements: List<MeasurementItem> = emptyList(),
    val useSharedSettings: Boolean = true
)

@Serializable
data class MeasurementItem(
    val id: String = UUID.randomUUID().toString(),
    val parameter: String = "",
    val value: String = "",
    val unit: String = "",
    val permittedValue: String = "",
    val result: String = ""
) {
    fun hasContent(): Boolean = listOf(parameter, value, unit, permittedValue, result).any { it.isNotBlank() }
    fun asTemplate(): MeasurementItem = copy(value = "", result = "")
}

@Serializable
data class ChecklistItem(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "",
    val result: String = ""
)

@Serializable
data class CustomReportField(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "",
    val value: String = "",
    val sourceDefinitionId: String = ""
)

@Serializable
data class InspectionObservation(
    val id: String = UUID.randomUUID().toString(),
    val location: String = "",
    val description: String = "",
    val recommendedAction: String = "",
    val photos: List<ReportPhoto> = emptyList(),
    val status: String = "",
    val priority: String = "",
    val responsiblePerson: String = "",
    val remediationDeadline: String = "",
    val reinspectionDate: String = ""
)

@Serializable
data class SignatureStroke(val points: List<SignaturePoint>)

@Serializable
data class SignaturePoint(val x: Float, val y: Float)

@Serializable
data class ReportPhoto(
    val id: String = UUID.randomUUID().toString(),
    val filePath: String,
    val caption: String = "",
    val annotatedPath: String = "",
    val category: String = ""
)

@Serializable
data class AppSettings(
    val logoPath: String = "",
    val headerText: String = "",
    val footerText: String = "",
    val inspectorName: String = "",
    val inspectorCertificateNumber: String = "",
    val inspectorPhone: String = "",
    val inspectorEmail: String = "",
    val signatureStrokes: List<SignatureStroke> = emptyList(),
    val signatureImagePath: String = ""
)

@Serializable
data class InspectorProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val certificateNumber: String = "",
    val phone: String = "",
    val email: String = "",
    val signatureStrokes: List<SignatureStroke> = emptyList(),
    val signatureImagePath: String = ""
)

@Serializable
data class CustomerProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val businessId: String = "",
    val address: String = "",
    val postalCode: String = "",
    val city: String = "",
    val contactPerson: String = "",
    val phone: String = "",
    val email: String = ""
)

@Serializable
data class CustomerSite(
    val id: String = UUID.randomUUID().toString(),
    val customerId: String,
    val name: String = "",
    val address: String = "",
    val fieldDefinitions: List<SiteFieldDefinition> = emptyList()
)

@Serializable
data class SiteFieldDefinition(
    val id: String = UUID.randomUUID().toString(),
    val label: String = ""
)
