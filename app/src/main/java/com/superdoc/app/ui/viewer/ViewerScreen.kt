package com.superdoc.app.ui.viewer

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.superdoc.app.R
import com.superdoc.app.data.ItemEntity
import com.superdoc.app.data.ItemType
import com.superdoc.app.share.SaveToPhone
import com.superdoc.app.share.ShareHelper
import com.superdoc.app.ui.components.BigButton
import com.superdoc.app.ui.components.BigOutlinedButton
import com.superdoc.app.ui.components.BottomActions
import com.superdoc.app.ui.components.BusyOverlay
import com.superdoc.app.ui.components.ButtonPair
import com.superdoc.app.ui.components.Choice
import com.superdoc.app.ui.components.ChoiceDialog
import com.superdoc.app.ui.components.ConfirmDialog
import com.superdoc.app.ui.components.MessageDialog
import com.superdoc.app.ui.components.SendItemDialog
import com.superdoc.app.ui.components.SeniorScaffold
import com.superdoc.app.ui.components.TextInputDialog
import com.superdoc.app.ui.components.pagesLabel
import com.superdoc.app.ui.navigation.PageMode
import com.superdoc.app.ui.theme.SeniorColors
import kotlinx.coroutines.launch
import java.io.File

data class ViewerActions(
    val back: () -> Unit,
    val home: () -> Unit,
    val help: () -> Unit,
    val pages: (PageMode) -> Unit,
    val split: () -> Unit,
    val deleted: () -> Unit,
)

@Composable
fun ViewerScreen(vm: ViewerViewModel, actions: ViewerActions) {
    val item by vm.item.collectAsState()
    val current = item
    SeniorScaffold(
        title = current?.title.orEmpty(),
        onBack = actions.back, onHome = actions.home, onHelp = actions.help,
        snackbarHostState = remember { SnackbarHostState() },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                current == null -> Unit
                current.type == ItemType.PDF -> PdfViewer(vm, current, actions)
                else -> WordFilePanel(vm, current, actions)
            }
        }
    }
    vm.busyMessage?.let { BusyOverlay(stringResource(it)) }
    vm.message?.let { MessageDialog(current?.title.orEmpty(), it, onDismiss = { vm.message = null }) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PdfViewer(vm: ViewerViewModel, item: ItemEntity, actions: ViewerActions) {
    val file = vm.fileOf(item) ?: return
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val speaking by vm.speaker.speaking.collectAsState()
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var sending by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    var dialogs by remember { mutableStateOf(ViewerDialog.NONE) }
    val pageCount = item.pageCount.coerceAtLeast(1)
    val currentPage by remember { derivedStateOf { listState.firstVisibleItemIndex } }

    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }

    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
                .background(Color(0xFFE0E0E0))
                .transformable(transform, canPan = { scale > 1f }),
        ) {
            val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
            ) {
                items(pageCount) { index -> PdfPage(vm, file, index, widthPx) }
            }
            Column(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ZoomButton(stringResource(R.string.viewer_bigger), Icons.Filled.ZoomIn) { scale = (scale + 0.5f).coerceAtMost(MAX_ZOOM) }
                ZoomButton(stringResource(R.string.viewer_smaller), Icons.Filled.ZoomOut) {
                    scale = (scale - 0.5f).coerceAtLeast(1f)
                    if (scale == 1f) offset = Offset.Zero
                }
            }
        }
        BottomActions {
            Text(
                stringResource(R.string.viewer_page_of, currentPage + 1, pageCount),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (pageCount > 1) {
                ButtonPair(
                    first = { m ->
                        BigOutlinedButton(
                            stringResource(R.string.viewer_previous), { scope.launch { listState.animateScrollToItem((currentPage - 1).coerceAtLeast(0)) } },
                            m, Icons.AutoMirrored.Filled.ArrowBack, enabled = currentPage > 0,
                        )
                    },
                    second = { m ->
                        BigOutlinedButton(
                            stringResource(R.string.viewer_next), { scope.launch { listState.animateScrollToItem((currentPage + 1).coerceAtMost(pageCount - 1)) } },
                            m, Icons.AutoMirrored.Filled.ArrowForward, enabled = currentPage < pageCount - 1,
                        )
                    },
                )
            }
            ButtonPair(
                first = { m -> BigButton(stringResource(R.string.note_send), { sending = true }, m, Icons.AutoMirrored.Filled.Send, SeniorColors.WhatsApp) },
                second = { m -> BigOutlinedButton(stringResource(R.string.viewer_more_options), { more = true }, m, Icons.Filled.MoreHoriz) },
            )
            if (speaking) {
                BigButton(stringResource(R.string.note_stop_reading), { vm.speaker.stop() }, icon = Icons.Filled.Stop, color = SeniorColors.Danger)
            }
        }
    }

    if (sending) SendItemDialog(item, file, vm.files, onDismiss = { sending = false })

    if (more) {
        ChoiceDialog(
            title = stringResource(R.string.viewer_more_options),
            message = item.title,
            onDismiss = { more = false },
            choices = listOf(
                Choice(stringResource(R.string.viewer_read_page, currentPage + 1), Icons.AutoMirrored.Filled.VolumeUp, SeniorColors.Help) {
                    vm.readPage(item, currentPage) { dialogs = ViewerDialog.TTS_MISSING }
                },
                Choice(stringResource(R.string.tool_remove), Icons.Filled.Delete, SeniorColors.Tools) { actions.pages(PageMode.REMOVE) },
                Choice(stringResource(R.string.tool_add), Icons.Filled.Description, SeniorColors.Tools) { actions.pages(PageMode.ADD) },
                Choice(stringResource(R.string.tool_reorder), Icons.Filled.Description, SeniorColors.Tools) { actions.pages(PageMode.REORDER) },
                Choice(stringResource(R.string.tool_split), Icons.Filled.Description, SeniorColors.Tools, actions.split),
                Choice(stringResource(R.string.action_rename), Icons.Filled.Edit, SeniorColors.Primary) { dialogs = ViewerDialog.RENAME },
                Choice(stringResource(R.string.viewer_save_copy), Icons.Filled.Download, SeniorColors.Primary) { dialogs = ViewerDialog.SAVE },
                Choice(stringResource(R.string.viewer_delete), Icons.Filled.Delete, SeniorColors.Danger) { dialogs = ViewerDialog.DELETE },
            ),
        )
    }
    CommonDialogs(vm, item, dialogs, { dialogs = it }, actions)
}

private enum class ViewerDialog { NONE, RENAME, DELETE, SAVE, TTS_MISSING }

@Composable
private fun CommonDialogs(
    vm: ViewerViewModel,
    item: ItemEntity,
    dialog: ViewerDialog,
    set: (ViewerDialog) -> Unit,
    actions: ViewerActions,
) {
    val context = LocalContext.current
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.saveToPhone(item)
    }
    when (dialog) {
        ViewerDialog.NONE -> Unit
        ViewerDialog.RENAME -> TextInputDialog(
            title = stringResource(R.string.rename_title),
            initial = item.title,
            label = stringResource(R.string.rename_label),
            confirmText = stringResource(R.string.action_save),
            onConfirm = { vm.rename(it); set(ViewerDialog.NONE) },
            onDismiss = { set(ViewerDialog.NONE) },
        )
        ViewerDialog.DELETE -> ConfirmDialog(
            title = stringResource(R.string.delete_title, item.title),
            message = stringResource(R.string.delete_message_recycle),
            confirmText = stringResource(R.string.delete_yes),
            destructive = true,
            onConfirm = { set(ViewerDialog.NONE); vm.delete(actions.deleted) },
            onDismiss = { set(ViewerDialog.NONE) },
        )
        ViewerDialog.SAVE -> ConfirmDialog(
            title = stringResource(R.string.viewer_save_copy),
            message = stringResource(R.string.viewer_save_copy_explain, SaveToPhone.FOLDER),
            confirmText = stringResource(R.string.action_save),
            onConfirm = {
                set(ViewerDialog.NONE)
                if (SaveToPhone.needsLegacyPermission) {
                    storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                } else {
                    vm.saveToPhone(item)
                }
            },
            onDismiss = { set(ViewerDialog.NONE) },
        )
        ViewerDialog.TTS_MISSING -> MessageDialog(
            title = stringResource(R.string.tts_missing_title),
            message = stringResource(R.string.tts_missing_message),
            onDismiss = { set(ViewerDialog.NONE) },
            extra = {
                BigOutlinedButton(stringResource(R.string.tts_install), {
                    runCatching { context.startActivity(vm.speaker.installVoiceDataIntent()) }
                    set(ViewerDialog.NONE)
                })
            },
        )
    }
}

@Composable
private fun PdfPage(vm: ViewerViewModel, file: File, index: Int, widthPx: Int) {
    var bitmap by remember(file, index) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(file, file.lastModified(), index, widthPx) { bitmap = vm.thumbnails.render(file, index, widthPx) }
    val bmp = bitmap
    Box(
        Modifier
            .fillMaxWidth()
            .then(if (bmp != null) Modifier.aspectRatio(bmp.width.toFloat() / bmp.height) else Modifier.aspectRatio(A4_RATIO))
            .background(Color.White)
            .border(1.dp, Color(0xFF9E9E9E)),
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) {
            Image(bmp.asImageBitmap(), contentDescription = stringResource(R.string.viewer_page_number, index + 1), modifier = Modifier.fillMaxSize())
        } else {
            Text(stringResource(R.string.viewer_page_number, index + 1), style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun ZoomButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    androidx.compose.material3.FilledTonalButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
        androidx.compose.material3.Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 6.dp))
    }
}

/** Word files can't be shown inside the app; offer clear actions instead. */
@Composable
private fun WordFilePanel(vm: ViewerViewModel, item: ItemEntity, actions: ViewerActions) {
    val context = LocalContext.current
    val file = vm.fileOf(item) ?: return
    var sending by remember { mutableStateOf(false) }
    var dialogs by remember { mutableStateOf(ViewerDialog.NONE) }
    var cannotOpen by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.material3.Icon(Icons.Filled.Description, contentDescription = null, tint = SeniorColors.Documents, modifier = Modifier.size(96.dp))
        Text(stringResource(R.string.viewer_word_explain), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Text(pagesLabel(item.pageCount.coerceAtLeast(1)), style = MaterialTheme.typography.bodyMedium)
        BigButton(stringResource(R.string.note_send), { sending = true }, icon = Icons.AutoMirrored.Filled.Send, color = SeniorColors.WhatsApp)
        BigOutlinedButton(stringResource(R.string.viewer_open_other_app), {
            if (!ShareHelper.openExternally(context, vm.files, file, item.title)) cannotOpen = true
        }, icon = Icons.Filled.OpenInNew)
        BigOutlinedButton(stringResource(R.string.viewer_save_copy), { dialogs = ViewerDialog.SAVE }, icon = Icons.Filled.Download)
        BigOutlinedButton(stringResource(R.string.action_rename), { dialogs = ViewerDialog.RENAME }, icon = Icons.Filled.Edit)
        BigOutlinedButton(stringResource(R.string.viewer_delete), { dialogs = ViewerDialog.DELETE }, icon = Icons.Filled.Delete, color = SeniorColors.Danger)
    }
    if (sending) SendItemDialog(item, file, vm.files, onDismiss = { sending = false })
    if (cannotOpen) MessageDialog(item.title, stringResource(R.string.viewer_no_word_app), onDismiss = { cannotOpen = false })
    CommonDialogs(vm, item, dialogs, { dialogs = it }, actions)
}

private const val MAX_ZOOM = 4f
private const val A4_RATIO = 210f / 297f
