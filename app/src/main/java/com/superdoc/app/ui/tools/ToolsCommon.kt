package com.superdoc.app.ui.tools

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.superdoc.app.R
import com.superdoc.app.data.DocumentsRepository
import com.superdoc.app.data.ItemEntity
import com.superdoc.app.pdf.PdfLockedException
import com.superdoc.app.pdf.ThumbnailCache
import com.superdoc.app.ui.components.BigButton
import com.superdoc.app.ui.components.BigOutlinedButton
import com.superdoc.app.ui.components.MessageDialog
import com.superdoc.app.ui.theme.SeniorColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

/** Shared "please wait / done / something went wrong" handling for the page tools. */
abstract class ToolViewModel(val documents: DocumentsRepository, val thumbnails: ThumbnailCache) : ViewModel() {
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<Int?>(null)
    var results by mutableStateOf<List<ItemEntity>>(emptyList())
        private set

    protected fun run(work: suspend () -> List<Long>) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                results = work().mapNotNull { documents.get(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: PdfLockedException) {
                error = R.string.pdf_locked
            } catch (e: OutOfMemoryError) {
                error = R.string.tools_failed_memory
            } catch (e: Exception) {
                error = R.string.tools_failed
            } finally {
                busy = false
            }
        }
    }
}

/** One page drawn as a picture, with its number underneath. */
@Composable
fun PageThumbnail(
    file: File,
    index: Int,
    thumbnails: ThumbnailCache,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    dimmed: Boolean = false,
    overlay: @Composable () -> Unit = {},
) {
    val bitmap by produceState<Bitmap?>(null, file, index) { value = thumbnails.render(file, index, THUMB_WIDTH_PX) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(210f / 297f)
                .background(Color.White, RoundedCornerShape(6.dp))
                .border(
                    if (highlighted) 5.dp else 1.dp,
                    if (highlighted) SeniorColors.Primary else Color(0xFF9E9E9E),
                    RoundedCornerShape(6.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            bitmap?.let {
                Image(
                    it.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    alpha = if (dimmed) 0.35f else 1f,
                    modifier = Modifier.fillMaxSize().padding(if (highlighted) 5.dp else 1.dp),
                )
            }
            overlay()
        }
        Text(
            stringResource(R.string.viewer_page_number, index + 1),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
    }
}

/** Shown when a tool has finished: what was made, and big buttons to open it or go back. */
@Composable
fun ToolDonePanel(
    results: List<ItemEntity>,
    onOpen: (ItemEntity) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    keptMessage: Int = R.string.tools_original_kept,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SeniorColors.SavedGreen, modifier = Modifier.size(96.dp))
        Text(stringResource(R.string.tools_done_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(
            if (results.size == 1) stringResource(R.string.tools_done_one) else stringResource(R.string.tools_done_many, results.size),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Text(stringResource(keptMessage), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        results.forEach { item ->
            BigOutlinedButton(stringResource(R.string.tools_open_named, item.title), { onOpen(item) }, icon = Icons.Filled.Visibility)
        }
        BigButton(stringResource(R.string.export_done), onDone, icon = Icons.Filled.Done, color = SeniorColors.SavedGreen)
    }
}

@Composable
fun ToolErrorDialog(vm: ToolViewModel) {
    val error = vm.error ?: return
    MessageDialog(stringResource(R.string.tools_failed_title), stringResource(error), onDismiss = { vm.error = null })
}

private const val THUMB_WIDTH_PX = 360
