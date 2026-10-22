package com.superdoc.app.ui.export

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.superdoc.app.R
import com.superdoc.app.data.DocumentsRepository
import com.superdoc.app.data.FileStore
import com.superdoc.app.data.ItemEntity
import com.superdoc.app.data.NotesRepository
import com.superdoc.app.data.SettingsRepository
import com.superdoc.app.export.ExportFormat
import com.superdoc.app.export.LetterFields
import com.superdoc.app.export.NoteExporter
import com.superdoc.app.export.Template
import com.superdoc.app.pdf.ThumbnailCache
import com.superdoc.app.ui.components.BigButton
import com.superdoc.app.ui.components.BigOutlinedButton
import com.superdoc.app.ui.components.BusyOverlay
import com.superdoc.app.ui.components.SendItemDialog
import com.superdoc.app.ui.components.SeniorScaffold
import com.superdoc.app.ui.components.VoiceInputButton
import com.superdoc.app.ui.components.pagesLabel
import com.superdoc.app.ui.theme.SeniorColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ExportViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val notes: NotesRepository,
    private val exporter: NoteExporter,
    private val settings: SettingsRepository,
    val documents: DocumentsRepository,
    val files: FileStore,
    val thumbnails: ThumbnailCache,
) : ViewModel() {
    private val noteId: Long = savedState.get<String>("id")?.toLongOrNull() ?: 0L

    var note by mutableStateOf<ItemEntity?>(null)
        private set
    var template by mutableStateOf(Template.PLAIN)
        private set
    var format by mutableStateOf(ExportFormat.PDF)
    var fields by mutableStateOf(LetterFields())
        private set
    var preview by mutableStateOf<Pair<File, Int>?>(null)
        private set
    var saving by mutableStateOf(false)
        private set
    var saved by mutableStateOf<ItemEntity?>(null)

    private var previewJob: Job? = null

    init {
        viewModelScope.launch {
            note = notes.get(noteId)
            fields = fields.copy(signer = settings.current().signatureName)
            refreshPreview()
        }
    }

    fun chooseTemplate(t: Template) { template = t; refreshPreview() }

    fun updateFields(f: LetterFields) { fields = f; refreshPreview(debounce = true) }

    private fun refreshPreview(debounce: Boolean = false) {
        val n = note ?: return
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            if (debounce) delay(600)
            val old = preview?.first
            preview = exporter.preview(n, template, fields)
            old?.delete()
        }
    }

    fun save() {
        val n = note ?: return
        saving = true
        viewModelScope.launch {
            val id = exporter.export(n, template, fields, format, preview?.second ?: 1)
            if (fields.signer.isNotBlank()) settings.setSignatureName(fields.signer)
            saved = documents.get(id)
            saving = false
        }
    }

    override fun onCleared() {
        preview?.first?.delete()
    }
}

@Composable
fun ExportScreen(
    vm: ExportViewModel,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onHelp: () -> Unit,
    onOpen: (ItemEntity) -> Unit,
) {
    var sending by remember { mutableStateOf(false) }
    SeniorScaffold(title = stringResource(R.string.export_title), onBack = onBack, onHome = onHome, onHelp = onHelp) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val saved = vm.saved
            if (saved != null) {
                SavedPanel(saved, onOpen = { onOpen(saved) }, onSend = { sending = true }, onDone = onBack)
                return@Column
            }

            StepTitle(1, stringResource(R.string.export_step_style))
            TemplateCard(Icons.AutoMirrored.Filled.Article, R.string.export_plain, R.string.export_plain_sub, vm.template == Template.PLAIN) { vm.chooseTemplate(Template.PLAIN) }
            TemplateCard(Icons.Filled.Mail, R.string.export_letter, R.string.export_letter_sub, vm.template == Template.LETTER) { vm.chooseTemplate(Template.LETTER) }
            TemplateCard(Icons.AutoMirrored.Filled.FormatListBulleted, R.string.export_list, R.string.export_list_sub, vm.template == Template.LIST) { vm.chooseTemplate(Template.LIST) }

            if (vm.template == Template.LETTER) {
                StepTitle(2, stringResource(R.string.export_step_letter))
                LetterField(R.string.export_to, R.string.export_to_hint, vm.fields.to, minLines = 3) { vm.updateFields(vm.fields.copy(to = it)) }
                LetterField(R.string.export_subject, R.string.export_subject_hint, vm.fields.subject) { vm.updateFields(vm.fields.copy(subject = it)) }
                LetterField(R.string.export_signer, R.string.settings_signature_label, vm.fields.signer) { vm.updateFields(vm.fields.copy(signer = it)) }
            }

            StepTitle(if (vm.template == Template.LETTER) 3 else 2, stringResource(R.string.export_step_type))
            TemplateCard(Icons.Filled.PictureAsPdf, R.string.export_pdf, R.string.export_pdf_sub, vm.format == ExportFormat.PDF) { vm.format = ExportFormat.PDF }
            TemplateCard(Icons.Filled.Description, R.string.export_word, R.string.export_word_sub, vm.format == ExportFormat.WORD) { vm.format = ExportFormat.WORD }

            StepTitle(if (vm.template == Template.LETTER) 4 else 3, stringResource(R.string.export_step_check))
            Preview(vm)
            BigButton(stringResource(R.string.export_save), vm::save, icon = Icons.Filled.Save, color = SeniorColors.SavedGreen, enabled = vm.preview != null)
        }
    }
    if (vm.saving) BusyOverlay(stringResource(R.string.export_saving))
    val saved = vm.saved
    if (sending && saved != null) {
        SendItemDialog(saved, vm.documents.fileOf(saved), vm.files, onDismiss = { sending = false })
    }
}

@Composable
private fun StepTitle(number: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
        Box(
            Modifier
                .size(40.dp)
                .background(SeniorColors.Primary, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) { Text("$number", color = Color.White, style = MaterialTheme.typography.titleMedium) }
        Text(text, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun TemplateCard(icon: ImageVector, title: Int, subtitle: Int, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (selected) Modifier.border(4.dp, SeniorColors.Primary, RoundedCornerShape(16.dp)) else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, contentDescription = null, tint = SeniorColors.Primary, modifier = Modifier.size(44.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(subtitle), style = MaterialTheme.typography.bodySmall)
            }
            if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = stringResource(R.string.export_selected), tint = SeniorColors.SavedGreen, modifier = Modifier.size(36.dp))
        }
    }
}

@Composable
private fun LetterField(label: Int, hint: Int, value: String, minLines: Int = 1, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(stringResource(label)) },
            placeholder = { Text(stringResource(hint)) },
            textStyle = MaterialTheme.typography.bodyLarge,
            minLines = minLines,
            modifier = Modifier.fillMaxWidth(),
        )
        VoiceInputButton(
            onText = { spoken -> onChange(if (value.isBlank()) spoken else "$value $spoken") },
            label = stringResource(R.string.export_speak_field, stringResource(label)),
        )
    }
}

@Composable
private fun Preview(vm: ExportViewModel) {
    val preview = vm.preview
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(preview) { bitmap = preview?.let { vm.thumbnails.render(it.first, 0, 900) } }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.Visibility, contentDescription = null)
            Text(
                if (preview != null) "${stringResource(R.string.export_preview)} · ${pagesLabel(preview.second)}" else stringResource(R.string.export_preview),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Box(
            Modifier
                .fillMaxWidth(0.85f)
                .aspectRatio(210f / 297f)
                .background(Color.White)
                .border(1.dp, Color(0xFF9E9E9E)),
            contentAlignment = Alignment.Center,
        ) {
            bitmap?.let { Image(it.asImageBitmap(), contentDescription = stringResource(R.string.export_preview), modifier = Modifier.fillMaxSize()) }
        }
    }
}

@Composable
private fun SavedPanel(item: ItemEntity, onOpen: () -> Unit, onSend: () -> Unit, onDone: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SeniorColors.SavedGreen, modifier = Modifier.size(96.dp))
        Text(stringResource(R.string.export_saved_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.export_saved_message, item.title), style = MaterialTheme.typography.bodyLarge)
        BigButton(stringResource(R.string.note_send), onSend, icon = Icons.AutoMirrored.Filled.Send, color = SeniorColors.WhatsApp)
        BigOutlinedButton(stringResource(R.string.export_open), onOpen, icon = Icons.Filled.Visibility)
        BigOutlinedButton(stringResource(R.string.export_done), onDone, icon = Icons.Filled.Done)
    }
}
