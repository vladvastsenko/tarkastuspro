package fi.vladi.valvontapoytakirja.cloud

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Local-first backup of the complete app-private files directory.
 * The archive is portable between installs of the same Tarkastus application id.
 */
object CloudBackupManager {
    data class BackupFile(val uri: Uri, val name: String, val sizeBytes: Long, val lastModified: Long)

    fun listBackups(context: Context, treeUri: Uri): List<BackupFile> {
        fi.vladi.valvontapoytakirja.PremiumAccess.requireCloud()
        val folder = DocumentFile.fromTreeUri(context, treeUri)
            ?: error("Valittua Google Drive -kansiota ei voi avata.")
        check(folder.isDirectory && folder.canRead()) { "Valitun Google Drive -kansion sisältöä ei voi lukea." }
        return folder.listFiles()
            .filter { it.isFile && it.name?.startsWith("TarkastusCloud_backup_") == true && it.name?.endsWith(".zip") == true }
            .map { BackupFile(it.uri, it.name.orEmpty(), it.length(), it.lastModified()) }
            .sortedByDescending { backupTimestamp(it.name) ?: it.lastModified }
    }

    private fun backupTimestamp(name: String): Long? {
        val stamp = name.removePrefix("TarkastusCloud_backup_").removeSuffix(".zip")
        return listOf("yyyyMMdd_HHmmss_SSS", "yyyyMMdd_HHmmss").firstNotNullOfOrNull { pattern ->
            runCatching { SimpleDateFormat(pattern, Locale.US).parse(stamp)?.time }.getOrNull()
        }
    }
    private const val MANIFEST = "TARKASTUSCLOUD_BACKUP_INFO.txt"
    private const val MANIFEST_CONTENT = "TARKASTUSCLOUD_BACKUP_V1"
    private const val MAX_ENTRIES = 20_000
    private const val MAX_UNPACKED_BYTES = 2L * 1024L * 1024L * 1024L

    private fun writeArchive(context: Context, tempZip: File) {
            fi.vladi.valvontapoytakirja.CustomStore.withStorageLock {
            writeBackupArchive(context.filesDir, tempZip, MAX_ENTRIES, MAX_UNPACKED_BYTES)

            }

    }

    fun retainedCopies(context: Context): List<File> =
        File(context.applicationInfo.dataDir).listFiles()?.filter {
            it.isDirectory && it.name.matches(Regex("tarkastus_retained_[0-9a-f-]{36}")) &&
                it.canonicalFile.parentFile == File(context.applicationInfo.dataDir).canonicalFile
        }?.sortedByDescending { it.lastModified() }.orEmpty()

    fun exportRetainedCopy(context: Context, name: String, uri: Uri) {
        val root = retainedCopies(context).firstOrNull { it.name == name } ?: error("Copy not found")
        val temp = File.createTempFile("retained-", ".zip", context.cacheDir)
        try {
            writeBackupArchive(root, temp, MAX_ENTRIES, MAX_UNPACKED_BYTES)
            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                temp.inputStream().use { it.copyTo(out) }
            } ?: error("Cannot open destination")
        } finally { temp.delete() }
    }

    fun backupToDocument(context: Context, uri: Uri) {
        val temp = File.createTempFile("local-backup-", ".zip", context.cacheDir)
        try {
            writeArchive(context, temp)
            context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                temp.inputStream().buffered().use { it.copyTo(output) }
            } ?: error("Cannot write backup")
        } finally { temp.delete() }
    }

    fun backupToFolder(context: Context, treeUri: Uri): String {
        fi.vladi.valvontapoytakirja.PremiumAccess.requireCloud()
        val folder = DocumentFile.fromTreeUri(context, treeUri)
            ?: error("Valittua Google Drive -kansiota ei voi avata.")
        check(folder.isDirectory && folder.canWrite()) {
            "Valittuun kansioon ei voi tallentaa."
        }

        val tempZip = File(context.cacheDir, "tarkastuscloud_backup_${System.currentTimeMillis()}.zip")
        try {
            writeArchive(context, tempZip)
            fi.vladi.valvontapoytakirja.PremiumAccess.requireCloud()
            val displayName = "TarkastusCloud_backup_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())}.zip"
            check(folder.findFile(displayName) == null) { "Backup already exists" }
            val target = folder.createFile("application/zip", displayName)
                ?: error("Varmuuskopiotiedostoa ei voitu luoda Google Driveen.")
            try {
                context.contentResolver.openOutputStream(target.uri, "w")?.use { output ->
                    tempZip.inputStream().buffered().use { input -> input.copyTo(output) }
                } ?: error("Google Drive -tiedostoa ei voitu kirjoittaa.")
            } catch (error: Throwable) {
                target.delete()
                throw error
            }
            val pdfCount = copyPdfExportsToDrive(context, folder)
            return "$displayName; PDF-tiedostoja: $pdfCount"
        } finally {
            tempZip.delete()
        }
    }

    private fun copyPdfExportsToDrive(context: Context, destinationRoot: DocumentFile): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val basePath = "${Environment.DIRECTORY_DOCUMENTS}/TarkastusCloud/"
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND ${MediaStore.MediaColumns.MIME_TYPE} = ?"
        val args = arrayOf("${basePath}Raportit/PDF/%", "application/pdf")
        var copied = 0
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.RELATIVE_PATH),
            selection,
            args,
            null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            while (cursor.moveToNext()) {
                fi.vladi.valvontapoytakirja.PremiumAccess.requireCloud()
                val displayName = cursor.getString(nameColumn) ?: continue
                val relativePath = cursor.getString(pathColumn) ?: continue
                val subPath = relativePath.removePrefix(basePath).trim('/')
                if (subPath.isBlank() || subPath.split('/').any { it == ".." || it == "." || it.isBlank() }) continue
                var folder = destinationRoot
                subPath.split('/').forEach { segment ->
                    folder = folder.findFile(segment) ?: folder.createDirectory(segment) ?: error("Drive-kansiota ei voitu luoda.")
                }
                val existing = folder.findFile(displayName)
                val temporaryName = if (existing == null) displayName else "pending-${java.util.UUID.randomUUID()}.pdf"
                val target = folder.createFile("application/pdf", temporaryName)
                    ?: error("PDF-tiedostoa ei voitu luoda Google Driveen.")
                val sourceUri = android.content.ContentUris.withAppendedId(collection, cursor.getLong(idColumn))
                try {
                    context.contentResolver.openInputStream(sourceUri)?.use { input ->
                        context.contentResolver.openOutputStream(target.uri, "w")?.use { output ->
                            input.copyTo(output)
                        } ?: error("PDF-tiedostoa ei voitu kirjoittaa Driveen.")
                    } ?: error("PDF-tiedostoa ei voitu lukea.")
                    if (existing != null) {
                        val oldName = "previous-${java.util.UUID.randomUUID()}.pdf"
                        check(existing.renameTo(oldName)) { "Cannot preserve previous PDF" }
                        if (!target.renameTo(displayName)) {
                            existing.renameTo(displayName)
                            error("Cannot publish PDF; previous copy retained")
                        }
                        existing.delete()
                    }
                    copied++
                } catch (error: Throwable) {
                    target.delete()
                    throw error
                }
            }
        }
        return copied
    }

    fun restoreCloudFromUri(context: Context, archiveUri: Uri): String {
        fi.vladi.valvontapoytakirja.PremiumAccess.requireCloud()
        return restoreFromUri(context, archiveUri)
    }

    fun restoreFromUri(context: Context, archiveUri: Uri): String {
        val original = File(context.applicationInfo.dataDir, "files")
        recoverInterruptedRestore(original)
        // Stage beside filesDir so the final rename stays on the same filesystem.
        val staging = File(context.filesDir.parentFile, "tarkastus_restore_staging").apply {
            deleteRecursively()
            check(mkdirs()) { "Palautuksen väliaikaista kansiota ei voitu luoda." }
        }
        try {
            val input = context.contentResolver.openInputStream(archiveUri)
                ?: error("Cannot open backup")
            extractBackup(input, staging, MAX_ENTRIES, MAX_UNPACKED_BYTES)
            relocateBackupPaths(staging, original)
            // Keep the current local data until the complete archive has been extracted and validated.
            val current = context.filesDir
            val previous = File(current.parentFile, "tarkastus_restore_previous")
            // Never discard a previous recovery copy after an interrupted restore.
            if (previous.exists()) {
                if (!current.exists()) {
                    check(previous.renameTo(current)) { "Keskeytyneen palautuksen tietoja ei voitu palauttaa." }
                } else {
                    error("Aiemman palautuksen turvakopio on olemassa. Tarkista tiedot ennen uutta palautusta.")
                }
            }
            check(current.renameTo(previous)) { "Nykyisiä tietoja ei voitu suojata palautuksen ajaksi." }
            if (!staging.renameTo(current)) {
                check(previous.renameTo(current)) { "Palautus epäonnistui. Alkuperäiset tiedot ovat turvakansiossa." }
                error("Varmuuskopion käyttöönotto epäonnistui. Nykyiset tiedot palautettiin.")
            }
            val retained = File(current.parentFile, "tarkastus_retained_" + java.util.UUID.randomUUID())
            check(previous.renameTo(retained)) { "Restored data installed; previous data retained in recovery folder" }
            return "Varmuuskopio palautettiin onnistuneesti."
        } catch (error: Throwable) {
            staging.deleteRecursively()
            throw error
        }
    }
}
