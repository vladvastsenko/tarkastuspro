package fi.vladi.valvontapoytakirja.cloud
import java.io.*
import java.util.zip.ZipFile
import java.util.zip.CRC32

internal fun extractBackup(input: InputStream, staging: File, maxEntries: Int = 20000, maxBytes: Long = 2L * 1024 * 1024 * 1024) {
    val archive = File.createTempFile("restore-", ".zip", staging.parentFile)
    try {
        input.use { source ->
            archive.outputStream().buffered().use { out ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    val n = source.read(buffer)
                    if (n < 0) break
                    total += n
                    require(total <= maxBytes + 128L * 1024 * 1024) { "Archive too large" }
                    out.write(buffer, 0, n)
                }
            }
        }
        ZipFile(archive).use { zip ->
            require(zip.size() in 2..maxEntries)
            val seen = hashSetOf<String>()
            var manifest = false
            var total = 0L
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name.replace('\\', '/')
                require(seen.add(name) && !entry.isDirectory && name.split('/').none { it.isBlank() || it == "." || it == ".." })
                val marker = name == "TARKASTUSCLOUD_BACKUP_INFO.txt"
                require(marker || name.startsWith("files/"))
                val target = File(staging, name.removePrefix("files/")).canonicalFile
                require(target.path.startsWith(staging.canonicalPath + File.separator))
                val crc = CRC32()
                var size = 0L
                val bytes = ByteArrayOutputStream()
                val output = if (marker) bytes else {
                    target.parentFile?.mkdirs()
                    target.outputStream().buffered()
                }
                output.use { out ->
                    zip.getInputStream(entry).use { source ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val n = source.read(buffer)
                            if (n < 0) break
                            size += n
                            if (marker) require(size <= 127) else {
                                total += n
                                require(total <= maxBytes)
                            }
                            crc.update(buffer,0,n)
                            out.write(buffer,0,n)
                        }
                    }
                }
                require(size == entry.size && crc.value == entry.crc) { "ZIP checksum mismatch" }
                if (marker) {
                    require(bytes.toString("UTF-8").trim() == "TARKASTUSCLOUD_BACKUP_V1")
                    manifest = true
                }
            }
            check(manifest)
        }
    } catch (e: java.util.zip.ZipException) {
        throw IllegalStateException("Invalid ZIP archive", e)
    } finally { archive.delete() }
}
