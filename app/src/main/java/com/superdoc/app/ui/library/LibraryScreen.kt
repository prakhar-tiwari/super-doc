package com.superdoc.app.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.superdoc.app.R
import com.superdoc.app.data.DocumentsRepository
import com.superdoc.app.data.FileStore
import com.superdoc.app.data.ItemEntity
import com.superdoc.app.data.ItemType
import com.superdoc.app.pdf.ThumbnailCache
import com.superdoc.app.ui.components.BigButton
import com.superdoc.app.ui.components.BigOutlinedButton
import com.superdoc.app.ui.components.ButtonPair
import com.superdoc.app.ui.components.ConfirmDialog
import com.superdoc.app.ui.components.EmptyState
import com.superdoc.app.ui.components.ItemCard
import com.superdoc.app.ui.components.SendItemDialog
import com.superdoc.app.ui.components.SeniorScaffold
import com.superdoc.app.ui.components.VoiceInputButton
import com.superdoc.app.ui.note.LanguageChip
import com.superdoc.app.ui.theme.SeniorColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibraryFilter { ALL, NOTES, DOCUMENTS }

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    val documents: DocumentsRepository,
    val thumbnails: ThumbnailCache,
    val files: FileStore,
) : ViewModel() {
    val query = MutableStateFlow("")
    val filter = MutableStateFlow(LibraryFilter.ALL)

    val items: StateFlow<List<ItemEntity>?> = combine(query.flatMapLatest { documents.observeLibrary(it) }, filter) { list, f ->
        when (f) {
            LibraryFilter.ALL -> list
            LibraryFilter.NOTES -> list.filter { it.type == ItemType.NOTE }
            LibraryFilter.DOCUMENTS -> list.filter { it.type != ItemType.NOTE }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun delete(item: ItemEntity) = viewModelScope.launch { documents.moveToRecycleBin(item.id) }
    fun restore(item: ItemEntity) = viewModelScope.launch { documents.restore(item.id) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(
    vm: LibraryViewModel,
    onOpen: (ItemEntity) -> Unit,
    onRecycleBin: () -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onHelp: () -> Unit,
    onMakePdf: (Long) -> Unit,
) {
    val items by vm.items.collectAsState()
    val query by vm.query.collectAsState()
    val filter by vm.filter.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var toDelete by remember { mutableStateOf<ItemEntity?>(null) }
    var toSend by remember { mutableStateOf<ItemEntity?>(null) }
    val movedMsg = stringResource(R.string.moved_to_recycle_bin)
    val undoLabel = stringResource(R.string.action_undo)

    SeniorScaffold(
        title = stringResource(R.string.home_my_documents),
        onBack = onBack, onHome = onHome, onHelp = onHelp,
        snackbarHostState = snackbar,
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { vm.query.value = it },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            IconButton(onClick = { vm.query.value = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.library_clear_search))
                            }
                        }
                    } else null,
                    placeholder = { Text(stringResource(R.string.library_search_hint), style = MaterialTheme.typography.bodyLarge) },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item { VoiceInputButton(onText = { vm.query.value = it }, replace = true, label = stringResource(R.string.library_speak_search)) }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LanguageChip(stringResource(R.string.filter_all), filter == LibraryFilter.ALL) { vm.filter.value = LibraryFilter.ALL }
                    LanguageChip(stringResource(R.string.filter_notes), filter == LibraryFilter.NOTES) { vm.filter.value = LibraryFilter.NOTES }
                    LanguageChip(stringResource(R.string.filter_documents), filter == LibraryFilter.DOCUMENTS) { vm.filter.value = LibraryFilter.DOCUMENTS }
                }
            }
            val list = items
            if (list != null && list.isEmpty()) {
                item {
                    EmptyState(
                        stringResource(if (query.isBlank()) R.string.library_empty else R.string.library_no_results, query)
                    )
                }
            }
            items(list.orEmpty(), key = { it.id }) { item ->
                ItemCard(
                    item = item,
                    file = vm.documents.fileOf(item),
                    thumbnails = vm.thumbnails,
                    onClick = { onOpen(item) },
                    badge = if (item.isSample) stringResource(R.string.practice_badge) else null,
                    footer = {
                        ButtonPair(
                            first = { m ->
                                BigButton(stringResource(R.string.note_send), { toSend = item }, m, Icons.AutoMirrored.Filled.Send, SeniorColors.WhatsApp)
                            },
                            second = { m ->
                                BigOutlinedButton(stringResource(R.string.action_delete), { toDelete = item }, m, Icons.Filled.Delete, SeniorColors.Danger)
                            },
                        )
                    },
                )
            }
            item {
                BigOutlinedButton(
                    text = stringResource(R.string.recycle_bin_open),
                    icon = Icons.Filled.Restore,
                    onClick = onRecycleBin,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }

    toDelete?.let { item ->
        ConfirmDialog(
            title = stringResource(R.string.delete_title, item.title),
            message = stringResource(R.string.delete_message_recycle),
            confirmText = stringResource(R.string.delete_yes),
            destructive = true,
            onConfirm = {
                toDelete = null
                vm.delete(item)
                scope.launch {
                    val result = snackbar.showSnackbar(movedMsg, actionLabel = undoLabel, duration = SnackbarDuration.Long)
                    if (result == SnackbarResult.ActionPerformed) vm.restore(item)
                }
            },
            onDismiss = { toDelete = null },
        )
    }

    toSend?.let { item ->
        SendItemDialog(
            item = item,
            file = vm.documents.fileOf(item),
            files = vm.files,
            onDismiss = { toSend = null },
            onMakePdf = { onMakePdf(item.id) },
        )
    }
}
