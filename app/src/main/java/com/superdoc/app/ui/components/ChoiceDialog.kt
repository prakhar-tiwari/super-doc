package com.superdoc.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.superdoc.app.R

data class Choice(val label: String, val icon: ImageVector, val color: Color, val onClick: () -> Unit)

/** A dialog listing a few big, clearly labelled choices (for example the ways to send a document). */
@Composable
fun ChoiceDialog(title: String, choices: List<Choice>, onDismiss: () -> Unit, message: String? = null) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (message != null) Text(message, style = MaterialTheme.typography.bodyLarge)
                choices.forEach { choice ->
                    BigButton(
                        text = choice.label,
                        icon = choice.icon,
                        color = choice.color,
                        onClick = {
                            onDismiss()
                            choice.onClick()
                        },
                    )
                }
                BigOutlinedButton(text = stringResource(R.string.action_cancel), onClick = onDismiss, icon = Icons.Filled.Close)
            }
        },
        confirmButton = {},
    )
}
