package fi.vladi.valvontapoytakirja.cloud
import java.io.*
import java.util.zip.*
internal fun writeBackupArchive(root: File, tempZip: File, maxEntries: Int = 20000, maxBytes: Long = 2L * 1024 * 1024 * 1024) {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(tempZip))).use { zip ->
                zip.putNextEntry(ZipEntry("TARKASTUSCLOUD_BACKUP_INFO.txt"))
                zip.write(("TARKASTUSCLOUD_BACKUP_V1" + "\n").toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                var entries = 1
                var bytes = 0L
                root.walkTopDown()
                    .filter { it.isFile && !it.name.endsWith(".pending") && !it.name.endsWith(".new") }
                    .forEach { file ->
                        val relative = file.relativeTo(root).invariantSeparatorsPath
                        require(relative.isNotBlank() && !relative.startsWith("../")) {
                            "Virheellinen tiedostopolku varmuuskopiossa."
                        }
                        require(++entries <= maxEntries)
                        require(file.canonicalPath.startsWith(root.canonicalPath + File.separator))
                        zip.putNextEntry(ZipEntry("files/$relative"))
                        FileInputStream(file).buffered().use { input ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                bytes += n
                                require(bytes <= maxBytes) { "Backup exceeds restore limit" }
                                zip.write(buffer,0,n)
                            }
                        }
                        zip.closeEntry()
                    }
            }
}
