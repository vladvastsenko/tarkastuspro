package fi.vladi.valvontapoytakirja
import android.net.Uri
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fi.vladi.tarkastuspoytakirja.AppLanguage
import fi.vladi.valvontapoytakirja.cloud.CloudBackupManager
import kotlinx.coroutines.*

internal fun localBackupLabels(language: String): List<String> = when(language) {
    "fi" -> listOf("Paikallinen varmuuskopio", "Tallenna ZIP", "Palauta ZIP", "Korvataanko nykyiset tiedot?", "Valmis", "Toiminto epäonnistui")
    "ru" -> listOf("Локальная резервная копия", "Сохранить ZIP", "Восстановить ZIP", "Заменить текущие данные приложения?", "Готово", "Ошибка операции")
    "sv" -> listOf("Lokal säkerhetskopia", "Spara ZIP", "Återställ ZIP", "Ersätta aktuella data?", "Klart", "Åtgärden misslyckades")
    "nb" -> listOf("Lokal sikkerhetskopi", "Lagre ZIP", "Gjenopprett ZIP", "Erstatte gjeldende data?", "Ferdig", "Operasjonen mislyktes")
    "da" -> listOf("Lokal sikkerhedskopi", "Gem ZIP", "Gendan ZIP", "Erstatte aktuelle data?", "Færdig", "Handlingen mislykkedes")
    "et" -> listOf("Kohalik varukoopia", "Salvesta ZIP", "Taasta ZIP", "Asendada praegused andmed?", "Valmis", "Toiming ebaõnnestus")
    "lv" -> listOf("Lokālā rezerves kopija", "Saglabāt ZIP", "Atjaunot ZIP", "Aizstāt pašreizējos datus?", "Pabeigts", "Darbība neizdevās")
    "lt" -> listOf("Vietinė atsarginė kopija", "Išsaugoti ZIP", "Atkurti ZIP", "Pakeisti dabartinius duomenis?", "Atlikta", "Veiksmas nepavyko")
    "fr" -> listOf("Sauvegarde locale", "Enregistrer ZIP", "Restaurer ZIP", "Remplacer les données actuelles ?", "Terminé", "Échec de l’opération")
    "es" -> listOf("Copia local", "Guardar ZIP", "Restaurar ZIP", "¿Reemplazar los datos actuales?", "Completado", "Error de operación")
    "it" -> listOf("Backup locale", "Salva ZIP", "Ripristina ZIP", "Sostituire i dati attuali?", "Completato", "Operazione non riuscita")
    "de" -> listOf("Lokale Sicherung", "ZIP speichern", "ZIP wiederherstellen", "Aktuelle Daten ersetzen?", "Fertig", "Vorgang fehlgeschlagen")
    "pl" -> listOf("Kopia lokalna", "Zapisz ZIP", "Przywróć ZIP", "Zastąpić bieżące dane?", "Gotowe", "Operacja nie powiodła się")
    else -> listOf("Local backup", "Save ZIP", "Restore ZIP", "Replace current app data?", "Completed", "Operation failed")
}
@Composable
internal fun LocalBackupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val l = localBackupLabels(AppLanguage.selected(context))
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var restore by remember { mutableStateOf<Uri?>(null) }
    fun operate(action: () -> Unit) {
        busy = true
        scope.launch {
            try { withContext(Dispatchers.IO) { action() }; status = l[4] }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { status = l[5] }
            finally { busy = false }
        }
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) operate { CloudBackupManager.backupToDocument(context, uri) }
    }
    val retained = remember { CloudBackupManager.retainedCopies(context) }
    var selectedCopy by rememberSaveable { mutableStateOf("") }
    val exportCopy = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null && selectedCopy.isNotBlank()) {
            val name = selectedCopy
            operate { CloudBackupManager.exportRetainedCopy(context,name,uri) }
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restore = uri }
    BackHandler { if (!busy) onBack() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(l[0], style = MaterialTheme.typography.headlineMedium)
        Button(onClick = { save.launch("TarkastusCloud_backup_${System.currentTimeMillis()}.zip") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(l[1]) }
        OutlinedButton(onClick = { open.launch(arrayOf("application/zip","application/octet-stream")) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(l[2]) }
        retained.forEach { copy ->
            OutlinedButton(onClick = {
                selectedCopy = copy.name
                exportCopy.launch("TarkastusCloud_${copy.name}.zip")
            }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(l[1] + " · " + recoveryLabels(AppLanguage.selected(context))[0] + " · " +
                    java.text.DateFormat.getDateTimeInstance().format(java.util.Date(copy.lastModified())) + " · " + copy.name.takeLast(8))
            }
        }
        if (busy) CircularProgressIndicator()
        Text(status)
        TextButton(onClick = onBack, enabled = !busy) { Text(stringResource(R.string.back)) }
    }
    restore?.let { uri ->
        AlertDialog(onDismissRequest = { restore = null }, text = { Text(l[3]) },
            confirmButton = { TextButton(onClick = { restore = null; operate { CloudBackupManager.restoreFromUri(context, uri) } }) { Text(stringResource(R.string.confirm)) } },
            dismissButton = { TextButton(onClick = { restore = null }) { Text(stringResource(R.string.cancel)) } })
    }
}
