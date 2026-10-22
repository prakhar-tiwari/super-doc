package com.superdoc.app.ui.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.superdoc.app.R
import com.superdoc.app.scan.ScanResult
import com.superdoc.app.scan.ScanSaver
import com.superdoc.app.scan.rememberPhotoPicker
import com.superdoc.app.scan.rememberScanner
import com.superdoc.app.ui.components.BigButton
import com.superdoc.app.ui.components.BigOutlinedButton
import com.superdoc.app.ui.components.BusyOverlay
import com.superdoc.app.ui.components.MessageDialog
import com.superdoc.app.ui.theme.SeniorColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(private val saver: ScanSaver) : ViewModel() {
    var busy by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)

    fun save(result: ScanResult, onSaved: (Long) -> Unit) {
        busy = true
        viewModelScope.launch {
            runCatching { saver.save(result) }
                .onSuccess { onSaved(it) }
                .onFailure { failed = true }
            busy = false
        }
    }
}

/**
 * "Photo to Document": a plain explanation first, then the camera, then a "please wait"
 * while the PDF is saved. Returns the function that starts it.
 */
@Composable
fun rememberScanFlow(onSaved: (Long) -> Unit): () -> Unit {
    val vm: ScanViewModel = hiltViewModel()
    var explaining by remember { mutableStateOf(false) }
    val startScanner = rememberScanner { result -> vm.save(result, onSaved) }
    val pickPhotos = rememberPhotoPicker { uris -> vm.save(ScanResult(null, uris), onSaved) }

    if (explaining) {
        AlertDialog(
            onDismissRequest = { explaining = false },
            title = { Text(stringResource(R.string.scan_intro_title), style = MaterialTheme.typography.headlineSmall) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(R.string.scan_step_1, R.string.scan_step_2, R.string.scan_step_3, R.string.scan_step_4).forEachIndexed { i, res ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                            Text("${i + 1}.", style = MaterialTheme.typography.titleLarge, color = SeniorColors.Scan, modifier = Modifier.size(32.dp))
                            Text(stringResource(res), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigButton(stringResource(R.string.scan_open_camera), { explaining = false; startScanner() }, icon = Icons.Filled.CameraAlt, color = SeniorColors.Scan)
                    BigOutlinedButton(stringResource(R.string.scan_from_gallery), { explaining = false; pickPhotos() }, icon = Icons.Filled.PhotoLibrary)
                    BigOutlinedButton(stringResource(R.string.action_cancel), { explaining = false }, icon = Icons.Filled.Close)
                }
            },
        )
    }
    if (vm.busy) BusyOverlay(stringResource(R.string.scan_saving))
    if (vm.failed) {
        MessageDialog(stringResource(R.string.scan_failed_title), stringResource(R.string.scan_failed_message), onDismiss = { vm.failed = false })
    }
    return remember { { explaining = true } }
}
