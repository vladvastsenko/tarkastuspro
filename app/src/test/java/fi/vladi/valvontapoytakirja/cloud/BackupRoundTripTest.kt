package fi.vladi.valvontapoytakirja.cloud
import fi.vladi.tarkastuspoytakirja.model.*
import fi.vladi.valvontapoytakirja.CustomDocument
import fi.vladi.valvontapoytakirja.CustomTemplate
import fi.vladi.valvontapoytakirja.CustomSection
import fi.vladi.valvontapoytakirja.CustomField
import fi.vladi.valvontapoytakirja.CustomKind
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.Assert.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackupRoundTripTest {
    @Test fun managerExportsRetainedCopyAndRestoresThroughContentResolver() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val root = context.filesDir
        val marker = File(root,"integration-marker.txt")
        val base = Files.createTempDirectory("manager-roundtrip").toFile()
        try {
            marker.writeText("original")
            val originalZip = File(base,"original.zip")
            CloudBackupManager.backupToDocument(context, android.net.Uri.fromFile(originalZip))
            marker.writeText("before restore")
            val existing = CloudBackupManager.retainedCopies(context).map { it.name }.toSet()
            CloudBackupManager.restoreFromUri(context, android.net.Uri.fromFile(originalZip))
            assertEquals("original",marker.readText())
            val retained = CloudBackupManager.retainedCopies(context).single { it.name !in existing }
            assertEquals("before restore",File(retained,marker.name).readText())
            val exported = File(base,"retained.zip")
            CloudBackupManager.exportRetainedCopy(context,retained.name,android.net.Uri.fromFile(exported))
            assertTrue(exported.length() > 0)
            CloudBackupManager.restoreFromUri(context,android.net.Uri.fromFile(exported))
            assertEquals("before restore",marker.readText())
            assertTrue(retained.isDirectory)
        } finally {
            base.deleteRecursively()
        }
    }

    @Test fun roundTripAllReportModelsAndImages() {
        val base = Files.createTempDirectory("backup-roundtrip").toFile()
        try {
            val root = File(base,"device-a/files").apply { mkdirs() }
            val restored = File(base,"device-b/files").apply { mkdirs() }
            fun file(path: String) = File(root,path).apply { parentFile?.mkdirs() }
            val jpeg = file("tarkastus_reports/test/photos/photo.jpg")
            val png = file("settings/signature.png")
            val image = Bitmap.createBitmap(32,24,Bitmap.Config.ARGB_8888)
            image.setPixel(5,5,0xff123456.toInt())
            jpeg.outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.JPEG,90,it)) }
            png.outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,it)) }
            val settings = AppSettings(inspectorName="Inspector",signatureImagePath=png.path,logoPath=png.path,headerText="Header")
            file("app_settings.json").writeText(Json.encodeToString(AppSettings.serializer(),settings))
            val reports = ReportTypes.ids.map { type ->
                InspectionReport(id=type,reportType=type,siteName="Site",signatureImagePath=png.path,logoPath=png.path,
                    observations=listOf(InspectionObservation(description="Observation",photos=listOf(ReportPhoto(filePath=jpeg.path,annotatedPath=png.path)))))
            }
            assertEquals(setOf("inspection", "defects", "handover", "warranty", "visit", "supervision", "custom", "freeform"), reports.map { it.reportType }.toSet())
            fun repository(dir: File): fi.vladi.tarkastuspoytakirja.data.ReportRepository {
                val baseContext = org.robolectric.RuntimeEnvironment.getApplication()
                val context = object : android.content.ContextWrapper(baseContext) {
                    override fun getFilesDir(): File = dir
                }
                return fi.vladi.tarkastuspoytakirja.data.ReportRepository(context)
            }
            val sourceRepository = repository(root)
            reports.forEach { sourceRepository.save(it) }
            assertEquals(reports.toSet(),sourceRepository.listReports().toSet())
            val field = CustomField(id="photo",kind=CustomKind.PHOTOS)
            val doc = CustomDocument(template=CustomTemplate(sections=listOf(CustomSection(fields=listOf(field)))),attachments=mapOf("photo" to listOf("12345678-1234-1234-1234-123456789abc.image")))
            jpeg.copyTo(file("custom_reports/12345678-1234-1234-1234-123456789abc.image"))
            fi.vladi.valvontapoytakirja.CustomStore(File(root,"custom_reports")).saveDocument(doc)
            val archive = File(base,"backup.zip")
            writeBackupArchive(root,archive)
            extractBackup(archive.inputStream(),restored)
            root.walkTopDown().filter { it.isFile }.forEach { original ->
                assertArrayEquals(original.readBytes(),File(restored,original.relativeTo(root).path).readBytes())
            }
            assertEquals(reports.toSet(),repository(restored).listReports().toSet())
            assertEquals(settings,Json.decodeFromString(AppSettings.serializer(),File(restored,"app_settings.json").readText()))
            assertEquals(doc,fi.vladi.valvontapoytakirja.CustomStore(File(restored,"custom_reports")).load().documents.single())
            relocateBackupPaths(restored,restored)
            // Simulate selecting the restored copy and exporting the retained version.
            val retained = File(base,"tarkastus_retained_" + java.util.UUID.randomUUID())
            assertTrue(root.renameTo(retained))
            val retainedZip = File(base,"retained.zip")
            writeBackupArchive(retained,retainedZip)
            val third = File(base,"device-c/files").apply { mkdirs() }
            extractBackup(retainedZip.inputStream(),third)
            relocateBackupPaths(third,third)
            assertTrue(retained.isDirectory)
            assertFalse(root.exists())
            for (destination in listOf(restored,third)) {
                val loaded = repository(destination).listReports()
                assertEquals(reports.size,loaded.size)
                assertEquals(reports.map { it.reportType }.toSet(), loaded.map { it.reportType }.toSet())
                fun checkImage(path: String) {
                    assertTrue(File(path).canonicalPath.startsWith(destination.canonicalPath + File.separator))
                    val bitmap = BitmapFactory.decodeFile(path) ?: error("Missing image: " + path)
                    assertEquals(32,bitmap.width)
                    assertEquals(24,bitmap.height)
                    bitmap.recycle()
                }
                loaded.forEach { report ->
                    checkImage(report.logoPath)
                    checkImage(report.signatureImagePath)
                    report.observations.flatMap { it.photos }.forEach {
                        checkImage(it.filePath); checkImage(it.annotatedPath)
                    }
                }
                val restoredSettings = repository(destination).loadSettings()
                checkImage(restoredSettings.logoPath)
                checkImage(restoredSettings.signatureImagePath)
                val store = fi.vladi.valvontapoytakirja.CustomStore(File(destination,"custom_reports"))
                val loadedDoc = store.load().documents.single()
                assertEquals(doc,loadedDoc)
                loadedDoc.attachments.values.flatten().forEach { checkImage(store.image(it).path) }
            }
        } finally { base.deleteRecursively() }
    }
}
