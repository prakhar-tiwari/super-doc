package com.superdoc.app.ui.help

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.superdoc.app.R
import com.superdoc.app.ai.DeviceCapability
import com.superdoc.app.help.HelpRepository
import com.superdoc.app.help.HelpTopic
import com.superdoc.app.help.PracticeSamples
import com.superdoc.app.ui.components.BigButton
import com.superdoc.app.ui.components.BigOutlinedButton
import com.superdoc.app.ui.components.BigTileButton
import com.superdoc.app.ui.components.BusyOverlay
import com.superdoc.app.ui.components.MessageDialog
import com.superdoc.app.ui.components.SeniorScaffold
import com.superdoc.app.ui.settings.Section
import com.superdoc.app.ui.theme.SeniorColors
import com.superdoc.app.util.AppLocale
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HelpIndexViewModel @Inject constructor(
    private val repo: HelpRepository,
    device: DeviceCapability,
) : ViewModel() {
    private val aiShown = device.canRunAi
    var topics by mutableStateOf<List<HelpTopic>>(emptyList())
        private set

    init {
        viewModelScope.launch {
            topics = repo.content().topics.filter { it.id != TOUR && (it.id != "ai" || aiShown) }
        }
    }

    companion object {
        const val TOUR = "tour"
    }
}

/** Adds or removes the practice examples; used from Help, the tour and Settings. */
@HiltViewModel
class PracticeViewModel @Inject constructor(private val practice: PracticeSamples) : ViewModel() {
    var hasSamples by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set

    fun refresh() {
        viewModelScope.launch { hasSamples = practice.has() }
    }

    fun add(onDone: () -> Unit = {}) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            runCatching { practice.add() }
            hasSamples = practice.has()
            busy = false
            onDone()
        }
    }

    fun remove(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            practice.remove()
            hasSamples = false
            onDone()
        }
    }
}

@Composable
fun HelpIndexScreen(
    vm: HelpIndexViewModel,
    practice: PracticeViewModel,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onTopic: (String) -> Unit,
    onOpenDocuments: () -> Unit,
) {
    val hindi = AppLocale.isHindi()
    var practiceAdded by remember { mutableStateOf(false) }

    SeniorScaffold(title = stringResource(R.string.help_title), onBack = onBack, onHome = onHome) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BigTileButton(
                Icons.Filled.Explore, stringResource(R.string.help_tour_button), SeniorColors.Help,
                { onTopic(HelpIndexViewModel.TOUR) }, Modifier.fillMaxWidth(), subtitle = stringResource(R.string.help_tour_sub),
            )
            BigTileButton(
                Icons.Filled.School, stringResource(R.string.help_practice_button), SeniorColors.SavedGreen,
                { practice.add { practiceAdded = true } }, Modifier.fillMaxWidth(), subtitle = stringResource(R.string.help_practice_sub),
            )
            Text(stringResource(R.string.help_topics), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
            vm.topics.forEach { topic ->
                BigOutlinedButton(topic.title.get(hindi), { onTopic(topic.id) }, icon = helpIcon(topic.icon))
            }
            FamilyCallButton()
        }
    }

    if (practice.busy) BusyOverlay(stringResource(R.string.export_saving))
    if (practiceAdded) {
        MessageDialog(
            title = stringResource(R.string.help_practice_added_title),
            message = stringResource(R.string.help_practice_added_message),
            onDismiss = { practiceAdded = false },
            extra = {
                BigButton(stringResource(R.string.help_open_documents), { practiceAdded = false; onOpenDocuments() }, icon = Icons.Filled.Folder)
            },
        )
    }
}

/** The "Practice examples" card in Settings. */
@Composable
fun ColumnScope.PracticeSettingsSection(practice: PracticeViewModel) {
    LaunchedEffect(Unit) { practice.refresh() }
    var message by remember { mutableStateOf<Int?>(null) }
    Section(stringResource(R.string.settings_practice)) {
        Text(stringResource(R.string.settings_practice_explain), style = MaterialTheme.typography.bodyMedium)
        message?.let { Text(stringResource(it), style = MaterialTheme.typography.titleMedium, color = SeniorColors.SavedGreen) }
        if (practice.hasSamples) {
            BigOutlinedButton(
                stringResource(R.string.settings_practice_remove),
                { practice.remove { message = R.string.settings_practice_removed } },
                icon = Icons.Filled.Delete,
                color = SeniorColors.Danger,
            )
        } else {
            BigButton(
                stringResource(R.string.settings_practice_add),
                { practice.add { message = R.string.settings_practice_added } },
                icon = Icons.Filled.School,
                color = SeniorColors.SavedGreen,
                enabled = !practice.busy,
            )
        }
    }
}

private fun helpIcon(name: String): ImageVector = when (name) {
    "home" -> Icons.Filled.Home
    "note" -> Icons.Filled.EditNote
    "scan" -> Icons.Filled.DocumentScanner
    "library" -> Icons.Filled.Folder
    "viewer" -> Icons.Filled.PictureAsPdf
    "export" -> Icons.Filled.Description
    "tools" -> Icons.Filled.Handyman
    "ai" -> Icons.Filled.AutoAwesome
    "settings" -> Icons.Filled.Settings
    "tour" -> Icons.Filled.Explore
    else -> Icons.AutoMirrored.Filled.HelpOutline
}
