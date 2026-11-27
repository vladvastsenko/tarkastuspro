package fi.vladi.tarkastuspoytakirja

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path as ComposePath
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import fi.vladi.tarkastuspoytakirja.data.ReportRepository
import fi.vladi.tarkastuspoytakirja.model.AppSettings
import fi.vladi.tarkastuspoytakirja.model.CustomerProfile
import fi.vladi.tarkastuspoytakirja.model.CustomerSite
import fi.vladi.tarkastuspoytakirja.model.CustomReportField
import fi.vladi.tarkastuspoytakirja.model.InspectionObservation
import fi.vladi.tarkastuspoytakirja.model.InspectorProfile
import fi.vladi.tarkastuspoytakirja.model.ReportTypes
import fi.vladi.tarkastuspoytakirja.model.FollowUpLabels
import fi.vladi.tarkastuspoytakirja.model.PhotoCategories
import fi.vladi.tarkastuspoytakirja.model.ChecklistItem
import fi.vladi.tarkastuspoytakirja.model.ChecklistLabels
import fi.vladi.tarkastuspoytakirja.model.MeasurementItem
import fi.vladi.tarkastuspoytakirja.model.MeasurementLabels
import fi.vladi.tarkastuspoytakirja.model.InspectionReport
import fi.vladi.tarkastuspoytakirja.model.ReportPhoto
import fi.vladi.tarkastuspoytakirja.pdf.ReportPdfGenerator
import fi.vladi.tarkastuspoytakirja.storage.SharedDocumentsArchive
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = TarkastusBlueColors) {
                Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding(), color = MaterialTheme.colorScheme.background) {
                    TarkastusApp()
                }
            }
        }
    }
}

@Composable
fun TarkastusApp(onManagement: (() -> Unit)? = null, homeFooter: @Composable () -> Unit = {}) {
    val context = LocalContext.current
    val repository = remember { ReportRepository(context) }
    LaunchedEffect(repository) {
        withContext(Dispatchers.IO) { repository.archiveExistingData() }
    }
    var reports by remember { mutableStateOf(repository.listReports()) }
    var templates by remember { mutableStateOf(repository.listTemplates()) }
    var appSettings by remember { mutableStateOf(repository.loadSettings()) }
    var customers by remember { mutableStateOf(repository.listCustomers()) }
    var inspectors by remember { mutableStateOf(repository.listInspectors()) }
    var sites by remember { mutableStateOf(repository.listSites()) }
    var currentReport by remember { mutableStateOf<InspectionReport?>(null) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    var pendingCameraObservationId by remember { mutableStateOf<String?>(null) }
    var pendingGalleryObservationId by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    var pendingSaveJob by remember { mutableStateOf<Job?>(null) }
    var pendingGalleryJob by remember { mutableStateOf<Job?>(null) }
    var pendingSettingsSaveJob by remember { mutableStateOf<Job?>(null) }

    fun saveReport(report: InspectionReport) {
        val isNewReport = currentReport == null
        currentReport = report
        if (isNewReport) reports = listOf(report) + reports.filterNot { it.id == report.id }
        pendingSaveJob?.cancel()
        pendingSaveJob = coroutineScope.launch {
            delay(400)
            withContext(Dispatchers.IO) { repository.save(report) }
        }
    }
    fun saveAppSettings(settings: AppSettings) {
        appSettings = settings
        pendingSettingsSaveJob?.cancel()
        pendingSettingsSaveJob = coroutineScope.launch {
            delay(300)
            withContext(Dispatchers.IO) { repository.saveSettings(settings) }
        }
    }

    fun applySettingsSnapshot(report: InspectionReport): InspectionReport = if (report.reportType == "freeform" && !report.useSharedSettings) report.copy(settingsSnapshotCreated = true, logoPath = "", headerText = "", footerText = "", inspectorName = "", inspectorCertificateNumber = "", inspectorPhone = "", inspectorEmail = "", signatureStrokes = emptyList(), signatureImagePath = "") else report.copy(
        inspectorName = inspectors.singleOrNull()?.name ?: appSettings.inspectorName,
        inspectorCertificateNumber = inspectors.singleOrNull()?.certificateNumber ?: appSettings.inspectorCertificateNumber,
        inspectorPhone = inspectors.singleOrNull()?.phone ?: appSettings.inspectorPhone,
        inspectorEmail = inspectors.singleOrNull()?.email ?: appSettings.inspectorEmail,
        signatureStrokes = inspectors.singleOrNull()?.signatureStrokes?.map { stroke ->
            fi.vladi.tarkastuspoytakirja.model.SignatureStroke(stroke.points)
        } ?: appSettings.signatureStrokes,
        signatureImagePath = inspectors.singleOrNull()?.signatureImagePath ?: appSettings.signatureImagePath,
        logoPath = appSettings.logoPath,
        headerText = appSettings.headerText,
        footerText = appSettings.footerText,
        settingsSnapshotCreated = true
    )

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingCameraFile
        val observationId = pendingCameraObservationId
        val report = currentReport
        if (success && file != null && observationId != null && report != null && file.exists() && file.length() > 0) {
            val updated = report.copy(observations = report.observations.map { observation ->
                if (observation.id == observationId) {
                    observation.copy(photos = observation.photos + ReportPhoto(filePath = file.absolutePath))
                } else observation
            })
            saveReport(updated)
        } else if (file != null) {
            file.delete()
        }
        pendingCameraFile = null
        pendingCameraObservationId = null
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(12)
    ) { uris ->
        val report = currentReport
        val observationId = pendingGalleryObservationId
        if (report != null && observationId != null && uris.isNotEmpty()) {
            pendingGalleryJob = coroutineScope.launch {
                val imported = withContext(Dispatchers.IO) {
                    uris.mapNotNull { uri ->
                        repository.importPhoto(report.id, uri)?.let { ReportPhoto(filePath = it.absolutePath) }
                    }
                }
                val latest = currentReport
                if (imported.isNotEmpty() && latest != null && latest.id == report.id) {
                    saveReport(latest.copy(observations = latest.observations.map { observation ->
                        if (observation.id == observationId) {
                            observation.copy(photos = observation.photos + imported)
                        } else observation
                    }))
                }
                pendingGalleryObservationId = null
            }
        } else {
            pendingGalleryObservationId = null
        }
    }

    val active = currentReport

    if (active == null) {
        HomeScreen(
            reports = reports,
            templates = templates,
            onCreateFromTemplate = { template ->
                saveReport(applySettingsSnapshot(template.copy(
                    id = java.util.UUID.randomUUID().toString(),
                    createdAt = LocalDate.now().toString(),
                    templateName = "",
                    checklist = template.checklist.map { it.copy(result = "") },
                    measurements = template.measurements.map { it.asTemplate() },
                    observations = if (template.observations.isEmpty()) listOf(InspectionObservation())
                        else template.observations.map { it.copy(photos = emptyList(), status = "", remediationDeadline = "", reinspectionDate = "") }
                )))
            },
            onCreate = { type ->
                saveReport(applySettingsSnapshot(InspectionReport(
                    reportType = type,
                    customFields = if (type == "freeform") listOf(CustomReportField()) else emptyList(),
                    customFieldsTitle = if (type == "freeform") "" else "OMAT LISÄKENTÄT",
                    inspectionDate = LocalDate.now().toString(),
                    observations = listOf(InspectionObservation())
                )))
            },
            onOpen = { currentReport = it },
            onDelete = { report ->
                repository.deleteReport(report.id)
                reports = repository.listReports()
            },
            onBackToDocumentSelection = { (context as? Activity)?.finish() },
            onManagement = onManagement,
            homeFooter = homeFooter
        )
    } else {
        EditorScreen(
            report = active,
            inspectors = inspectors,
            customers = customers,
            sites = sites,
            onChange = ::saveReport,
            onBack = {
                coroutineScope.launch {
                    pendingGalleryJob?.join()
                    pendingSaveJob?.cancelAndJoin()
                    val latest = currentReport ?: active
                    withContext(Dispatchers.IO) { repository.save(latest) }
                    reports = withContext(Dispatchers.IO) { repository.listReports() }
                    currentReport = null
                }
            },
            onCamera = { observationId ->
                val file = repository.createCameraPhotoFile(active.id)
                pendingCameraFile = file
                pendingCameraObservationId = observationId
                cameraLauncher.launch(repository.cameraUri(file))
            },
            onGallery = { observationId ->
                pendingGalleryObservationId = observationId
                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onSaveTemplate = { name ->
                coroutineScope.launch {
                    val latest = currentReport ?: active
                    withContext(Dispatchers.IO) { repository.saveTemplate(latest, name) }
                    templates = withContext(Dispatchers.IO) { repository.listTemplates() }
                }
            },
            onPreview = {
                coroutineScope.launch {
                    pendingGalleryJob?.join()
                    val latest = currentReport ?: active
                    val pdf = withContext(Dispatchers.IO) {
                        ReportPdfGenerator.generate(context, latest, appSettings)
                    }
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdf)
                    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/pdf")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    try {
                        context.startActivity(viewIntent)
                    } catch (_: android.content.ActivityNotFoundException) {
                        android.widget.Toast.makeText(context, context.getString(R.string.ui_preview_unavailable), android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            },
            onExport = {
                coroutineScope.launch {
                    pendingGalleryJob?.join()
                    pendingSaveJob?.cancel()
                    val latest = currentReport ?: active
                    withContext(Dispatchers.IO) { repository.save(latest) }
                    val pdf = withContext(Dispatchers.IO) {
                        ReportPdfGenerator.generate(context, latest, appSettings)
                    }
                    withContext(Dispatchers.IO) {
                        SharedDocumentsArchive.archiveFile(
                            context,
                            pdf,
                            "Raportit/PDF/Tarkastuspoytakirja",
                            pdf.name,
                            "application/pdf"
                        )
                    }
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdf)
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.share_report)))
                }
            },
            onDeletePhoto = { observationId, photo ->
                repository.deletePhoto(photo.filePath)
                if (photo.annotatedPath.isNotBlank()) repository.deletePhoto(photo.annotatedPath)
                val latest = currentReport ?: active
                saveReport(latest.copy(observations = latest.observations.map { observation ->
                    if (observation.id == observationId) {
                        observation.copy(photos = observation.photos.filterNot { it.id == photo.id })
                    } else observation
                }))
            },
            onDeleteObservation = { observation ->
                observation.photos.forEach { photo ->
                    repository.deletePhoto(photo.filePath)
                    if (photo.annotatedPath.isNotBlank()) repository.deletePhoto(photo.annotatedPath)
                }
                val latest = currentReport ?: active
                saveReport(latest.copy(observations = latest.observations.filterNot { it.id == observation.id }))
            }
        )
    }
}

@Composable
private fun HomeScreen(
    reports: List<InspectionReport>,
    templates: List<InspectionReport>,
    onCreateFromTemplate: (InspectionReport) -> Unit,
    onCreate: (String) -> Unit,
    onOpen: (InspectionReport) -> Unit,
    onDelete: (InspectionReport) -> Unit,
    onBackToDocumentSelection: () -> Unit,
    onManagement: (() -> Unit)? = null,
    homeFooter: @Composable () -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (onManagement != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tarkastus Cloud", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = onManagement) { Text("⚙", style = MaterialTheme.typography.headlineMedium) }
            }
        } else {
        Text(stringResource(R.string.report_title_localized), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.loc3_general_intro))
        TextButton(onClick = onBackToDocumentSelection) { Text(stringResource(R.string.ui_back_reports)) }
        }
        var typeMenu by remember { mutableStateOf(false) }
        val language = AppLanguage.selected(LocalContext.current)
        Box {
            Button(onClick = { typeMenu = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ui_new_report) + " ▾")
            }
            DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                ReportTypes.ids.forEach { type ->
                    DropdownMenuItem(text = { Text(ReportTypes.title(type, language)) }, onClick = {
                        typeMenu = false
                        onCreate(type)
                    })
                }
            }
        }
        if (templates.isNotEmpty()) {
            Text(stringResource(R.string.loc_templates), style = MaterialTheme.typography.titleLarge)
            templates.forEach { template ->
                OutlinedButton(onClick = { onCreateFromTemplate(template) }, modifier = Modifier.fillMaxWidth()) {
                    Text(template.templateName + " • " + template.siteAddress)
                }
            }
        }
        Text(stringResource(R.string.loc_saved), style = MaterialTheme.typography.titleLarge)
        if (reports.isEmpty()) {
            Text(stringResource(R.string.ui_no_reports))
        } else {
            reports.forEach { report ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(report) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(ReportTypes.title(report, language), fontWeight = FontWeight.Bold)
                        Text(report.siteAddress.ifBlank { stringResource(R.string.new_report_fallback) }, fontWeight = FontWeight.SemiBold)
                        if (report.folderPath.isNotBlank()) Text(stringResource(R.string.loc3_folder) + report.folderPath, style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.loc4_inspection_date_prefix, report.inspectionDate.ifBlank { "—" }))
                        Text(stringResource(R.string.loc4_observation_count, report.observations.size), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { onDelete(report) }) { Text(stringResource(R.string.ui_delete)) }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.loc4_local_save_tarkastus), style = MaterialTheme.typography.bodySmall)
        homeFooter()
    }
}

@Composable
private fun EditorScreen(
    report: InspectionReport,
    inspectors: List<InspectorProfile>,
    customers: List<CustomerProfile>,
    sites: List<CustomerSite>,
    onChange: (InspectionReport) -> Unit,
    onBack: () -> Unit,
    onCamera: (String) -> Unit,
    onGallery: (String) -> Unit,
    onSaveTemplate: (String) -> Unit,
    onExport: () -> Unit,
    onPreview: () -> Unit,
    onDeletePhoto: (String, ReportPhoto) -> Unit,
    onDeleteObservation: (InspectionObservation) -> Unit
) {
    var photoBeingEdited by remember(report.id) { mutableStateOf<ReportPhoto?>(null) }
    if (photoBeingEdited != null) {
        val photo = photoBeingEdited!!
        PhotoMarkupEditor(
            photo = photo,
            onCancel = { photoBeingEdited = null },
            onSave = { markedPath ->
                if (photo.annotatedPath.isNotBlank()) File(photo.annotatedPath).delete()
                onChange(report.copy(observations = report.observations.map { observation ->
                    observation.copy(photos = observation.photos.map { item ->
                        if (item.id == photo.id) item.copy(annotatedPath = markedPath) else item
                    })
                }))
                photoBeingEdited = null
            }
        )
        return
    }
    val defaultTemplateName = stringResource(R.string.new_template_fallback)
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        var reportLanguageMenu by remember(report.id) { mutableStateOf(false) }
        val pdfContext = LocalContext.current
        val reportLanguage = report.pdfLanguage.ifBlank {
            AppLanguage.selected(pdfContext)
        }
        if (inspectors.size > 1 && (report.reportType != "freeform" || report.useSharedSettings)) {
            var inspectorMenuExpanded by remember(report.id) { mutableStateOf(false) }
            Text(stringResource(R.string.inspector_heading_localized), fontWeight = FontWeight.SemiBold)
            Box {
                OutlinedButton(onClick = { inspectorMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(report.inspectorName.ifBlank { stringResource(R.string.select_inspector_localized) } + " ▾")
                }
                DropdownMenu(expanded = inspectorMenuExpanded, onDismissRequest = { inspectorMenuExpanded = false }) {
                    inspectors.forEach { inspector ->
                        DropdownMenuItem(text = { Text(inspector.name) }, onClick = {
                            inspectorMenuExpanded = false
                            onChange(report.copy(
                                inspectorName = inspector.name,
                                inspectorCertificateNumber = inspector.certificateNumber,
                                inspectorPhone = inspector.phone,
                                inspectorEmail = inspector.email,
                                signatureStrokes = inspector.signatureStrokes,
                                signatureImagePath = inspector.signatureImagePath
                            ))
                        })
                    }
                }
            }
        }
        Text(stringResource(R.string.report_pdf_language), fontWeight = FontWeight.SemiBold)
        Box {
            OutlinedButton(onClick = { reportLanguageMenu = true }, modifier = Modifier.fillMaxWidth()) {
                Text(AppLanguage.label(reportLanguage) + " ▾")
            }
            DropdownMenu(expanded = reportLanguageMenu, onDismissRequest = { reportLanguageMenu = false }) {
                AppLanguage.options.forEach { (code, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = {
                        reportLanguageMenu = false
                        onChange(report.copy(pdfLanguage = code))
                    })
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.ui_back)) }
            Spacer(Modifier.weight(1f))
            Button(onClick = onExport) { Text(stringResource(R.string.ui_create_pdf)) }
        }
        Text(ReportTypes.title(report, reportLanguage), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Field(stringResource(R.string.pro_document_title), report.documentTitle) {
            onChange(report.copy(documentTitle = it))
        }
        Text(stringResource(R.string.loc_fill), style = MaterialTheme.typography.bodySmall)

        val freeform = report.reportType == "freeform"
        if (freeform) {
            val settingsLabel = when (AppLanguage.selected(pdfContext)) {
                "ru" -> "Использовать настройки"; "fi" -> "Käytä asetuksia"; "sv" -> "Använd inställningar"; "nb" -> "Bruk innstillinger"; "da" -> "Brug indstillinger"; "et" -> "Kasuta seadeid"; "lv" -> "Izmantot iestatījumus"; "lt" -> "Naudoti nustatymus"; "fr" -> "Utiliser les paramètres"; "es" -> "Usar ajustes"; "it" -> "Usa impostazioni"; "de" -> "Einstellungen verwenden"; "pl" -> "Użyj ustawień"; else -> "Use settings"
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(settingsLabel, Modifier.weight(1f))
                androidx.compose.material3.Switch(checked = report.useSharedSettings, onCheckedChange = { enabled ->
                    onChange(report.copy(useSharedSettings = enabled))
                })
            }
        }
        if (!freeform || report.useSharedSettings) {
        SectionTitle(stringResource(R.string.loc_site_parties))
        Field(stringResource(R.string.loc4_folder_path), report.folderPath) {
            onChange(report.copy(folderPath = it.trim('/')))
        }
        ChoiceField(
            label = stringResource(R.string.audit_saved_customer),
            selectedId = report.customerId,
            options = customers.map { it.id to it.name },
            onSelected = { id ->
                val customer = customers.firstOrNull { it.id == id }
                onChange(report.copy(
                    customerId = id,
                    siteId = "",
                    customerName = customer?.name.orEmpty(),
                    customerBusinessId = customer?.businessId.orEmpty(),
                    customerAddress = customer?.address.orEmpty(),
                    customerPostalCode = customer?.postalCode.orEmpty(),
                    customerCity = customer?.city.orEmpty(),
                    clientOrRepresentative = customer?.contactPerson.orEmpty(),
                    clientPhone = customer?.phone.orEmpty(),
                    clientEmail = customer?.email.orEmpty(),
                    siteName = "",
                    siteAddress = "",
                    customFields = if (freeform) report.customFields else emptyList()
                ))
            }
        )
        if (customers.isEmpty()) {
            Text(stringResource(R.string.loc2_no_customers))
        } else if (report.customerId.isNotBlank()) {
            val selectedCustomer = customers.firstOrNull { it.id == report.customerId }
            if (selectedCustomer != null) {
                Text(stringResource(R.string.loc4_customer_prefix, selectedCustomer.name), fontWeight = FontWeight.SemiBold)
                val customerSites = sites.filter { it.customerId == selectedCustomer.id }
                ChoiceField(
                    label = stringResource(R.string.audit_site),
                    selectedId = report.siteId,
                    options = customerSites.map { it.id to (it.name + " — " + it.address) },
                    onSelected = { id ->
                        val site = customerSites.firstOrNull { it.id == id }
                        onChange(report.copy(
                            siteId = id,
                            siteName = site?.name.orEmpty(),
                            siteAddress = site?.address.orEmpty(),
                            customFields = if (freeform) report.customFields else site?.fieldDefinitions?.map { definition ->
                                CustomReportField(label = definition.label, sourceDefinitionId = definition.id)
                            }.orEmpty()
                        ))
                    }
                )
                if (customerSites.isEmpty()) Text(stringResource(R.string.loc2_no_sites))
            }
        }
        if (report.customerName.isNotBlank()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(report.customerName, fontWeight = FontWeight.SemiBold)
                    if (report.customerBusinessId.isNotBlank()) Text(stringResource(R.string.loc4_business_id) + report.customerBusinessId)
                    if (report.customerAddress.isNotBlank()) Text(report.customerAddress)
                    val cityLine = listOf(report.customerPostalCode, report.customerCity).filter { it.isNotBlank() }.joinToString(" ")
                    if (cityLine.isNotBlank()) Text(cityLine)
                    if (report.clientOrRepresentative.isNotBlank()) Text(stringResource(R.string.loc4_contact_person) + report.clientOrRepresentative)
                    if (report.clientPhone.isNotBlank()) Text(report.clientPhone)
                    if (report.clientEmail.isNotBlank()) Text(report.clientEmail)
                }
            }
        }
        Field(stringResource(R.string.loc_site_detail), report.siteAddress) { onChange(report.copy(siteAddress = it)) }
        if (report.customerId.isBlank()) {
            Field(stringResource(R.string.loc4_client_rep), report.clientOrRepresentative) { onChange(report.copy(clientOrRepresentative = it)) }
            Field(stringResource(R.string.loc_phone), report.clientPhone) { onChange(report.copy(clientPhone = it)) }
            Field(stringResource(R.string.loc_email), report.clientEmail) { onChange(report.copy(clientEmail = it)) }
        }

        }
        if (!freeform) {
        SectionTitle(stringResource(R.string.loc_purpose_section))
        Field(stringResource(R.string.loc_purpose), report.purposeAndScope, minLines = 4) {
            onChange(report.copy(purposeAndScope = it))
        }

        SectionTitle(stringResource(R.string.loc_general_section))
        Field(stringResource(R.string.loc_general), report.generalObservations, minLines = 4) {
            onChange(report.copy(generalObservations = it))
        }

        }
        SectionTitle(stringResource(R.string.loc_custom_section))
        Field(stringResource(R.string.loc_pdf_heading), if (report.customFieldsTitle == "OMAT LISÄKENTÄT") stringResource(R.string.audit_custom_fields_title) else report.customFieldsTitle) {
            onChange(report.copy(customFieldsTitle = it))
        }
        report.customFields.forEach { customField ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    if (customField.sourceDefinitionId.isBlank()) {
                        Field(stringResource(R.string.loc_field_title), customField.label) { value ->
                            onChange(report.copy(customFields = report.customFields.map {
                                if (it.id == customField.id) it.copy(label = value) else it
                            }))
                        }
                    } else {
                        Text(customField.label, fontWeight = FontWeight.SemiBold)
                    }
                    Field(stringResource(R.string.loc_field_content), customField.value, minLines = 3) { value ->
                        onChange(report.copy(customFields = report.customFields.map {
                            if (it.id == customField.id) it.copy(value = value) else it
                        }))
                    }
                    TextButton(onClick = {
                        onChange(report.copy(customFields = report.customFields.filterNot { it.id == customField.id }))
                    }) { Text(stringResource(R.string.ui_remove_field)) }
                }
            }
        }
        OutlinedButton(
            onClick = { onChange(report.copy(customFields = report.customFields + CustomReportField(label = pdfContext.getString(R.string.new_custom_field_label)))) },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.ui_add_field)) }

        if (!freeform) {
        ChecklistEditor(report.checklist, AppLanguage.selected(pdfContext)) { items ->
            onChange(report.copy(checklist = items))
        }
        MeasurementEditor(report.measurements, AppLanguage.selected(pdfContext)) { items ->
            onChange(report.copy(measurements = items))
        }
        }
        SectionTitle(stringResource(R.string.loc_observations_section))
        report.observations.forEachIndexed { index, observation ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.loc4_observation_number, index + 1), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (index > 0) {
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { onDeleteObservation(observation) }) {
                                Text(stringResource(R.string.ui_delete))
                            }
                        }
                    }
                    Field(stringResource(R.string.loc4_location), observation.location) { value ->
                        onChange(report.copy(observations = report.observations.map {
                            if (it.id == observation.id) it.copy(location = value) else it
                        }))
                    }
                    Field(stringResource(R.string.loc4_description), observation.description, minLines = 2) { value ->
                        onChange(report.copy(observations = report.observations.map {
                            if (it.id == observation.id) it.copy(description = value) else it
                        }))
                    }
                    Field(stringResource(R.string.loc4_actions), observation.recommendedAction, minLines = 2) { value ->
                        onChange(report.copy(observations = report.observations.map {
                            if (it.id == observation.id) it.copy(recommendedAction = value) else it
                        }))
                    }
                    ObservationFollowUp(observation, AppLanguage.selected(pdfContext)) { updated ->
                        onChange(report.copy(observations = report.observations.map {
                            if (it.id == updated.id) updated else it
                        }))
                    }
                    observation.photos.forEach { photo ->
                        PhotoCard(
                            photo = photo,
                            onCategoryChange = { category ->
                                onChange(report.copy(observations = report.observations.map { item ->
                                    if (item.id == observation.id) item.copy(photos = item.photos.map {
                                        if (it.id == photo.id) it.copy(category = category) else it
                                    }) else item
                                }))
                            },
                            onCaptionChange = { caption ->
                                onChange(report.copy(observations = report.observations.map { item ->
                                    if (item.id == observation.id) {
                                        item.copy(photos = item.photos.map { if (it.id == photo.id) it.copy(caption = caption) else it })
                                    } else item
                                }))
                            },
                            onEdit = { photoBeingEdited = photo },
                            onDelete = { onDeletePhoto(observation.id, photo) }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onCamera(observation.id) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.ui_camera_photo))
                        }
                        OutlinedButton(
                            onClick = { onGallery(observation.id) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.ui_photos))
                        }
                    }
                }
            }
        }
        OutlinedButton(
            onClick = { onChange(report.copy(observations = report.observations + InspectionObservation())) },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.ui_add_item)) }

        if (!freeform) {
        SectionTitle(stringResource(R.string.loc_summary_section))
        Field(stringResource(R.string.loc_conclusions), report.summary, minLines = 4) {
            onChange(report.copy(summary = it))
        }

        }
        DateField(stringResource(R.string.loc_date), report.inspectionDate) {
            onChange(report.copy(inspectionDate = it))
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { onSaveTemplate(report.siteAddress.ifBlank { defaultTemplateName }) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.ui_save_template))
        }
        OutlinedButton(onClick = onPreview, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ui_preview_pdf)) }
        Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ui_share_pdf)) }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ui_back)) }
        Spacer(Modifier.height(28.dp))
    }
}


@Composable
private fun ChoiceField(
    label: String,
    selectedId: String,
    options: List<Pair<String, String>>,
    onSelected: (String) -> Unit
) {
    var expanded by remember(label, selectedId) { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selectedId }?.second ?: stringResource(R.string.audit_choose)
    Column {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), enabled = options.isNotEmpty()) {
            Column(Modifier.fillMaxWidth()) {
                Text(label, style = MaterialTheme.typography.labelSmall)
                Text(selectedLabel)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.second) },
                    onClick = {
                        onSelected(option.first)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Spacer(Modifier.height(10.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun Field(
    label: String,
    value: String,
    minLines: Int = 1,
    onValueChange: (String) -> Unit
) {
    // Keep keystrokes local to this field. The large report form receives
    // updates only after a short pause or when the field loses focus.
    var draft by remember { mutableStateOf(value) }

    LaunchedEffect(value) {
        if (draft != value) draft = value
    }
    LaunchedEffect(draft, value) {
        if (draft != value) {
            delay(220)
            if (draft != value) onValueChange(draft)
        }
    }

    OutlinedTextField(
        value = draft,
        onValueChange = { draft = it },
        label = { Text(label) },
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focus ->
                if (!focus.isFocused && draft != value) onValueChange(draft)
            },
        minLines = minLines,
        singleLine = minLines == 1
    )
}

@Composable
private fun PhotoCard(
    photo: ReportPhoto,
    onCaptionChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val displayPath = photo.annotatedPath.takeIf { it.isNotBlank() && File(it).exists() } ?: photo.filePath
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, displayPath) {
        value = withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(displayPath, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
            BitmapFactory.decodeFile(displayPath, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        }
    }
    val loadedBitmap = bitmap
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (loadedBitmap != null) {
                Image(
                    bitmap = loadedBitmap,
                    contentDescription = "Havaintokuva",
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(stringResource(R.string.loc4_image_failed, File(photo.filePath).name))
            }
            val language = AppLanguage.selected(LocalContext.current)
            var categoryMenu by remember(photo.id) { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { categoryMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(PhotoCategories.text("category", language) + ": " + PhotoCategories.text(PhotoCategories.normalize(photo.category), language) + " ▾")
                }
                DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                    PhotoCategories.ids.forEach { category ->
                        DropdownMenuItem(text = { Text(PhotoCategories.text(category, language)) }, onClick = {
                            categoryMenu = false
                            onCategoryChange(category)
                        })
                    }
                }
            }
            Field(stringResource(R.string.loc_photo_comment), photo.caption, minLines = 2, onValueChange = onCaptionChange)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.ui_edit_photo)) }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.ui_remove_photo)) }
            }
        }
    }
}

@Composable
private fun PhotoMarkupEditor(
    photo: ReportPhoto,
    onCancel: () -> Unit,
    onSave: (String) -> Unit
) {
    val sourcePath = photo.annotatedPath.takeIf { it.isNotBlank() && File(it).exists() } ?: photo.filePath
    val bitmap by produceState<Bitmap?>(initialValue = null, sourcePath) {
        value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(sourcePath) }
    }
    val loadedBitmap = bitmap
    var strokes by remember(photo.id) { mutableStateOf<List<List<Offset>>>(emptyList()) }

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.loc3_photo_edit_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCancel) { Text(stringResource(R.string.ui_back_plain)) }
        }
        Text(stringResource(R.string.ui_draw_hint))
        if (loadedBitmap != null) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                val scale = minOf(
                    constraints.maxWidth.toFloat() / loadedBitmap.width,
                    constraints.maxHeight.toFloat() / loadedBitmap.height
                ).coerceAtMost(1f)
                val imageWidth = (loadedBitmap.width * scale).toInt().coerceAtLeast(1)
                val imageHeight = (loadedBitmap.height * scale).toInt().coerceAtLeast(1)
                Canvas(
                    modifier = Modifier.size(
                        with(androidx.compose.ui.platform.LocalDensity.current) { imageWidth.toDp() },
                        with(androidx.compose.ui.platform.LocalDensity.current) { imageHeight.toDp() }
                    ).pointerInput(photo.id, imageWidth, imageHeight) {
                        detectDragGestures(
                            onDragStart = { point ->
                                val normalized = Offset(
                                    (point.x / size.width).coerceIn(0f, 1f),
                                    (point.y / size.height).coerceIn(0f, 1f)
                                )
                                strokes = strokes + listOf(listOf(normalized))
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val normalized = Offset(
                                    (change.position.x / size.width).coerceIn(0f, 1f),
                                    (change.position.y / size.height).coerceIn(0f, 1f)
                                )
                                if (strokes.isNotEmpty()) {
                                    strokes = strokes.dropLast(1) + listOf(strokes.last() + normalized)
                                }
                            }
                        )
                    }
                ) {
                    drawImage(
                        image = loadedBitmap.asImageBitmap(),
                        dstSize = IntSize(size.width.toInt(), size.height.toInt())
                    )
                    strokes.forEach { points ->
                        if (points.isNotEmpty()) {
                            val path = ComposePath()
                            points.forEachIndexed { index, point ->
                                val x = point.x * size.width
                                val y = point.y * size.height
                                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            if (points.size == 1) {
                                drawCircle(
                                    color = Color.Red,
                                    radius = size.minDimension * 0.006f,
                                    center = Offset(points.first().x * size.width, points.first().y * size.height)
                                )
                            } else {
                                drawPath(
                                    path = path,
                                    color = Color.Red,
                                    style = Stroke(
                                        width = size.minDimension * 0.009f,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Text(stringResource(R.string.loc3_photo_error), modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { if (strokes.isNotEmpty()) strokes = strokes.dropLast(1) },
                enabled = strokes.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.ui_undo)) }
            OutlinedButton(
                onClick = { strokes = emptyList() },
                enabled = strokes.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.ui_clear)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.ui_cancel)) }
            Button(
                onClick = {
                    val base = loadedBitmap
                    if (base != null) {
                        val outputBitmap = base.copy(Bitmap.Config.ARGB_8888, true)
                        val canvas = android.graphics.Canvas(outputBitmap)
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.RED
                            style = Paint.Style.STROKE
                            strokeWidth = minOf(base.width, base.height) * 0.009f
                            strokeCap = Paint.Cap.ROUND
                            strokeJoin = Paint.Join.ROUND
                        }
                        strokes.forEach { points ->
                            if (points.isNotEmpty()) {
                                val path = Path()
                                points.forEachIndexed { index, point ->
                                    val x = point.x * outputBitmap.width
                                    val y = point.y * outputBitmap.height
                                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                }
                                if (points.size == 1) {
                                    val point = points.first()
                                    canvas.drawCircle(
                                        point.x * outputBitmap.width,
                                        point.y * outputBitmap.height,
                                        paint.strokeWidth / 2f,
                                        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.RED }
                                    )
                                } else {
                                    canvas.drawPath(path, paint)
                                }
                            }
                        }
                        val outputFile = File(
                            File(photo.filePath).parentFile,
                            "marked_${System.currentTimeMillis()}_${photo.id.take(8)}.png"
                        )
                        outputFile.outputStream().use {
                            outputBitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                        }
                        outputBitmap.recycle()
                        onSave(outputFile.absolutePath)
                    }
                },
                enabled = loadedBitmap != null,
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.ui_save_marks)) }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DateField(
    label: String,
    value: String,
    onDateSelected: (String) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy") }
    val initialMillis = remember(value) {
        runCatching {
            LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrElse {
            LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
    }
    val displayDate = remember(value) {
        runCatching { LocalDate.parse(value).format(dateFormatter) }.getOrDefault("")
    }

    OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(displayDate.ifBlank { stringResource(R.string.audit_choose_date) })
        }
    }

    if (showPicker) {
        val pickerState = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val selectedDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onDateSelected(selectedDate.toString())
                    }
                    showPicker = false
                }) { Text(stringResource(R.string.ui_select)) }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.ui_cancel)) } }
        ) {
            androidx.compose.material3.DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun ObservationFollowUp(observation: InspectionObservation, language: String, onChange: (InspectionObservation) -> Unit) {
    var expanded by remember(observation.id) { mutableStateOf(
        listOf(observation.status, observation.priority, observation.responsiblePerson,
            observation.remediationDeadline, observation.reinspectionDate).any { it.isNotBlank() }
    ) }
    TextButton(onClick = { expanded = !expanded }) {
        Text(FollowUpLabels.text("details", language) + if (expanded) " ▴" else " ▾")
    }
    if (expanded) {
        FollowUpChoice("status", observation.status, FollowUpLabels.statuses, language) { onChange(observation.copy(status = it)) }
        FollowUpChoice("priority", observation.priority, FollowUpLabels.priorities, language) { onChange(observation.copy(priority = it)) }
        Field(FollowUpLabels.text("responsible", language), observation.responsiblePerson) { onChange(observation.copy(responsiblePerson = it)) }
        DateField(FollowUpLabels.text("due", language), observation.remediationDeadline) { onChange(observation.copy(remediationDeadline = it)) }
        if (observation.remediationDeadline.isNotBlank()) TextButton(onClick = { onChange(observation.copy(remediationDeadline = "")) }) { Text(FollowUpLabels.text("clear", language)) }
        DateField(FollowUpLabels.text("recheck", language), observation.reinspectionDate) { onChange(observation.copy(reinspectionDate = it)) }
        if (observation.reinspectionDate.isNotBlank()) TextButton(onClick = { onChange(observation.copy(reinspectionDate = "")) }) { Text(FollowUpLabels.text("clear", language)) }
    }
}

@Composable
private fun FollowUpChoice(label: String, value: String, options: List<String>, language: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(FollowUpLabels.text(label, language) + ": " + FollowUpLabels.text(value.ifBlank { "none" }, language) + " ▾")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(FollowUpLabels.text(option.ifBlank { "none" }, language)) }, onClick = {
                    expanded = false
                    onChange(option)
                })
            }
        }
    }
}

@Composable
private fun ChecklistEditor(items: List<ChecklistItem>, language: String, onChange: (List<ChecklistItem>) -> Unit) {
    var expanded by remember { mutableStateOf(items.isNotEmpty()) }
    TextButton(onClick = { expanded = !expanded }) {
        Text(ChecklistLabels.text("heading", language) + " (" + items.size + ")" + if (expanded) " ▴" else " ▾")
    }
    if (expanded) {
        items.forEach { item ->
            androidx.compose.runtime.key(item.id) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field(ChecklistLabels.text("item", language), item.label) { value ->
                            onChange(items.map { if (it.id == item.id) it.copy(label = value) else it })
                        }
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(ChecklistLabels.text("result", language) + ": " +
                                    ChecklistLabels.text(item.result.ifBlank { "none" }, language) + " ▾")
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                ChecklistLabels.results.forEach { result ->
                                    DropdownMenuItem(text = { Text(ChecklistLabels.text(result.ifBlank { "none" }, language)) }, onClick = {
                                        menu = false
                                        onChange(items.map { if (it.id == item.id) it.copy(result = result) else it })
                                    })
                                }
                            }
                        }
                        TextButton(onClick = { onChange(items.filterNot { it.id == item.id }) }) {
                            Text(stringResource(R.string.ui_delete))
                        }
                    }
                }
            }
        }
        OutlinedButton(onClick = { onChange(items + ChecklistItem()) }, modifier = Modifier.fillMaxWidth()) {
            Text(ChecklistLabels.text("add", language))
        }
    }
}

@Composable
private fun MeasurementEditor(items: List<MeasurementItem>, language: String, onChange: (List<MeasurementItem>) -> Unit) {
    var expanded by remember { mutableStateOf(items.isNotEmpty()) }
    TextButton(onClick = { expanded = !expanded }) {
        Text(MeasurementLabels.text("heading", language) + " (" + items.size + ")" + if (expanded) " ▴" else " ▾")
    }
    if (expanded) {
        items.forEach { item ->
            androidx.compose.runtime.key(item.id) {
                fun update(updated: MeasurementItem) {
                    onChange(items.map { if (it.id == updated.id) updated else it })
                }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field(MeasurementLabels.text("parameter", language), item.parameter) { update(item.copy(parameter = it)) }
                        Field(MeasurementLabels.text("value", language), item.value) { update(item.copy(value = it)) }
                        Field(MeasurementLabels.text("unit", language), item.unit) { update(item.copy(unit = it)) }
                        Field(MeasurementLabels.text("limit", language), item.permittedValue) { update(item.copy(permittedValue = it)) }
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(ChecklistLabels.text("result", language) + ": " +
                                    ChecklistLabels.text(item.result.ifBlank { "none" }, language) + " ▾")
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                ChecklistLabels.results.forEach { result ->
                                    DropdownMenuItem(text = { Text(ChecklistLabels.text(result.ifBlank { "none" }, language)) }, onClick = {
                                        menu = false
                                        update(item.copy(result = result))
                                    })
                                }
                            }
                        }
                        TextButton(onClick = { onChange(items.filterNot { it.id == item.id }) }) {
                            Text(stringResource(R.string.ui_delete))
                        }
                    }
                }
            }
        }
        OutlinedButton(onClick = { onChange(items + MeasurementItem()) }, modifier = Modifier.fillMaxWidth()) {
            Text(MeasurementLabels.text("add", language))
        }
    }
}
