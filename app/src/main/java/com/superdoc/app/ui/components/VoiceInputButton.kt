package com.superdoc.app.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.superdoc.app.R
import com.superdoc.app.ui.LocalSettings
import com.superdoc.app.ui.theme.SeniorColors
import com.superdoc.app.util.AppLocale
import com.superdoc.app.voice.VoiceTyping

/**
 * One big "Speak" button. Explains the microphone permission in plain words before Android asks,
 * shows what it hears while listening, and hands the final text to [onText].
 */
@Composable
fun VoiceInputButton(
    onText: (String) -> Unit,
    modifier: Modifier = Modifier,
    languageTag: String = if (AppLocale.isHindi()) "hi-IN" else "en-IN",
    replace: Boolean = false,
    label: String = stringResource(if (replace) R.string.voice_speak_name else R.string.voice_speak),
) {
    val context = LocalContext.current
    val settings = LocalSettings.current
    val voice = remember { VoiceTyping(context) }
    val state by voice.state.collectAsState()
    val latestOnText by rememberUpdatedState(onText)
    var explainPermission by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { voice.stop() } }

    fun begin() = voice.start(languageTag, settings.voiceOfflineOnly) { latestOnText(it) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) begin()
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val listening = state is VoiceTyping.State.Listening
        BigButton(
            text = if (listening) stringResource(R.string.voice_stop) else label,
            icon = if (listening) Icons.Filled.Stop else Icons.Filled.Mic,
            color = if (listening) SeniorColors.Danger else SeniorColors.Primary,
            onClick = {
                when {
                    listening -> voice.stop()
                    !voice.isAvailable() -> {}
                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED -> begin()
                    else -> explainPermission = true
                }
            },
        )
        when (val s = state) {
            is VoiceTyping.State.Listening -> Text(
                text = if (s.partial.isBlank()) stringResource(R.string.voice_listening) else s.partial,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFF3C4), RoundedCornerShape(12.dp))
                    .padding(12.dp),
            )
            is VoiceTyping.State.Error -> MessageDialog(
                title = stringResource(R.string.voice_error_title),
                message = stringResource(s.messageRes),
                onDismiss = { voice.clearError() },
            )
            VoiceTyping.State.Idle -> Unit
        }
        if (!voice.isAvailable()) {
            Text(stringResource(R.string.voice_error_not_available), style = MaterialTheme.typography.bodySmall)
        }
    }

    if (explainPermission) {
        ConfirmDialog(
            title = stringResource(R.string.permission_mic_title),
            message = stringResource(R.string.permission_mic_message),
            confirmText = stringResource(R.string.action_continue),
            onConfirm = {
                explainPermission = false
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            onDismiss = { explainPermission = false },
        )
    }
}
