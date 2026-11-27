package fi.vladi.valvontapoytakirja.cloud
import java.io.File
import java.nio.file.Files
import org.junit.Test
import org.junit.Assert.*
import kotlinx.serialization.json.*
class BackupPathsTest {
    @Test fun relocatesKnownPathsWithoutChangingUserText() {
        val root = Files.createTempDirectory("paths").toFile()
        try {
            val staged = File(root,"staging").apply { mkdirs() }
            val target = File(root,"files")
            val old = "/data/user/10/fi.vladi.tarkastuscloud/files/settings/signature.png"
            File(staged,"settings/signature.png").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1)) }
            val json = File(staged,"app_settings.json")
            json.writeText(JsonObject(mapOf("signatureImagePath" to JsonPrimitive(old),"headerText" to JsonPrimitive(old))).toString())
            relocateBackupPaths(staged,target)
            val result = Json.parseToJsonElement(json.readText()).jsonObject
            assertEquals(File(target,"settings/signature.png").path,result.getValue("signatureImagePath").jsonPrimitive.content)
            assertEquals(old,result.getValue("headerText").jsonPrimitive.content)
        } finally { root.deleteRecursively() }
    }
}
