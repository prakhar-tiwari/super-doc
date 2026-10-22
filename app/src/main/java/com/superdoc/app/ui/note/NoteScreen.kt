package com.superdoc.app.ui.note

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.superdoc.app.R
import com.superdoc.app.ui.components.BigButton
import com.superdoc.app.ui.components.BigOutlinedButton
import com.superdoc.app.ui.components.ButtonPair
import com.superdoc.app.ui.components.ConfirmDialog
import com.superdoc.app.ui.components.MessageDialog
import com.superdoc.app.ui.components.SendItemDialog
import com.superdoc.app.ui.components.SeniorScaffold
import com.superdoc.app.ui.components.TextInputDialog
import com.superdoc.app.ui.components.TopBarButton
import com.superdoc.app.ui.components.VoiceInputButton
import com.superdoc.app.ui.theme.SeniorColors
import com.superdoc.app.util.AppLocale
import com.superdoc.app.voice.Speaker
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val AI_UNDO_MS = 30_000L

data class NoteActions(
    val back: () -> Unit,
    val home: () -> Unit,
    val help: () -> Unit,
    val export: (Long) -> Unit,
    val deleted: () -> Unit,
)

/** What the AI helper panel may do on this screen; spoken instructions can also read, send or export. */
class NoteAiHost(
    val vm: NoteViewModel,
    /** Puts [after] in the note and offers Undo; runs on this screen so it outlives the closed panel. */
    val replaceWithUndo: (before: String, after: String) -> Unit,
    val voiceLanguage: String,
    val export: () -> Unit,
    val send: () -> Unit,
    val readAloud: () -> Unit,
    val dismiss: () -> Unit,
)

/** Slot for the AI helper panel; keeps this screen independent of the AI feature. */
typealias AiPanelSlot = @Composable (NoteAiHost) -> Unit

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteScreen(vm: NoteViewModel, actions: NoteActions, aiPanel: AiPanelSlot? = null) {
    val context = LocalContext.current
    val item by vm.item.collectAsState()
    val speaking by vm.speaker.speaking.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val aiChangedMsg = stringResource(R.string.ai_kept_message)
    val undoLabel = stringResource(R.string.action_undo)
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val imeVisible = WindowInsets.isImeVisible

    var voiceLang by rememberSaveable { mutableStateOf(if (AppLocale.isHindi()) "hi-IN" else "en-IN") }
    var renaming by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showAi by rememberSaveable { mutableStateOf(false) }
    var ttsMissing by remember { mutableStateOf(false) }

    val copiedMsg = stringResource(R.string.note_copied)
    val emptyMsg = stringResource(R.string.note_empty_nothing_to_do)

    fun withText(action: () -> Unit) {
        if (vm.text.text.isBlank()) scope.launch { snackbar.showSnackbar(emptyMsg) } else action()
    }

    SeniorScaffold(
        title = stringResource(R.string.type_note),
        onBack = actions.back,
        onHome = actions.home,
        onHelp = actions.help,
        snackbarHostState = snackbar,
        trailing = { TopBarButton(Icons.Filled.Edit, stringResource(R.string.action_rename), { renaming = true }) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item?.let { Text(it.title, style = MaterialTheme.typography.titleMedium) }
            SaveIndicator(vm.saveState)
            OutlinedTextField(
                value = vm.text,
                onValueChange = vm::onTextChange,
                enabled = vm.loaded,
                textStyle = MaterialTheme.typography.bodyLarge,
                placeholder = { Text(stringResource(R.string.note_placeholder), style = MaterialTheme.typography.bodyLarge) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (imeVisible) 180.dp else 260.dp),
            )

            if (imeVisible) {
                BigButton(
                    text = stringResource(R.string.note_done_typing),
                    icon = Icons.Filled.KeyboardHide,
                    color = SeniorColors.SavedGreen,
                    onClick = { keyboard?.hide(); focus.clearFocus() },
                )
            }

            Text(stringResource(R.string.note_voice_language), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LanguageChip("हिन्दी", voiceLang == "hi-IN") { voiceLang = "hi-IN" }
                LanguageChip("English", voiceLang == "en-IN") { voiceLang = "en-IN" }
            }
            VoiceInputButton(onText = vm::insertSpoken, languageTag = voiceLang)

            ButtonPair(
                first = { m ->
                    BigOutlinedButton(
                        text = stringResource(if (speaking) R.string.note_stop_reading else R.string.note_read_aloud),
                        icon = if (speaking) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                        modifier = m,
                        onClick = {
                            if (speaking) vm.speaker.stop() else withText {
                                if (vm.speaker.speak(vm.text.text) == Speaker.Result.LANGUAGE_MISSING) ttsMissing = true
                            }
                        },
                    )
                },
                second = { m ->
                    BigOutlinedButton(
                        text = stringResource(R.string.note_copy_all),
                        icon = Icons.Filled.ContentCopy,
                        modifier = m,
                        onClick = {
                            withText {
                                copyToClipboard(context, item?.title.orEmpty(), vm.text.text)
                                scope.launch { snackbar.showSnackbar(copiedMsg, duration = SnackbarDuration.Long) }
                            }
                        },
                    )
                },
            )
            BigButton(
                text = stringResource(R.string.note_send),
                icon = Icons.AutoMirrored.Filled.Send,
                color = SeniorColors.WhatsApp,
                onClick = { withText { scope.launch { vm.saveNow(); sending = true } } },
            )
            BigButton(
                text = stringResource(R.string.note_make_document),
                icon = Icons.Filled.PictureAsPdf,
                color = SeniorColors.Documents,
                onClick = { withText { scope.launch { vm.saveNow(); vm.id?.let(actions.export) } } },
            )
            if (aiPanel != null) {
                BigButton(
                    text = stringResource(R.string.note_ai_helper),
                    icon = Icons.Filled.AutoAwesome,
                    color = SeniorColors.Help,
                    onClick = { showAi = true },
                )
            }
            BigOutlinedButton(
                text = stringResource(R.string.note_delete),
                icon = Icons.Filled.Delete,
                color = SeniorColors.Danger,
                onClick = { confirmDelete = true },
            )
        }
    }

    if (showAi && aiPanel != null) {
        aiPanel(
            NoteAiHost(
                vm = vm,
                replaceWithUndo = { before, after ->
                    vm.replaceAll(after)
                    scope.launch {
                        // Longer than SnackbarDuration.Long (10 s): older users need time to read and decide.
                        val r = withTimeoutOrNull(AI_UNDO_MS) { snackbar.showSnackbar(aiChangedMsg, undoLabel, duration = SnackbarDuration.Indefinite) }
                        if (r == SnackbarResult.ActionPerformed) vm.replaceAll(before)
                    }
                },
                voiceLanguage = voiceLang,
                export = { withText { scope.launch { vm.saveNow(); vm.id?.let(actions.export) } } },
                send = { withText { scope.launch { vm.saveNow(); sending = true } } },
                readAloud = {
                    withText { if (vm.speaker.speak(vm.text.text) == Speaker.Result.LANGUAGE_MISSING) ttsMissing = true }
                },
                dismiss = { showAi = false },
            )
        )
    }

    if (renaming) {
        TextInputDialog(
            title = stringResource(R.string.rename_title),
            initial = item?.title.orEmpty(),
            label = stringResource(R.string.rename_label),
            confirmText = stringResource(R.string.action_save),
            onConfirm = { vm.rename(it); renaming = false },
            onDismiss = { renaming = false },
        )
    }

    val current = item
    if (sending && current != null) {
        SendItemDialog(
            item = current.copy(body = vm.text.text),
            file = null,
            files = vm.files,
            onDismiss = { sending = false },
            onMakePdf = { vm.id?.let(actions.export) },
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.note_delete_title),
            message = stringResource(R.string.delete_message_recycle),
            confirmText = stringResource(R.string.delete_yes),
            destructive = true,
            onConfirm = { confirmDelete = false; vm.delete(actions.deleted) },
            onDismiss = { confirmDelete = false },
        )
    }

    if (ttsMissing) {
        MessageDialog(
            title = stringResource(R.string.tts_missing_title),
            message = stringResource(R.string.tts_missing_message),
            onDismiss = { ttsMissing = false },
            extra = {
                BigOutlinedButton(
                    text = stringResource(R.string.tts_install),
                    onClick = { runCatching { context.startActivity(vm.speaker.installVoiceDataIntent()) }; ttsMissing = false },
                )
            },
        )
    }

    LaunchedEffect(Unit) { vm.speaker.stop() }
}

@Composable
private fun SaveIndicator(state: SaveState) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val saved = state == SaveState.SAVED
        Icon(
            if (saved) Icons.Filled.CheckCircle else Icons.Filled.Sync,
            contentDescription = null,
            tint = if (saved) SeniorColors.SavedGreen else SeniorColors.TextMuted,
            modifier = Modifier.size(26.dp),
        )
        Text(
            stringResource(if (saved) R.string.note_saved else R.string.note_saving),
            style = MaterialTheme.typography.bodyMedium,
            color = if (saved) SeniorColors.SavedGreen else SeniorColors.TextMuted,
        )
    }
}

@Composable
fun LanguageChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp)) },
        leadingIcon = if (selected) {
            { Icon(Icons.Filled.CheckCircle, contentDescription = null) }
        } else null,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = SeniorColors.Primary,
            selectedLabelColor = androidx.compose.ui.graphics.Color.White,
            selectedLeadingIconColor = androidx.compose.ui.graphics.Color.White,
        ),
    )
}

fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}
