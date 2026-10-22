package com.superdoc.app.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.JoinFull
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.superdoc.app.R
import com.superdoc.app.data.DocumentsRepository
import com.superdoc.app.data.ItemEntity
import com.superdoc.app.pdf.DocumentTools
import com.superdoc.app.pdf.ThumbnailCache
import com.superdoc.app.ui.components.BigButton
import com.superdoc.app.ui.components.BigOutlinedButton
import com.superdoc.app.ui.components.BottomActions
import com.superdoc.app.ui.components.BusyOverlay
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
import javax.inject.Inject

@HiltViewModel
class MergeViewModel @Inject constructor(
    documents: DocumentsRepository,
    thumbnails: ThumbnailCache,
    private val tools: DocumentTools,
) : ToolViewModel(documents, thumbnails) {
    val pdfs: StateFlow<List<ItemEntity>?> = documents.observePdfs().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Chosen documents in the order they were tapped; that is the order they are joined in. */
    var chosen by mutableStateOf(listOf<Long>())
        private set

    fun toggle(id: Long) {
        chosen = if (id in chosen) chosen - id else chosen + id
    }

    fun clear() {
        chosen = emptyList()
    }

    fun merge() {
        val byId = pdfs.value.orEmpty().associateBy { it.id }
        val items = chosen.mapNotNull { byId[it] }
        if (items.size < 2) return
        run { listOf(tools.merge(items)) }
    }
}

@Composable
fun MergeScreen(
    vm: MergeViewModel,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onHelp: () -> Unit,
    onOpen: (ItemEntity) -> Unit,
    onDone: () -> Unit,
) {
    val pdfs by vm.pdfs.collectAsState()
    var confirming by remember { mutableStateOf(false) }
    val done = vm.results.isNotEmpty()

    SeniorScaffold(
        title = stringResource(R.string.tool_merge),
        onBack = onBack,
        onHome = onHome,
        onHelp = onHelp,
        bottomBar = {
            if (!done && !pdfs.isNullOrEmpty()) {
                BottomActions {
                    Text(
                        when (vm.chosen.size) {
                            0 -> stringResource(R.string.tools_merge_none)
                            1 -> stringResource(R.string.tools_merge_one_more)
                            else -> stringResource(R.string.tools_merge_count, vm.chosen.size)
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    val join: @Composable (Modifier) -> Unit = { m ->
                        BigButton(stringResource(R.string.tools_merge_button), { confirming = true }, m, icon = Icons.Filled.JoinFull, color = SeniorColors.Tools, enabled = vm.chosen.size >= 2)
                    }
                    if (vm.chosen.isNotEmpty()) {
                        ButtonPair({ m -> BigOutlinedButton(stringResource(R.string.tools_start_again), vm::clear, m, icon = Icons.Filled.Refresh) }, join)
                    } else {
                        join(Modifier)
                    }
                }
            }
        },
    ) { padding ->
        val list = pdfs ?: return@SeniorScaffold
        when {
            done -> ToolDonePanel(vm.results, onOpen, onDone, Modifier.padding(padding), R.string.tools_originals_kept)
            list.size < 2 -> Box(Modifier.padding(padding)) { EmptyState(stringResource(R.string.tools_merge_need_two)) }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Text(stringResource(R.string.tools_merge_instruction), style = MaterialTheme.typography.bodyLarge) }
                items(list, key = { it.id }) { item ->
                    val position = vm.chosen.indexOf(item.id)
                    ItemCard(
                        item, vm.documents.fileOf(item), vm.thumbnails,
                        onClick = { vm.toggle(item.id) },
                        selected = position >= 0,
                        badge = if (position >= 0) stringResource(R.string.tools_merge_position, position + 1) else null,
                    )
                }
            }
        }
    }

    if (confirming) {
        val byId = pdfs.orEmpty().associateBy { it.id }
        val names = vm.chosen.mapIndexedNotNull { i, id -> byId[id]?.let { "${i + 1}. ${it.title}" } }.joinToString("\n")
        ConfirmDialog(
            title = stringResource(R.string.tools_merge_confirm_title),
            message = names + "\n\n" + stringResource(R.string.tools_originals_kept),
            confirmText = stringResource(R.string.tools_merge_button),
            onConfirm = { confirming = false; vm.merge() },
            onDismiss = { confirming = false },
        )
    }
    if (vm.busy) BusyOverlay(stringResource(R.string.tools_working))
    ToolErrorDialog(vm)
}
