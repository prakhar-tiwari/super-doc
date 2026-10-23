package com.superdoc.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.superdoc.app.R
import com.superdoc.app.data.FamilyHelper
import com.superdoc.app.data.SettingsRepository
import com.superdoc.app.share.ShareHelper
import com.superdoc.app.ui.LocalSettings
import com.superdoc.app.ui.components.BigButton
import com.superdoc.app.ui.components.BigOutlinedButton
import com.superdoc.app.ui.components.ButtonPair
import com.superdoc.app.ui.components.SeniorScaffold
import com.superdoc.app.ui.components.TextInputDialog
import com.superdoc.app.ui.note.LanguageChip
import com.superdoc.app.ui.theme.SeniorColors
import com.superdoc.app.util.AppLocale
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    fun setTextScale(scale: Float) = viewModelScope.launch { settings.setTextScale(scale) }
    fun setSimpleMode(on: Boolean) = viewModelScope.launch { settings.setSimpleMode(on) }
    fun setNoteColors(on: Boolean) = viewModelScope.launch { settings.setNoteColors(on) }
    fun setVoiceOfflineOnly(on: Boolean) = viewModelScope.launch { settings.setVoiceOfflineOnly(on) }
    fun setFamily(helper: FamilyHelper?) = viewModelScope.launch { settings.setFamily(helper) }
    fun setSignature(name: String) = viewModelScope.launch { settings.setSignatureName(name) }
}

/** Extra sections contributed by later features (AI, practice, backup), so this screen stays small. */
typealias SettingsSection = @Composable ColumnScope.() -> Unit

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onHelp: () -> Unit,
    onAi: () -> Unit,
    extraSections: List<SettingsSection> = emptyList(),
) {
    val settings = LocalSettings.current
    val context = LocalContext.current
    var editFamily by remember { mutableStateOf(false) }
    var editSignature by remember { mutableStateOf(false) }

    SeniorScaffold(title = stringResource(R.string.action_settings), onBack = onBack, onHome = onHome, onHelp = onHelp) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Section(stringResource(R.string.settings_language)) {
                ButtonPair(
                    first = { m ->
                        BigButton("हिन्दी", { AppLocale.set(AppLocale.HINDI) }, m, Icons.Filled.Translate,
                            if (AppLocale.isHindi()) SeniorColors.Primary else SeniorColors.TextMuted)
                    },
                    second = { m ->
                        BigButton("English", { AppLocale.set(AppLocale.ENGLISH) }, m, Icons.Filled.Translate,
                            if (!AppLocale.isHindi()) SeniorColors.Primary else SeniorColors.TextMuted)
                    },
                )
            }

            Section(stringResource(R.string.settings_text_size)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TEXT_SIZES.forEach { (scale, label) ->
                        LanguageChip(stringResource(label), settings.textScale == scale) { vm.setTextScale(scale) }
                    }
                }
                Text(stringResource(R.string.settings_text_size_sample), style = MaterialTheme.typography.bodyLarge)
            }

            Section(stringResource(R.string.settings_family)) {
                Text(stringResource(R.string.settings_family_explain), style = MaterialTheme.typography.bodyMedium)
                val family = settings.family
                if (family != null) {
                    Text("${family.name}  ·  ${family.phone}", style = MaterialTheme.typography.titleMedium)
                    ButtonPair(
                        first = { m -> BigOutlinedButton(stringResource(R.string.settings_family_change), { editFamily = true }, m, Icons.Filled.Group) },
                        second = { m -> BigOutlinedButton(stringResource(R.string.settings_family_call, family.name), { ShareHelper.dial(context, family.phone) }, m, Icons.Filled.Phone) },
                    )
                    BigOutlinedButton(stringResource(R.string.settings_family_remove), { vm.setFamily(null) }, icon = Icons.Filled.Delete, color = SeniorColors.Danger)
                } else {
                    BigButton(stringResource(R.string.settings_family_add), { editFamily = true }, icon = Icons.Filled.Group, color = SeniorColors.WhatsApp)
                }
            }

            Section(stringResource(R.string.settings_signature)) {
                Text(stringResource(R.string.settings_signature_explain), style = MaterialTheme.typography.bodyMedium)
                if (settings.signatureName.isNotBlank()) Text(settings.signatureName, style = MaterialTheme.typography.titleMedium)
                BigOutlinedButton(stringResource(R.string.settings_signature_set), { editSignature = true })
            }

            Section(stringResource(R.string.settings_ai)) {
                Text(stringResource(R.string.settings_ai_explain), style = MaterialTheme.typography.bodyMedium)
                BigButton(stringResource(R.string.settings_ai_open), onAi, icon = Icons.Filled.AutoAwesome, color = SeniorColors.Help)
            }

            SwitchRow(
                title = stringResource(R.string.settings_simple_mode),
                subtitle = stringResource(R.string.settings_simple_mode_explain),
                checked = settings.simpleMode,
                onChange = vm::setSimpleMode,
            )
            SwitchRow(
                title = stringResource(R.string.settings_note_colors),
                subtitle = stringResource(R.string.settings_note_colors_explain),
                checked = settings.noteColors,
                onChange = vm::setNoteColors,
            )
            SwitchRow(
                title = stringResource(R.string.settings_voice_offline),
                subtitle = stringResource(R.string.settings_voice_offline_explain),
                checked = settings.voiceOfflineOnly,
                onChange = vm::setVoiceOfflineOnly,
            )
            extraSections.forEach { it() }
        }
    }

    if (editFamily) {
        FamilyDialog(settings.family, onSave = { vm.setFamily(it); editFamily = false }, onDismiss = { editFamily = false })
    }
    if (editSignature) {
        TextInputDialog(
            title = stringResource(R.string.settings_signature),
            initial = settings.signatureName,
            label = stringResource(R.string.settings_signature_label),
            confirmText = stringResource(R.string.action_save),
            onConfirm = { vm.setSignature(it); editSignature = false },
            onDismiss = { editSignature = false },
        )
    }
}

private val TEXT_SIZES = listOf(
    1f to R.string.settings_text_normal,
    1.15f to R.string.settings_text_large,
    1.3f to R.string.settings_text_extra_large,
)

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Card(
        onClick = { onChange(!checked) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { role = Role.Switch },
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
                Text(
                    stringResource(if (checked) R.string.settings_on else R.string.settings_off),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (checked) SeniorColors.SavedGreen else SeniorColors.TextMuted,
                )
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun FamilyDialog(current: FamilyHelper?, onSave: (FamilyHelper) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(current?.name.orEmpty()) }
    var phone by remember { mutableStateOf(current?.phone.orEmpty()) }
    val valid = name.isNotBlank() && phone.count { it.isDigit() } >= 10
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_family), style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(stringResource(R.string.settings_family_name)) },
                    textStyle = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it },
                    label = { Text(stringResource(R.string.settings_family_phone)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    textStyle = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton(stringResource(R.string.action_save), { onSave(FamilyHelper(name, phone)) }, icon = Icons.Filled.Check, enabled = valid)
                BigOutlinedButton(stringResource(R.string.action_cancel), onDismiss, icon = Icons.Filled.Close)
            }
        },
    )
}
