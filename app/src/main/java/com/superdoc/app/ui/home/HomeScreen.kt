package com.superdoc.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.superdoc.app.R
import com.superdoc.app.data.DocumentsRepository
import com.superdoc.app.data.ItemEntity
import com.superdoc.app.data.ItemType
import com.superdoc.app.data.SettingsRepository
import com.superdoc.app.pdf.ThumbnailCache
import com.superdoc.app.ui.LocalSettings
import com.superdoc.app.ui.components.BigTileButton
import com.superdoc.app.ui.components.ButtonPair
import com.superdoc.app.ui.components.ConfirmDialog
import com.superdoc.app.ui.components.ItemCard
import com.superdoc.app.ui.components.SeniorScaffold
import com.superdoc.app.ui.components.TopBarButton
import com.superdoc.app.ui.navigation.PageTool
import com.superdoc.app.ui.theme.SeniorColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    val documents: DocumentsRepository,
    val thumbnails: ThumbnailCache,
    private val settings: SettingsRepository,
) : ViewModel() {
    val recent: StateFlow<List<ItemEntity>> = documents.observeLibrary("")
        .map { it.take(3) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun tourOffered() = viewModelScope.launch { settings.setTourOffered() }
}

data class HomeActions(
    val newNote: () -> Unit,
    val scan: () -> Unit,
    val library: () -> Unit,
    val help: () -> Unit,
    val tools: () -> Unit,
    val merge: () -> Unit,
    val pageTool: (PageTool) -> Unit,
    val settings: () -> Unit,
    val open: (ItemEntity) -> Unit,
    val startTour: () -> Unit,
)

@Composable
fun HomeScreen(vm: HomeViewModel, actions: HomeActions) {
    val recent by vm.recent.collectAsState()
    val settings = LocalSettings.current

    SeniorScaffold(
        title = stringResource(R.string.app_name),
        onHelp = { actions.help() },
        trailing = { TopBarButton(Icons.Filled.Settings, stringResource(R.string.action_settings), actions.settings) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.home_question), style = MaterialTheme.typography.headlineSmall)
            ButtonPair(
                first = { m -> BigTileButton(Icons.Filled.EditNote, stringResource(R.string.home_write_note), SeniorColors.Note, actions.newNote, m) },
                second = { m -> BigTileButton(Icons.Filled.DocumentScanner, stringResource(R.string.home_scan), SeniorColors.Scan, actions.scan, m) },
            )
            ButtonPair(
                first = { m -> BigTileButton(Icons.Filled.Folder, stringResource(R.string.home_my_documents), SeniorColors.Documents, actions.library, m) },
                second = { m -> BigTileButton(Icons.AutoMirrored.Filled.HelpOutline, stringResource(R.string.home_help), SeniorColors.Help, actions.help, m) },
            )
            if (settings.simpleMode) {
                BigTileButton(
                    Icons.Filled.Handyman, stringResource(R.string.home_more_tools), SeniorColors.Tools, actions.tools,
                    Modifier.fillMaxWidth(), subtitle = stringResource(R.string.home_more_tools_sub),
                )
            } else {
                ButtonPair(
                    first = { m -> BigTileButton(Icons.Filled.CallMerge, stringResource(R.string.tool_merge), SeniorColors.Tools, actions.merge, m) },
                    second = { m -> BigTileButton(Icons.Filled.CallSplit, stringResource(R.string.tool_split), SeniorColors.Tools, { actions.pageTool(PageTool.SPLIT) }, m) },
                )
                ButtonPair(
                    first = { m -> BigTileButton(Icons.Filled.DeleteSweep, stringResource(R.string.tool_remove), SeniorColors.Tools, { actions.pageTool(PageTool.REMOVE) }, m) },
                    second = { m -> BigTileButton(Icons.Filled.NoteAdd, stringResource(R.string.tool_add), SeniorColors.Tools, { actions.pageTool(PageTool.ADD) }, m) },
                )
            }
            if (recent.isNotEmpty()) {
                Text(stringResource(R.string.home_recent), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
                recent.forEach { item ->
                    ItemCard(
                        item = item,
                        file = vm.documents.fileOf(item),
                        thumbnails = vm.thumbnails,
                        onClick = { actions.open(item) },
                        badge = if (item.isSample) stringResource(R.string.practice_badge) else null,
                    )
                }
            }
        }
    }

    if (!settings.tourOffered) {
        ConfirmDialog(
            title = stringResource(R.string.tour_offer_title),
            message = stringResource(R.string.tour_offer_message),
            confirmText = stringResource(R.string.tour_offer_yes),
            dismissText = stringResource(R.string.tour_offer_no),
            onConfirm = { vm.tourOffered(); actions.startTour() },
            onDismiss = { vm.tourOffered() },
        )
    }
}

fun ItemEntity.isNote() = type == ItemType.NOTE
