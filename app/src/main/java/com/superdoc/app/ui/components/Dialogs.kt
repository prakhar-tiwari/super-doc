package com.superdoc.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.superdoc.app.R
import com.superdoc.app.ui.theme.SeniorColors

/** Asks before anything destructive. Buttons are stacked so both are large and easy to read. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    dismissText: String = stringResource(R.string.action_no_go_back),
    closeOnlyWithButtons: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = if (closeOnlyWithButtons) ({}) else onDismiss,
        properties = DialogProperties(dismissOnBackPress = !closeOnlyWithButtons, dismissOnClickOutside = !closeOnlyWithButtons),
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = { Text(message, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton(
                    text = confirmText,
                    onClick = onConfirm,
                    icon = Icons.Filled.Check,
                    color = if (destructive) SeniorColors.Danger else SeniorColors.Primary,
                )
                BigOutlinedButton(text = dismissText, onClick = onDismiss, icon = Icons.Filled.Close)
            }
        },
    )
}

@Composable
fun MessageDialog(title: String, message: String, onDismiss: () -> Unit, extra: (@Composable () -> Unit)? = null) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = { Text(message, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                extra?.invoke()
                BigButton(text = stringResource(R.string.action_ok), onClick = onDismiss, icon = Icons.Filled.Check)
            }
        },
    )
}

/** Single text box dialog, e.g. for renaming. A microphone lets the user speak the text instead of typing. */
@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    label: String,
    confirmText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    allowVoice: Boolean = true,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(label, style = MaterialTheme.typography.bodySmall) },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (allowVoice) {
                    VoiceInputButton(onText = { spoken -> text = spoken }, replace = true)
                }
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton(text = confirmText, onClick = { onConfirm(text) }, icon = Icons.Filled.Check)
                BigOutlinedButton(text = stringResource(R.string.action_cancel), onClick = onDismiss, icon = Icons.Filled.Close)
            }
        },
    )
}

/** Blocks the screen with a clear "please wait" message during slow work. */
@Composable
fun BusyOverlay(message: String, progress: Float? = null) {
    // Drawn inside the screen rather than as a Dialog window: a window shown for only a moment
    // can trip the system's focus watchdog ("app isn't responding") on slow phones.
    BackHandler {}
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } }
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Color.White, MaterialTheme.shapes.large)
                .padding(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (progress == null) {
                    CircularProgressIndicator(modifier = Modifier.size(64.dp), strokeWidth = 6.dp)
                } else {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                }
                Text(message, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun EmptyState(message: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}
