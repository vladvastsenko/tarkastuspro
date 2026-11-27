package fi.vladi.valvontapoytakirja.model

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.UUID

@Serializable
data class SignatureStroke(
    val points: List<SignaturePoint>
)

@Serializable
data class SignaturePoint(
    val x: Float,
    val y: Float
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

/** Shared inspector identity. Never overwrite the report's own inspector snapshot. */
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


