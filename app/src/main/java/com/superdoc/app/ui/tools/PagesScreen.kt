package com.superdoc.app.ui.tools

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.superdoc.app.R
import com.superdoc.app.data.DocumentsRepository
import com.superdoc.app.data.FileStore
import com.superdoc.app.data.ItemEntity
import com.superdoc.app.pdf.DocumentTools
import com.superdoc.app.pdf.PdfEngine
import com.superdoc.app.pdf.PdfLockedException
import com.superdoc.app.pdf.ThumbnailCache
import com.superdoc.app.scan.ScanResult
import com.superdoc.app.scan.ScanSaver
import com.superdoc.app.scan.rememberPhotoPicker
import com.superdoc.app.scan.rememberScanner
import com.superdoc.app.ui.components.BigButton
import com.superdoc.app.ui.components.BigOutlinedButton
import com.superdoc.app.ui.components.BottomActions
import com.superdoc.app.ui.components.BusyOverlay
import com.superdoc.app.ui.components.ButtonPair
import com.superdoc.app.ui.components.Choice
import com.superdoc.app.ui.components.ChoiceDialog
import com.superdoc.app.ui.components.ConfirmDialog
import com.superdoc.app.ui.components.SeniorScaffold
import com.superdoc.app.ui.navigation.PageMode
import com.superdoc.app.ui.navigation.PageTool
import com.superdoc.app.ui.theme.SeniorColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileNotFoundException
import javax.inject.Inject

@HiltViewModel
class PagesViewModel @Inject constructor(
    savedState: SavedStateHandle,
    documents: DocumentsRepository,
    thumbnails: ThumbnailCache,
    private val tools: DocumentTools,
    private val engine: PdfEngine,
    private val saver: ScanSaver,
    private val files: FileStore,
) : ToolViewModel(documents, thumbnails) {
    val id: Long = savedState.get<String>("id")?.toLongOrNull() ?: 0L
    val mode: PageMode = savedState.get<String>("mode")?.let { runCatching { PageMode.valueOf(it) }.getOrNull() } ?: PageMode.REMOVE

    var item by mutableStateOf<ItemEntity?>(null)
        private set
    var file by mutableStateOf<File?>(null)
        private set
    var pageCount by mutableIntStateOf(0)
        private set

    /** Remove: pages (0-based) the user marked. */
    var marked by mutableStateOf(setOf<Int>())
        private set

    /** Reorder: original page numbers (0-based) in their new order. */
    var order by mutableStateOf(listOf<Int>())
        private set
    var lastMoved by mutableStateOf<Int?>(null)
        private set

    /** Add: new pages go before this position (0 = at the start, [pageCount] = at the end). */
    var insertAt by mutableIntStateOf(0)

    val otherPdfs: StateFlow<List<ItemEntity>> = documents.observePdfs()
        .map { list -> list.filter { it.id != id } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            val found = documents.get(id) ?: return@launch
            val f = documents.fileOf(found)
            item = found
            file = f
            pageCount = try {
                f?.let { engine.pageCount(it) } ?: 0
            } catch (e: PdfLockedException) {
                error = R.string.pdf_locked
                0
            }
            order = (0 until pageCount).toList()
            insertAt = pageCount
        }
    }

    fun toggle(page: Int) {
        marked = if (page in marked) marked - page else marked + page
    }

    fun move(position: Int, delta: Int) {
        val target = position + delta
        if (target !in order.indices) return
        order = order.toMutableList().also { val p = it.removeAt(position); it.add(target, p); lastMoved = p }
    }

    fun resetOrder() {
        order = (0 until pageCount).toList()
        lastMoved = null
    }

    val orderChanged: Boolean get() = order != (0 until pageCount).toList()

    fun removeMarked() {
        val doc = item ?: return
        run { listOf(tools.removePages(doc, pageCount, marked)) }
    }

    fun saveOrder() {
        val doc = item ?: return
        run { listOf(tools.reorder(doc, order)) }
    }

    fun addFromScan(result: ScanResult) = add { saver.scanToTempPdf(result) }

    fun addFromPhotos(uris: List<Uri>) = add { saver.photosToTempPdf(uris) }

    fun addFromPhoneFile(uri: Uri) = add { files.copyIntoWork(uri, "pdf") }

    fun addFromDocument(other: ItemEntity) = add { documents.fileOf(other) ?: throw FileNotFoundException(other.title) }

    private fun add(source: suspend () -> File) {
        val doc = item ?: return
        val at = insertAt
        run { listOf(tools.insert(doc, source(), at)) }
    }
}

@Composable
fun PagesScreen(
    vm: PagesViewModel,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onHelp: () -> Unit,
    onOpen: (ItemEntity) -> Unit,
    onDone: () -> Unit,
) {
    val tool = when (vm.mode) {
        PageMode.REMOVE -> PageTool.REMOVE
        PageMode.ADD -> PageTool.ADD
        PageMode.REORDER -> PageTool.REORDER
    }
    var confirming by remember { mutableStateOf(false) }
    var choosingSource by remember { mutableStateOf(false) }
    var choosingDocument by remember { mutableStateOf(false) }
    val startScanner = rememberScanner { vm.addFromScan(it) }
    val pickPhotos = rememberPhotoPicker { vm.addFromPhotos(it) }
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::addFromPhoneFile) }
    val done = vm.results.isNotEmpty()

    SeniorScaffold(
        title = stringResource(tool.title()),
        onBack = onBack,
        onHome = onHome,
        onHelp = onHelp,
        bottomBar = {
            if (!done && vm.pageCount > 0) {
                BottomActions {
                    when (vm.mode) {
                        PageMode.REMOVE -> RemoveActions(vm) { confirming = true }
                        PageMode.ADD -> AddActions(vm) { choosingSource = true }
                        PageMode.REORDER -> ReorderActions(vm)
                    }
                }
            }
        },
    ) { padding ->
        val file = vm.file
        when {
            done -> ToolDonePanel(vm.results, onOpen, onDone, Modifier.padding(padding))
            file == null || vm.pageCount == 0 -> Unit
            vm.mode == PageMode.REMOVE -> RemoveGrid(vm, file, PaddingValues(16.dp), Modifier.padding(padding))
            vm.mode == PageMode.ADD -> InsertPositionList(vm, file, Modifier.padding(padding))
            else -> ReorderList(vm, file, Modifier.padding(padding))
        }
    }

    if (confirming) {
        val pages = vm.marked.sorted().joinToString(", ") { "${it + 1}" }
        ConfirmDialog(
            title = stringResource(R.string.tools_remove_confirm_title),
            message = stringResource(R.string.tools_remove_confirm_message, pages),
            confirmText = stringResource(R.string.tools_remove_button),
            onConfirm = { confirming = false; vm.removeMarked() },
            onDismiss = { confirming = false },
            destructive = true,
        )
    }
    if (choosingSource) {
        ChoiceDialog(
            title = stringResource(R.string.tools_add_from_title),
            choices = listOf(
                Choice(stringResource(R.string.tools_add_camera), Icons.Filled.CameraAlt, SeniorColors.Scan, startScanner),
                Choice(stringResource(R.string.tools_add_photos), Icons.Filled.PhotoLibrary, SeniorColors.Primary, pickPhotos),
                Choice(stringResource(R.string.tools_add_document), Icons.Filled.PictureAsPdf, SeniorColors.Documents) { choosingDocument = true },
                Choice(stringResource(R.string.tools_add_phone_file), Icons.Filled.FolderOpen, SeniorColors.Tools) { pickPdf.launch(arrayOf("application/pdf")) },
            ),
            onDismiss = { choosingSource = false },
        )
    }
    if (choosingDocument) {
        val others by vm.otherPdfs.collectAsState()
        ChoiceDialog(
            title = stringResource(R.string.tools_add_document),
            message = if (others.isEmpty()) stringResource(R.string.tools_no_other_pdfs) else null,
            choices = others.map { Choice(it.title, Icons.Filled.PictureAsPdf, SeniorColors.Documents) { vm.addFromDocument(it) } },
            onDismiss = { choosingDocument = false },
        )
    }
    if (vm.busy) BusyOverlay(stringResource(R.string.tools_working))
    ToolErrorDialog(vm)
}

@Composable
private fun Instruction(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 4.dp))
}

// ---- Remove pages ----

@Composable
private fun RemoveGrid(vm: PagesViewModel, file: File, contentPadding: PaddingValues, modifier: Modifier) {
    val removeLabel = stringResource(R.string.tools_marked_for_removal)
    val keepLabel = stringResource(R.string.tools_kept)
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(2) }) { Instruction(stringResource(R.string.tools_remove_instruction)) }
        items((0 until vm.pageCount).toList(), key = { it }) { page ->
            val isMarked = page in vm.marked
            PageThumbnail(
                file, page, vm.thumbnails,
                modifier = Modifier
                    .clickable(role = Role.Checkbox) { vm.toggle(page) }
                    .semantics { stateDescription = if (isMarked) removeLabel else keepLabel },
                highlighted = isMarked,
                dimmed = isMarked,
            ) {
                if (isMarked) {
                    Box(Modifier.size(72.dp).background(SeniorColors.Danger, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoveActions(vm: PagesViewModel, onRemove: () -> Unit) {
    val count = vm.marked.size
    val all = count == vm.pageCount
    Text(
        when {
            all -> stringResource(R.string.tools_cannot_remove_all)
            count == 0 -> stringResource(R.string.tools_none_marked)
            else -> pluralStringResource(R.plurals.tools_marked_count, count, count)
        },
        style = MaterialTheme.typography.titleMedium,
        color = if (all) SeniorColors.Danger else Color.Unspecified,
    )
    BigButton(stringResource(R.string.tools_remove_button), onRemove, icon = Icons.Filled.Delete, color = SeniorColors.Danger, enabled = count > 0 && !all)
}

// ---- Add pages ----

@Composable
private fun InsertPositionList(vm: PagesViewModel, file: File, modifier: Modifier) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Instruction(stringResource(R.string.tools_add_instruction)) }
        for (position in 0..vm.pageCount) {
            item(key = "slot$position") { InsertSlot(position == vm.insertAt) { vm.insertAt = position } }
            if (position < vm.pageCount) {
                item(key = "page$position") {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        PageThumbnail(file, position, vm.thumbnails, Modifier.width(170.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun InsertSlot(selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        BigButton(stringResource(R.string.tools_slot_chosen), onClick, icon = Icons.Filled.CheckCircle, color = SeniorColors.SavedGreen)
    } else {
        BigOutlinedButton(stringResource(R.string.tools_slot_put_here), onClick, icon = Icons.Filled.AddCircle)
    }
}

@Composable
private fun AddActions(vm: PagesViewModel, onChoose: () -> Unit) {
    val where = when (vm.insertAt) {
        0 -> stringResource(R.string.tools_position_start)
        vm.pageCount -> stringResource(R.string.tools_position_end)
        else -> stringResource(R.string.tools_position_after, vm.insertAt)
    }
    Text(stringResource(R.string.tools_position_label, where), style = MaterialTheme.typography.titleMedium)
    BigButton(stringResource(R.string.tools_add_choose_pages), onChoose, icon = Icons.Filled.AddCircle, color = SeniorColors.Tools)
}

// ---- Reorder ----

@Composable
private fun ReorderList(vm: PagesViewModel, file: File, modifier: Modifier) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Instruction(stringResource(R.string.tools_reorder_instruction)) }
        items(vm.order.size, key = { vm.order[it] }) { position ->
            val page = vm.order[position]
            val moved = page == vm.lastMoved
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (moved) Modifier.border(4.dp, SeniorColors.Highlight, RoundedCornerShape(16.dp)) else Modifier),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    PageThumbnail(file, page, vm.thumbnails, Modifier.width(110.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.tools_place, position + 1),
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Start,
                        )
                        BigOutlinedButton(stringResource(R.string.tools_move_up), { vm.move(position, -1) }, icon = Icons.Filled.ArrowUpward, enabled = position > 0)
                        BigOutlinedButton(stringResource(R.string.tools_move_down), { vm.move(position, 1) }, icon = Icons.Filled.ArrowDownward, enabled = position < vm.order.lastIndex)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReorderActions(vm: PagesViewModel) {
    if (vm.orderChanged) {
        ButtonPair(
            { m -> BigOutlinedButton(stringResource(R.string.tools_start_again), vm::resetOrder, m, icon = Icons.Filled.Refresh) },
            { m -> BigButton(stringResource(R.string.tools_save_order), vm::saveOrder, m, icon = Icons.Filled.Save, color = SeniorColors.SavedGreen) },
        )
    } else {
        Text(stringResource(R.string.tools_order_unchanged), style = MaterialTheme.typography.titleMedium)
    }
}
