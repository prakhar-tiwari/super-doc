package com.superdoc.app.ui.viewer

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.superdoc.app.R
import com.superdoc.app.data.DocumentsRepository
import com.superdoc.app.data.FileStore
import com.superdoc.app.data.ItemEntity
import com.superdoc.app.pdf.ThumbnailCache
import com.superdoc.app.scan.TextReader
import com.superdoc.app.share.SaveToPhone
import com.superdoc.app.share.ShareHelper
import com.superdoc.app.util.AppLocale
import com.superdoc.app.voice.Speaker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ViewerViewModel @Inject constructor(
    savedState: SavedStateHandle,
    @ApplicationContext private val context: Context,
    val documents: DocumentsRepository,
    val thumbnails: ThumbnailCache,
    val files: FileStore,
    val speaker: Speaker,
    private val reader: TextReader,
) : ViewModel() {
    val id: Long = savedState.get<String>("id")?.toLongOrNull() ?: 0L

    val item: StateFlow<ItemEntity?> = documents.observe(id)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    var busyMessage by mutableStateOf<Int?>(null)
        private set
    var message by mutableStateOf<String?>(null)

    fun fileOf(item: ItemEntity): File? = documents.fileOf(item)

    /** Reads the text printed on a page out loud (OCR, then text-to-speech). */
    fun readPage(item: ItemEntity, pageIndex: Int, onNoVoice: () -> Unit) {
        val file = fileOf(item) ?: return
        busyMessage = R.string.viewer_reading_page
        viewModelScope.launch {
            val bitmap = thumbnails.render(file, pageIndex, 1600)
            val text = bitmap?.let { reader.read(it) }?.text.orEmpty()
            busyMessage = null
            if (text.isBlank()) {
                message = AppLocale.string(context, R.string.viewer_no_text_found)
            } else if (speaker.speak(text) == Speaker.Result.LANGUAGE_MISSING) {
                onNoVoice()
            }
        }
    }

    fun saveToPhone(item: ItemEntity) {
        val file = fileOf(item) ?: return
        busyMessage = R.string.viewer_saving_copy
        viewModelScope.launch {
            val path = SaveToPhone.save(context, file, item.title, ShareHelper.mimeFor(file.extension))
            busyMessage = null
            message = if (path != null) {
                AppLocale.string(context, R.string.viewer_saved_copy, path)
            } else {
                AppLocale.string(context, R.string.viewer_save_copy_failed)
            }
        }
    }

    fun rename(title: String) = viewModelScope.launch { documents.rename(id, title) }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        documents.moveToRecycleBin(id)
        onDone()
    }

    override fun onCleared() {
        speaker.stop()
    }
}
