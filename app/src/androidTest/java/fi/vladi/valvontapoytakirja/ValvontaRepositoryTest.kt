package fi.vladi.valvontapoytakirja
import fi.vladi.valvontapoytakirja.data.ReportRepository
import fi.vladi.valvontapoytakirja.model.*
import fi.vladi.valvontapoytakirja.pdf.ReportPdfGenerator
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(androidx.test.ext.junit.runners.AndroidJUnit4::class)
class ValvontaRepositoryTest {
    @Test fun saveReloadExportAndDelete() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val repository = ReportRepository(context)
        val report = InspectionReport(siteAddress = "Test B44", pdfLanguage = "en", inspectionResult = "Hyväksytty")
        try {
            repository.save(report)
            assertEquals(report, repository.listReports().first { it.id == report.id })
            val pdf = ReportPdfGenerator.generate(context, report, AppSettings())
            assertEquals("%PDF-", pdf.inputStream().use { it.readNBytes(5).decodeToString() })
        } finally { repository.deleteReport(report.id) }
        assertFalse(repository.listReports().any { it.id == report.id })
    }
}
