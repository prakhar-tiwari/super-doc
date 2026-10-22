package com.superdoc.app.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.superdoc.app.R
import com.superdoc.app.ai.AiAction
import com.superdoc.app.ai.AiProgress
import com.superdoc.app.ai.AiRunner
import com.superdoc.app.ai.DeviceCapability
import com.superdoc.app.ai.InputTooLongException
import com.superdoc.app.ai.IntentMatcher
import com.superdoc.app.ai.ModelManager
import com.superdoc.app.ai.NoHindiException
import com.superdoc.app.ai.NoModelException
import com.superdoc.app.ai.VoiceIntent
import com.superdoc.app.ui.components.BigButton
import com.superdoc.app.ui.components.BigOutlinedButton
import com.superdoc.app.ui.components.VoiceInputButton
import com.superdoc.app.ui.note.NoteAiHost
import com.superdoc.app.ui.theme.SeniorColors
import com.superdoc.app.util.Script
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AiPhase {
    data object Menu : AiPhase
    data object Understanding : AiPhase
    /** The model's guess at a spoken instruction; small models guess wrong often enough that it must be confirmed. */
    data class Confirm(val intent: VoiceIntent) : AiPhase
    data class Working(val action: AiAction, val progress: AiProgress?) : AiPhase
    data class Result(val action: AiAction, val before: String, val after: String) : AiPhase
    data class Error(val message: Int) : AiPhase
}

@HiltViewModel
class AiViewModel @Inject constructor(
    device: DeviceCapability,
    private val models: ModelManager,
    private val runner: AiRunner,
) : ViewModel() {
    /** False on phones without enough memory: the AI helper is then not offered at all. */
    val deviceSupported: Boolean = device.canRunAi

    var hasModel by mutableStateOf<Boolean?>(null)
        private set
    var knowsHindi by mutableStateOf(false)
        private set
    var phase by mutableStateOf<AiPhase>(AiPhase.Menu)
        private set
    private var job: Job? = null

    fun refresh() {
        if (phase is AiPhase.Error || phase is AiPhase.Confirm || phase is AiPhase.Result) phase = AiPhase.Menu
        viewModelScope.launch {
            val model = models.active()
            knowsHindi = model?.hindi == true
            hasModel = model != null
        }
    }

    fun run(action: AiAction, text: String) {
        job?.cancel()
        phase = AiPhase.Working(action, null)
        job = viewModelScope.launch {
            phase = try {
                val after = runner.run(action, text) { p -> viewModelScope.launch { if (phase is AiPhase.Working) phase = AiPhase.Working(action, p) } }
                AiPhase.Result(action, text, after)
            } catch (e: CancellationException) {
                throw e
            } catch (e: NoModelException) {
                AiPhase.Error(R.string.ai_error_no_model)
            } catch (e: NoHindiException) {
                AiPhase.Error(R.string.ai_english_only)
            } catch (e: InputTooLongException) {
                AiPhase.Error(R.string.ai_error_too_long)
            } catch (e: OutOfMemoryError) {
                AiPhase.Error(R.string.ai_error_memory)
            } catch (e: Exception) {
                AiPhase.Error(R.string.ai_error_failed)
            }
        }
    }

    /** Keywords first (instant, no model needed); the model is asked only if they found nothing. */
    fun understand(spoken: String, onIntent: (VoiceIntent) -> Unit) {
        val quick = IntentMatcher.match(spoken)
        if (quick != VoiceIntent.Unknown || hasModel != true) {
            if (quick == VoiceIntent.Unknown) phase = AiPhase.Error(R.string.ai_error_not_understood) else onIntent(quick)
            return
        }
        phase = AiPhase.Understanding
        job = viewModelScope.launch {
            val intent = runCatching { runner.understand(spoken) }.getOrDefault(VoiceIntent.Unknown)
            phase = if (intent == VoiceIntent.Unknown) AiPhase.Error(R.string.ai_error_not_understood) else AiPhase.Confirm(intent)
        }
    }

    fun stop() {
        job?.cancel()
        phase = AiPhase.Menu
    }

    fun backToMenu() {
        phase = AiPhase.Menu
    }

    fun showError(message: Int) {
        phase = AiPhase.Error(message)
    }
}

/** The AI helper for a note: a few big buttons, or speak an instruction; then compare before/after and keep or undo. */
@Composable
fun AiPanel(host: NoteAiHost, vm: AiViewModel, onSetup: () -> Unit) {
    LaunchedEffect(Unit) { vm.refresh() }
    val text = host.vm.text.text
    val hindi = Script.isMostlyHindi(text)

    fun onIntent(intent: VoiceIntent) {
        when (intent) {
            is VoiceIntent.Ai -> if (text.isBlank()) vm.showError(R.string.ai_write_first) else vm.run(intent.action, text)
            VoiceIntent.ExportPdf, VoiceIntent.ExportWord -> { host.dismiss(); host.export() }
            VoiceIntent.ReadAloud -> { host.dismiss(); host.readAloud() }
            VoiceIntent.Send -> { host.dismiss(); host.send() }
            VoiceIntent.Unknown -> Unit
        }
    }

    Dialog(
        onDismissRequest = { if (vm.phase is AiPhase.Menu || vm.phase is AiPhase.Error || vm.phase is AiPhase.Confirm) host.dismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = SeniorColors.Help, modifier = Modifier.size(36.dp))
                    Text(stringResource(R.string.ai_title), style = MaterialTheme.typography.headlineSmall)
                }
                when (val phase = vm.phase) {
                    AiPhase.Menu -> Menu(vm, host, text, hindi, onSetup, ::onIntent)
                    AiPhase.Understanding -> Waiting(stringResource(R.string.ai_understanding), null, vm::stop)
                    is AiPhase.Confirm -> {
                        Text(stringResource(R.string.ai_did_you_mean), style = MaterialTheme.typography.titleMedium)
                        TextBox(stringResource(intentLabel(phase.intent, hindi)), highlighted = true)
                        BigButton(stringResource(R.string.ai_yes_do_it), { vm.backToMenu(); onIntent(phase.intent) }, icon = Icons.Filled.Check, color = SeniorColors.SavedGreen)
                        BigOutlinedButton(stringResource(R.string.ai_no_not_this), vm::backToMenu, icon = Icons.AutoMirrored.Filled.Undo)
                    }
                    is AiPhase.Working -> {
                        val p = phase.progress
                        val status = if (p != null && p.parts > 1) stringResource(R.string.ai_working_part, p.part, p.parts) else stringResource(R.string.ai_working)
                        Waiting(status, p?.partial, vm::stop)
                    }
                    is AiPhase.Result -> Result(
                        phase,
                        onKeep = {
                            vm.backToMenu()
                            host.replaceWithUndo(phase.before, phase.after)
                            host.dismiss()
                        },
                        onUndo = vm::backToMenu,
                    )
                    is AiPhase.Error -> {
                        Text(stringResource(phase.message), style = MaterialTheme.typography.bodyLarge)
                        BigButton(stringResource(R.string.action_ok), vm::backToMenu, icon = Icons.Filled.Check)
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.Menu(
    vm: AiViewModel,
    host: NoteAiHost,
    text: String,
    hindi: Boolean,
    onSetup: () -> Unit,
    onIntent: (VoiceIntent) -> Unit,
) {
    Text(stringResource(R.string.ai_privacy), style = MaterialTheme.typography.bodyMedium)
    when (vm.hasModel) {
        null -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        false -> {
            Text(stringResource(R.string.ai_not_set_up), style = MaterialTheme.typography.bodyLarge)
            BigButton(stringResource(R.string.settings_ai_open), { host.dismiss(); onSetup() }, icon = Icons.Filled.Download, color = SeniorColors.Help)
        }
        true -> {
            if (text.isBlank()) {
                Text(stringResource(R.string.ai_write_first), style = MaterialTheme.typography.bodyLarge, color = SeniorColors.Danger)
            } else if (hindi && !vm.knowsHindi) {
                Text(stringResource(R.string.ai_english_only), style = MaterialTheme.typography.bodyLarge)
            } else {
                Text(stringResource(R.string.ai_choose), style = MaterialTheme.typography.titleMedium)
                ActionButton(Icons.Filled.Spellcheck, R.string.ai_fix) { vm.run(AiAction.FIX_SPELLING, text) }
                ActionButton(Icons.Filled.FormatAlignLeft, R.string.ai_neat) { vm.run(AiAction.MAKE_NEAT, text) }
                ActionButton(Icons.Filled.Mail, R.string.ai_letter) { vm.run(AiAction.MAKE_LETTER, text) }
                ActionButton(Icons.Filled.ContentCut, R.string.ai_shorter) { vm.run(AiAction.MAKE_SHORTER, text) }
                if (vm.knowsHindi) {
                    ActionButton(Icons.Filled.Translate, if (hindi) R.string.ai_translate_to_en else R.string.ai_translate_to_hi) {
                        vm.run(AiAction.TRANSLATE, text)
                    }
                }
            }
            HorizontalDivider()
            Text(stringResource(R.string.ai_or_speak), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.ai_speak_examples), style = MaterialTheme.typography.bodyMedium)
            VoiceInputButton(
                onText = { spoken -> vm.understand(spoken, onIntent) },
                languageTag = host.voiceLanguage,
                label = stringResource(R.string.ai_speak_instruction),
            )
        }
    }
    BigOutlinedButton(stringResource(R.string.ai_close), host.dismiss, icon = Icons.Filled.Close)
}

private fun intentLabel(intent: VoiceIntent, hindi: Boolean): Int = when (intent) {
    is VoiceIntent.Ai -> when (intent.action) {
        AiAction.FIX_SPELLING -> R.string.ai_fix
        AiAction.MAKE_NEAT -> R.string.ai_neat
        AiAction.MAKE_LETTER -> R.string.ai_letter
        AiAction.MAKE_SHORTER -> R.string.ai_shorter
        AiAction.TRANSLATE -> if (hindi) R.string.ai_translate_to_en else R.string.ai_translate_to_hi
    }
    VoiceIntent.ExportPdf, VoiceIntent.ExportWord -> R.string.note_make_document
    VoiceIntent.ReadAloud -> R.string.note_read_aloud
    VoiceIntent.Send -> R.string.note_send
    VoiceIntent.Unknown -> R.string.ai_error_not_understood
}

@Composable
private fun ActionButton(icon: ImageVector, label: Int, onClick: () -> Unit) {
    BigButton(stringResource(label), onClick, icon = icon, color = SeniorColors.Help)
}

@Composable
private fun Waiting(status: String, partial: String?, onStop: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        CircularProgressIndicator(Modifier.size(48.dp), strokeWidth = 5.dp)
        Text(status, style = MaterialTheme.typography.titleMedium)
    }
    Text(stringResource(R.string.ai_takes_time), style = MaterialTheme.typography.bodyMedium)
    if (!partial.isNullOrBlank()) TextBox(partial, highlighted = true)
    BigOutlinedButton(stringResource(R.string.ai_stop), onStop, icon = Icons.Filled.Stop, color = SeniorColors.Danger)
}

@Composable
private fun Result(result: AiPhase.Result, onKeep: () -> Unit, onUndo: () -> Unit) {
    val same = result.after.trim() == result.before.trim()
    if (same) {
        Text(stringResource(R.string.ai_no_changes), style = MaterialTheme.typography.titleMedium, color = SeniorColors.SavedGreen)
        BigButton(stringResource(R.string.action_ok), onUndo, icon = Icons.Filled.Check)
        return
    }
    Text(stringResource(R.string.ai_before), style = MaterialTheme.typography.titleMedium)
    TextBox(result.before, highlighted = false)
    Text(stringResource(R.string.ai_after), style = MaterialTheme.typography.titleMedium)
    TextBox(result.after, highlighted = true)
    Text(stringResource(R.string.ai_check_result), style = MaterialTheme.typography.bodyMedium)
    BigButton(stringResource(R.string.ai_keep), onKeep, icon = Icons.Filled.Check, color = SeniorColors.SavedGreen)
    BigOutlinedButton(stringResource(R.string.ai_undo), onUndo, icon = Icons.AutoMirrored.Filled.Undo)
}

@Composable
private fun TextBox(text: String, highlighted: Boolean) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Start,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .background(if (highlighted) Color(0xFFFFF8E1) else Color(0xFFF1F1F1), RoundedCornerShape(12.dp))
            .border(if (highlighted) 2.dp else 1.dp, if (highlighted) SeniorColors.Highlight else Color(0xFFBDBDBD), RoundedCornerShape(12.dp))
            .padding(14.dp),
    )
}
