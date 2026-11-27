package fi.vladi.valvontapoytakirja.data

import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class ReportAttachmentTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun resolvesForeignDevicePhotoAndLogo() {
        val root = folder.root
        for (name in listOf("photos/photo.jpg", "logo.png", "signature.png")) {
            val file = File(root, "reports/r1/$name")
            file.parentFile.mkdirs()
            file.writeText("attachment")
            assertEquals(file.canonicalPath, resolveReportAttachment(root, "r1", "/data/user/10/fi.vladi.tarkastuspro/files/reports/r1/$name"))
        }
    }
    @Test fun doesNotResolveOtherReportsOrTraversal() {
        val root = folder.root
        val file = File(root, "reports/r2/photo.jpg")
        file.parentFile.mkdirs()
        file.writeText("attachment")
        for (path in listOf("reports/r2/photo.jpg", "reports/r1/../r2/photo.jpg", "/unrelated/photo.jpg")) {
            assertEquals("", resolveReportAttachment(root, "r1", path))
        }
    }
    @Test fun preservesMissingPortableImage() {
        assertEquals("reports/r1/photos/a.jpg", resolveReportAttachment(folder.root, "r1", "/data/user/10/app/files/reports/r1/photos/a.jpg"))
        assertEquals("", resolveReportAttachment(folder.root, "r1", "reports/r1/private.json"))
    }
}
