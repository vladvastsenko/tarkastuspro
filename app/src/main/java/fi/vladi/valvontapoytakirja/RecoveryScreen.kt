package fi.vladi.valvontapoytakirja
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import fi.vladi.tarkastuspoytakirja.AppLanguage
import fi.vladi.valvontapoytakirja.cloud.resolveRestoreConflict
import java.io.File
internal fun recoveryLabels(lang: String): List<String> = (when(lang) {
    "fi" -> "Tietojen palautus|Molemmat kopiot säilytetään. Valitse avattavat tiedot.|Avaa palautettu kopio|Avaa alkuperäinen kopio|Vahvista valinta|Toiminto epäonnistui. Kopiot säilytetty."
    "ru" -> "Восстановление данных|Обе копии сохранятся. Выберите данные для открытия.|Открыть восстановленную копию|Открыть исходную копию|Подтвердить выбор|Ошибка операции. Копии сохранены."
    "sv" -> "Dataåterställning|Båda kopiorna sparas. Välj data att öppna.|Öppna återställd kopia|Öppna originalkopian|Bekräfta valet|Åtgärden misslyckades. Kopiorna finns kvar."
    "nb" -> "Datagjenoppretting|Begge kopiene beholdes. Velg data som skal åpnes.|Åpne gjenopprettet kopi|Åpne originalkopien|Bekreft valget|Handlingen mislyktes. Kopiene er beholdt."
    "da" -> "Datagendannelse|Begge kopier bevares. Vælg data, der skal åbnes.|Åbn gendannet kopi|Åbn originalkopi|Bekræft valg|Handlingen mislykkedes. Kopierne er bevaret."
    "et" -> "Andmete taastamine|Mõlemad koopiad säilivad. Vali avatavad andmed.|Ava taastatud koopia|Ava algne koopia|Kinnita valik|Toiming ebaõnnestus. Koopiad säilivad."
    "lv" -> "Datu atjaunošana|Abas kopijas tiks saglabātas. Izvēlieties datus.|Atvērt atjaunoto kopiju|Atvērt sākotnējo kopiju|Apstiprināt izvēli|Darbība neizdevās. Kopijas saglabātas."
    "lt" -> "Duomenų atkūrimas|Abi kopijos bus išsaugotos. Pasirinkite duomenis.|Atverti atkurtą kopiją|Atverti pradinę kopiją|Patvirtinti pasirinkimą|Veiksmas nepavyko. Kopijos išsaugotos."
    "fr" -> "Récupération des données|Les deux copies seront conservées. Choisissez les données à ouvrir.|Ouvrir la copie restaurée|Ouvrir la copie originale|Confirmer le choix|Échec. Copies conservées."
    "es" -> "Recuperación de datos|Se conservarán ambas copias. Elija los datos que desea abrir.|Abrir copia restaurada|Abrir copia original|Confirmar selección|Error. Copias conservadas."
    "it" -> "Ripristino dati|Entrambe le copie saranno conservate. Scegliere i dati da aprire.|Apri copia ripristinata|Apri copia originale|Conferma scelta|Operazione non riuscita. Copie conservate."
    "de" -> "Datenwiederherstellung|Beide Kopien bleiben erhalten. Daten zum Öffnen auswählen.|Wiederhergestellte Kopie öffnen|Originalkopie öffnen|Auswahl bestätigen|Vorgang fehlgeschlagen. Kopien erhalten."
    "pl" -> "Odzyskiwanie danych|Obie kopie zostaną zachowane. Wybierz dane do otwarcia.|Otwórz przywróconą kopię|Otwórz oryginalną kopię|Potwierdź wybór|Operacja nieudana. Kopie zachowane."
    else -> "Data recovery|Both copies will be retained. Choose the data to open.|Open restored copy|Open original copy|Confirm choice|Operation failed. Copies retained."
}).split('|')
@Composable internal fun RecoveryScreen(current: File, onDone: () -> Unit) {
    val l = recoveryLabels(AppLanguage.selected(LocalContext.current))
    var choice by remember { mutableStateOf<Boolean?>(null) }
    var failed by remember { mutableStateOf(false) }
    val conflict = current.isDirectory && File(current.parentFile,"tarkastus_restore_previous").isDirectory
    Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text(l[0],style=MaterialTheme.typography.headlineMedium)
        Text(l[1])
        if (conflict) {
            OutlinedButton(onClick={choice=false}) { Text(l[2]) }
            OutlinedButton(onClick={choice=true}) { Text(l[3]) }
        }
        if (failed || !conflict) Text(l[5])
    }
    choice?.let { original ->
        AlertDialog(onDismissRequest={choice=null},title={Text(l[4])},text={Text(if(original) l[3] else l[2])},
            confirmButton={TextButton(onClick={
                choice=null
                if(runCatching { resolveRestoreConflict(current,original) }.isSuccess) onDone() else failed=true
            }) { Text(l[4]) }},dismissButton={TextButton(onClick={choice=null}) { Text(androidx.compose.ui.res.stringResource(R.string.cancel)) }})
    }
}
