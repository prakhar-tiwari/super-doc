package com.superdoc.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.superdoc.app.R
import com.superdoc.app.backup.BackupManager
import com.superdoc.app.ui.components.BigButton
import com.superdoc.app.ui.components.BigOutlinedButton
import com.superdoc.app.ui.components.BusyOverlay
import com.superdoc.app.ui.components.MessageDialog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface BackupMessage {
    data class Saved(val count: Int) : BackupMessage
    data class Restored(val added: Int, val alreadyThere: Int) : BackupMessage
    data object SaveFailed : BackupMessage
    data object RestoreFailed : BackupMessage
}

@HiltViewModel
class BackupViewModel @Inject constructor(private val backup: BackupManager) : ViewModel() {
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<BackupMessage?>(null)
        private set

    fun suggestedName() = backup.suggestedName()

    fun save(uri: Uri) = run {
        message = runCatching { BackupMessage.Saved(backup.export(uri)) }.getOrElse { BackupMessage.SaveFailed }
    }

    fun restore(uri: Uri) = run {
        message = runCatching { backup.restore(uri).let { BackupMessage.Restored(it.added, it.alreadyThere) } }
            .getOrElse { BackupMessage.RestoreFailed }
    }

    fun dismiss() {
        message = null
    }

    private fun run(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            block()
            busy = false
        }
    }
}

/** The "Backup" card in Settings: one file with every document, kept wherever the user chooses. */
@Composable
fun ColumnScope.BackupSettingsSection(vm: BackupViewModel) {
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(vm::save)
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::restore)
    }
    Section(stringResource(R.string.backup_title)) {
        Text(stringResource(R.string.backup_explain), style = MaterialTheme.typography.bodyMedium)
        BigButton(stringResource(R.string.backup_save), { saveLauncher.launch(vm.suggestedName()) }, icon = Icons.Filled.CloudUpload, enabled = !vm.busy)
        BigOutlinedButton(
            stringResource(R.string.backup_restore),
            { openLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
            icon = Icons.Filled.CloudDownload,
            enabled = !vm.busy,
        )
    }

    if (vm.busy) BusyOverlay(stringResource(R.string.backup_working))
    vm.message?.let { message ->
        val (title, text) = when (message) {
            is BackupMessage.Saved -> stringResource(R.string.backup_saved_title) to
                stringResource(R.string.backup_saved_message, message.count)
            is BackupMessage.Restored -> stringResource(R.string.backup_restored_title) to
                stringResource(R.string.backup_restored_message, message.added, message.alreadyThere)
            BackupMessage.SaveFailed -> stringResource(R.string.backup_failed_title) to stringResource(R.string.backup_save_failed)
            BackupMessage.RestoreFailed -> stringResource(R.string.backup_failed_title) to stringResource(R.string.backup_restore_failed)
        }
        MessageDialog(title = title, message = text, onDismiss = vm::dismiss)
    }
}
