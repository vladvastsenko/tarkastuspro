package fi.vladi.valvontapoytakirja.cloud
import java.io.*
import java.util.zip.*
import org.junit.*
import org.junit.rules.TemporaryFolder

class BackupExtractionTest {
    @get:Rule val folder = TemporaryFolder()
    private fun archive(vararg entries: Pair<String,String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { z -> entries.forEach { (name,value) -> z.putNextEntry(ZipEntry(name)); z.write(value.toByteArray()); z.closeEntry() } }
        return bytes.toByteArray()
    }
    private val manifest = "TARKASTUSCLOUD_BACKUP_INFO.txt" to "TARKASTUSCLOUD_BACKUP_V1"
    @Test fun validArchive() {
        val root = folder.newFolder()
        extractBackup(archive(manifest,"files/report.json" to "saved").inputStream(),root)
        Assert.assertEquals("saved",File(root,"report.json").readText())
    }
    @Test fun rejectsTraversal() {
        Assert.assertThrows(IllegalArgumentException::class.java) { extractBackup(archive(manifest,"files/../escape" to "bad").inputStream(),folder.newFolder()) }
    }
    @Test fun rejectsWrongManifest() {
        Assert.assertThrows(IllegalArgumentException::class.java) { extractBackup(archive(manifest.first to "wrong","files/report" to "data").inputStream(),folder.newFolder()) }
    }
    @Test fun rejectsOversizedContent() {
        Assert.assertThrows(IllegalArgumentException::class.java) { extractBackup(archive(manifest,"files/report" to "12345").inputStream(),folder.newFolder(),maxBytes=4) }
    }
    @Test fun rejectsNonZip() {
        Assert.assertThrows(IllegalStateException::class.java) { extractBackup("broken".byteInputStream(),folder.newFolder()) }
    }
}
