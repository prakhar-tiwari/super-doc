package com.superdoc.app.ui.tools

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.superdoc.app.R
import com.superdoc.app.data.DocumentsRepository
import com.superdoc.app.data.ItemEntity
import com.superdoc.app.pdf.DocumentTools
import com.superdoc.app.pdf.PdfEngine
import com.superdoc.app.pdf.PdfLockedException
import com.superdoc.app.pdf.ThumbnailCache
import com.superdoc.app.ui.components.BigButton
import com.superdoc.app.ui.components.BigOutlinedButton
import com.superdoc.app.ui.components.BottomActions
import com.superdoc.app.ui.components.BusyOverlay
import com.superdoc.app.ui.components.ConfirmDialog
import com.superdoc.app.ui.components.EmptyState
import com.superdoc.app.ui.components.SeniorScaffold
import com.superdoc.app.ui.theme.SeniorColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class SplitWay { EVERY_PAGE, CHOOSE_PAGES, CUT_IN_TWO }

@HiltViewModel
class SplitViewModel @Inject constructor(
    savedState: SavedStateHandle,
    documents: DocumentsRepository,
    thumbnails: ThumbnailCache,
    private val tools: DocumentTools,
    private val engine: PdfEngine,
) : ToolViewModel(documents, thumbnails) {
    val id: Long = savedState.get<String>("id")?.toLongOrNull() ?: 0L

    var item by mutableStateOf<ItemEntity?>(null)
        private set
    var file by mutableStateOf<File?>(null)
        private set
    var pageCount by mutableIntStateOf(-1)
        private set
    var way by mutableStateOf<SplitWay?>(null)
    var chosen by mutableStateOf(setOf<Int>())
        private set

    /** Cut in two: the first part ends after this page (1-based). */
    var cutAfter by mutableIntStateOf(1)

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
            cutAfter = (pageCount / 2).coerceAtLeast(1)
        }
    }

    fun toggle(page: Int) {
        chosen = if (page in chosen) chosen - page else chosen + page
    }

    fun split() {
        val doc = item ?: return
        val parts = when (way ?: return) {
            SplitWay.EVERY_PAGE -> DocumentTools.everyPage(pageCount)
            SplitWay.CHOOSE_PAGES -> listOf(chosen.sorted())
            SplitWay.CUT_IN_TWO -> DocumentTools.cutInTwo(pageCount, cutAfter)
        }
        run { tools.split(doc, parts) }
    }
}

@Composable
fun SplitScreen(
    vm: SplitViewModel,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onHelp: () -> Unit,
    onOpen: (ItemEntity) -> Unit,
    onDone: () -> Unit,
) {
    var confirming by remember { mutableStateOf(false) }
    val done = vm.results.isNotEmpty()
    val way = vm.way
    val back = if (way != null && !done) ({ vm.way = null }) else onBack
    BackHandler(enabled = way != null && !done) { vm.way = null }

    SeniorScaffold(
        title = stringResource(R.string.tool_split),
        onBack = back,
        onHome = onHome,
        onHelp = onHelp,
        bottomBar = {
            if (!done && way != null && way != SplitWay.EVERY_PAGE) {
                BottomActions {
                    val ready = when (way) {
                        SplitWay.CHOOSE_PAGES -> vm.chosen.isNotEmpty()
                        else -> true
                    }
                    if (way == SplitWay.CHOOSE_PAGES) {
                        Text(
                            if (vm.chosen.isEmpty()) stringResource(R.string.tools_none_chosen) else pluralStringResource(R.plurals.tools_chosen_count, vm.chosen.size, vm.chosen.size),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    BigButton(
                        stringResource(if (way == SplitWay.CUT_IN_TWO) R.string.tools_cut_button else R.string.tools_make_from_chosen),
                        { confirming = true },
                        icon = if (way == SplitWay.CUT_IN_TWO) Icons.Filled.ContentCut else Icons.Filled.FileCopy,
                        color = SeniorColors.Tools,
                        enabled = ready,
                    )
                }
            }
        },
    ) { padding ->
        val file = vm.file
        val modifier = Modifier.padding(padding)
        when {
            done -> ToolDonePanel(vm.results, onOpen, onDone, modifier)
            vm.pageCount < 0 || file == null -> Unit
            vm.pageCount < 2 -> Box(modifier) { EmptyState(stringResource(R.string.tools_split_one_page)) }
            way == null || way == SplitWay.EVERY_PAGE -> SplitChoices(vm, modifier) { chosenWay ->
                vm.way = chosenWay
                if (chosenWay == SplitWay.EVERY_PAGE) confirming = true
            }
            way == SplitWay.CHOOSE_PAGES -> ChoosePagesGrid(vm, file, modifier)
            else -> CutPositionList(vm, file, modifier)
        }
    }

    if (confirming) {
        val message = when (way) {
            SplitWay.EVERY_PAGE -> stringResource(R.string.tools_split_every_confirm, vm.pageCount)
            SplitWay.CHOOSE_PAGES -> stringResource(R.string.tools_split_chosen_confirm, vm.chosen.sorted().joinToString(", ") { "${it + 1}" })
            else -> stringResource(R.string.tools_split_cut_confirm, vm.cutAfter, vm.cutAfter + 1)
        }
        ConfirmDialog(
            title = stringResource(R.string.tool_split),
            message = message + "\n\n" + stringResource(R.string.tools_original_kept),
            confirmText = stringResource(R.string.tools_yes_make),
            onConfirm = { confirming = false; vm.split() },
            onDismiss = { confirming = false; if (way == SplitWay.EVERY_PAGE) vm.way = null },
        )
    }
    if (vm.busy) BusyOverlay(stringResource(R.string.tools_working))
    ToolErrorDialog(vm)
}

@Composable
private fun SplitChoices(vm: SplitViewModel, modifier: Modifier, onChoose: (SplitWay) -> Unit) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.tools_split_question, vm.pageCount), style = MaterialTheme.typography.titleLarge)
        BigButton(stringResource(R.string.tools_split_every), { onChoose(SplitWay.EVERY_PAGE) }, icon = Icons.AutoMirrored.Filled.CallSplit, color = SeniorColors.Tools)
        Text(stringResource(R.string.tools_split_every_sub, vm.pageCount), style = MaterialTheme.typography.bodyMedium)
        BigButton(stringResource(R.string.tools_split_choose), { onChoose(SplitWay.CHOOSE_PAGES) }, icon = Icons.Filled.TouchApp, color = SeniorColors.Tools)
        Text(stringResource(R.string.tools_split_choose_sub), style = MaterialTheme.typography.bodyMedium)
        BigButton(stringResource(R.string.tools_split_cut), { onChoose(SplitWay.CUT_IN_TWO) }, icon = Icons.Filled.ContentCut, color = SeniorColors.Tools)
        Text(stringResource(R.string.tools_split_cut_sub), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ChoosePagesGrid(vm: SplitViewModel, file: File, modifier: Modifier) {
    val chosenLabel = stringResource(R.string.tools_chosen)
    val notChosenLabel = stringResource(R.string.tools_not_chosen)
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(2) }) { Text(stringResource(R.string.tools_choose_instruction), style = MaterialTheme.typography.bodyLarge) }
        items((0 until vm.pageCount).toList(), key = { it }) { page ->
            val isChosen = page in vm.chosen
            PageThumbnail(
                file, page, vm.thumbnails,
                modifier = Modifier
                    .clickable(role = Role.Checkbox) { vm.toggle(page) }
                    .semantics { stateDescription = if (isChosen) chosenLabel else notChosenLabel },
                highlighted = isChosen,
            ) {
                if (isChosen) {
                    Box(Modifier.size(72.dp).background(SeniorColors.SavedGreen, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CutPositionList(vm: SplitViewModel, file: File, modifier: Modifier) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text(stringResource(R.string.tools_cut_instruction), style = MaterialTheme.typography.bodyLarge) }
        for (page in 0 until vm.pageCount) {
            item(key = "page$page") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    PageThumbnail(file, page, vm.thumbnails, Modifier.width(170.dp))
                }
            }
            val after = page + 1
            if (after < vm.pageCount) {
                item(key = "cut$after") {
                    if (vm.cutAfter == after) {
                        BigButton(stringResource(R.string.tools_cut_chosen), { vm.cutAfter = after }, icon = Icons.Filled.ContentCut, color = SeniorColors.SavedGreen)
                    } else {
                        BigOutlinedButton(stringResource(R.string.tools_cut_here), { vm.cutAfter = after }, icon = Icons.Filled.ContentCut)
                    }
                }
            }
        }
    }
}
