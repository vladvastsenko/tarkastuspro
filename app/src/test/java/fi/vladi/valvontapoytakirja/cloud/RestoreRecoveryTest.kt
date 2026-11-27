package fi.vladi.valvontapoytakirja.cloud
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test
class RestoreRecoveryTest {
    private fun scenario(block: (File,File,File)->Unit) {
        val root = Files.createTempDirectory("restore-recovery").toFile()
        try { block(File(root,"files"),File(root,"tarkastus_restore_previous"),File(root,"tarkastus_restore_staging")) }
        finally { root.deleteRecursively() }
    }
    private fun seed(dir: File, value: String) { dir.mkdirs(); File(dir,"report").writeText(value) }
    @Test fun beforeSwap() = scenario { current, previous, staging ->
        seed(current,"old"); seed(staging,"new")
        recoverInterruptedRestore(current)
        assertEquals("old",File(current,"report").readText()); assertFalse(staging.exists())
    }
    @Test fun betweenRenames() = scenario { current, previous, staging ->
        seed(previous,"old"); seed(staging,"new")
        recoverInterruptedRestore(current)
        assertEquals("old",File(current,"report").readText()); assertFalse(staging.exists())
    }
    @Test fun afterSwapRetainsBoth() = scenario { current, previous, staging ->
        seed(previous,"old"); seed(current,"new")
        assertThrows(IllegalStateException::class.java) { recoverInterruptedRestore(current) }
        assertEquals("old",File(previous,"report").readText()); assertEquals("new",File(current,"report").readText())
    }
    @Test fun missingOriginalRetainsStaging() = scenario { current, previous, staging ->
        seed(staging,"new")
        assertThrows(IllegalStateException::class.java) { recoverInterruptedRestore(current) }
        assertTrue(staging.exists())
    }
    @Test fun choiceKeepsBothCopies() {
        for (original in listOf(true,false)) scenario { current, previous, staging ->
            seed(current,"new"); seed(previous,"old")
            val retained = resolveRestoreConflict(current,original)
            assertEquals(if(original) "old" else "new",File(current,"report").readText())
            assertEquals(if(original) "new" else "old",File(retained,"report").readText())
            recoverInterruptedRestore(current)
            assertTrue(retained.exists())
        }
    }
}
