package com.superdoc.app.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
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
import com.superdoc.app.data.ItemEntity
import com.superdoc.app.pdf.ThumbnailCache
import com.superdoc.app.ui.components.BigButton
import com.superdoc.app.ui.components.BigOutlinedButton
import com.superdoc.app.ui.components.ButtonPair
import com.superdoc.app.ui.components.ConfirmDialog
import com.superdoc.app.ui.components.EmptyState
import com.superdoc.app.ui.components.ItemCard
import com.superdoc.app.ui.components.SeniorScaffold
import com.superdoc.app.ui.theme.SeniorColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecycleBinViewModel @Inject constructor(
    val documents: DocumentsRepository,
    val thumbnails: ThumbnailCache,
) : ViewModel() {
    val items: StateFlow<List<ItemEntity>?> = documents.observeRecycleBin()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun restore(item: ItemEntity) = viewModelScope.launch { documents.restore(item.id) }
    fun deleteForever(item: ItemEntity) = viewModelScope.launch { documents.deleteForever(item.id) }
    fun emptyAll() = viewModelScope.launch { documents.emptyRecycleBin() }
}

@Composable
fun RecycleBinScreen(vm: RecycleBinViewModel, onBack: () -> Unit, onHome: () -> Unit, onHelp: () -> Unit) {
    val items by vm.items.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var forever by remember { mutableStateOf<ItemEntity?>(null) }
    var emptyAll by remember { mutableStateOf(false) }
    val restoredMsg = stringResource(R.string.recycle_restored)

    SeniorScaffold(
        title = stringResource(R.string.recycle_bin_title),
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
                Text(
                    stringResource(R.string.recycle_bin_info, DocumentsRepository.RECYCLE_DAYS.toInt()),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            val list = items
            if (list != null && list.isEmpty()) {
                item { EmptyState(stringResource(R.string.recycle_bin_empty)) }
            }
            items(list.orEmpty(), key = { it.id }) { item ->
                ItemCard(
                    item = item,
                    file = vm.documents.fileOf(item),
                    thumbnails = vm.thumbnails,
                    onClick = {},
                    footer = {
                        ButtonPair(
                            first = { m ->
                                BigButton(
                                    stringResource(R.string.recycle_restore),
                                    {
                                        vm.restore(item)
                                        scope.launch { snackbar.showSnackbar(restoredMsg) }
                                    },
                                    m, Icons.Filled.Restore, SeniorColors.SavedGreen,
                                )
                            },
                            second = { m ->
                                BigOutlinedButton(stringResource(R.string.recycle_delete_forever), { forever = item }, m, Icons.Filled.DeleteForever, SeniorColors.Danger)
                            },
                        )
                    },
                )
            }
            if (!list.isNullOrEmpty()) {
                item {
                    BigOutlinedButton(
                        text = stringResource(R.string.recycle_empty_all),
                        icon = Icons.Filled.DeleteForever,
                        color = SeniorColors.Danger,
                        onClick = { emptyAll = true },
                    )
                }
            }
        }
    }

    forever?.let { item ->
        ConfirmDialog(
            title = stringResource(R.string.recycle_delete_forever_title),
            message = stringResource(R.string.recycle_delete_forever_message, item.title),
            confirmText = stringResource(R.string.recycle_delete_forever),
            destructive = true,
            onConfirm = { forever = null; vm.deleteForever(item) },
            onDismiss = { forever = null },
        )
    }
    if (emptyAll) {
        ConfirmDialog(
            title = stringResource(R.string.recycle_empty_all),
            message = stringResource(R.string.recycle_empty_all_message),
            confirmText = stringResource(R.string.recycle_empty_all),
            destructive = true,
            onConfirm = { emptyAll = false; vm.emptyAll() },
            onDismiss = { emptyAll = false },
        )
    }
}
