package com.superdoc.app.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.JoinFull
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.superdoc.app.R
import com.superdoc.app.data.DocumentsRepository
import com.superdoc.app.data.ItemEntity
import com.superdoc.app.pdf.ThumbnailCache
import com.superdoc.app.ui.components.EmptyState
import com.superdoc.app.ui.components.ItemCard
import com.superdoc.app.ui.components.SeniorScaffold
import com.superdoc.app.ui.navigation.PageTool
import com.superdoc.app.ui.theme.SeniorColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

fun PageTool.icon(): ImageVector = when (this) {
    PageTool.SPLIT -> Icons.AutoMirrored.Filled.CallSplit
    PageTool.REMOVE -> Icons.Filled.DeleteSweep
    PageTool.ADD -> Icons.Filled.AddCircle
    PageTool.REORDER -> Icons.Filled.SwapVert
}

fun PageTool.title(): Int = when (this) {
    PageTool.SPLIT -> R.string.tool_split
    PageTool.REMOVE -> R.string.tool_remove
    PageTool.ADD -> R.string.tool_add
    PageTool.REORDER -> R.string.tool_reorder
}

private fun PageTool.subtitle(): Int = when (this) {
    PageTool.SPLIT -> R.string.tool_split_sub
    PageTool.REMOVE -> R.string.tool_remove_sub
    PageTool.ADD -> R.string.tool_add_sub
    PageTool.REORDER -> R.string.tool_reorder_sub
}

/** "More tools": every page tool as one big, explained card. */
@Composable
fun ToolsScreen(onBack: () -> Unit, onHome: () -> Unit, onHelp: () -> Unit, onMerge: () -> Unit, onTool: (PageTool) -> Unit) {
    SeniorScaffold(title = stringResource(R.string.tools_title), onBack = onBack, onHome = onHome, onHelp = onHelp) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.tools_intro), style = MaterialTheme.typography.bodyLarge)
            ToolCard(Icons.Filled.JoinFull, R.string.tool_merge, R.string.tool_merge_sub, onMerge)
            PageTool.entries.forEach { tool -> ToolCard(tool.icon(), tool.title(), tool.subtitle()) { onTool(tool) } }
        }
    }
}

@Composable
private fun ToolCard(icon: ImageVector, title: Int, subtitle: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                Modifier
                    .size(64.dp)
                    .background(SeniorColors.Tools, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@HiltViewModel
class PickDocumentViewModel @Inject constructor(
    savedState: SavedStateHandle,
    val documents: DocumentsRepository,
    val thumbnails: ThumbnailCache,
) : ViewModel() {
    val tool: PageTool = savedState.get<String>("tool")?.let { runCatching { PageTool.valueOf(it) }.getOrNull() } ?: PageTool.REMOVE
    val pdfs: StateFlow<List<ItemEntity>?> = documents.observePdfs().stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

/** "Which document?" before a page tool. Only PDFs are listed, because only they have pages to change. */
@Composable
fun PickDocumentScreen(
    vm: PickDocumentViewModel,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onHelp: () -> Unit,
    onPicked: (PageTool, ItemEntity) -> Unit,
) {
    val pdfs by vm.pdfs.collectAsState()
    SeniorScaffold(title = stringResource(vm.tool.title()), onBack = onBack, onHome = onHome, onHelp = onHelp) { padding ->
        val list = pdfs ?: return@SeniorScaffold
        if (list.isEmpty()) {
            Box(Modifier.padding(padding)) { EmptyState(stringResource(R.string.tools_no_pdfs)) }
            return@SeniorScaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text(stringResource(R.string.tools_pick_document), style = MaterialTheme.typography.titleLarge) }
            items(list, key = { it.id }) { item ->
                ItemCard(item, vm.documents.fileOf(item), vm.thumbnails, onClick = { onPicked(vm.tool, item) })
            }
        }
    }
}
