package com.superdoc.app.ui.note

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.superdoc.app.data.DocumentsRepository
import com.superdoc.app.data.FileStore
import com.superdoc.app.data.ItemEntity
import com.superdoc.app.data.NotesRepository
import com.superdoc.app.di.ApplicationScope
import com.superdoc.app.voice.Speaker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Inject

enum class SaveState { SAVED, SAVING }

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class NoteViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val notes: NotesRepository,
    private val documents: DocumentsRepository,
    val files: FileStore,
    val speaker: Speaker,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    private val noteId = MutableStateFlow<Long?>(null)
    val id: Long? get() = noteId.value

    val item: StateFlow<ItemEntity?> = noteId
        .flatMapLatest { id -> if (id == null) flowOf(null) else notes.observe(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    var text by mutableStateOf(TextFieldValue(""))
        private set
    var saveState by mutableStateOf(SaveState.SAVED)
        private set
    var loaded by mutableStateOf(false)
        private set

    private val pendingSave = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            val argId = savedState.get<String>("id")?.toLongOrNull() ?: 0L
            val existing = savedState.get<Long>(KEY_CREATED) ?: argId
            val resolved = if (existing > 0 && notes.get(existing) != null) existing else {
                notes.create().also { savedState[KEY_CREATED] = it }
            }
            noteId.value = resolved
            text = TextFieldValue(notes.get(resolved)?.body.orEmpty())
            loaded = true
            pendingSave.debounce(AUTOSAVE_DELAY_MS).filterNotNull().collect { save(it) }
        }
    }

    fun onTextChange(value: TextFieldValue) {
        val changed = value.text != text.text
        text = value
        if (changed) {
            saveState = SaveState.SAVING
            pendingSave.value = value.text
        }
    }

    /** Puts spoken words where the cursor is, adding a space when needed. */
    fun insertSpoken(spoken: String) {
        val current = text
        val start = current.selection.min.coerceIn(0, current.text.length)
        val end = current.selection.max.coerceIn(0, current.text.length)
        val before = current.text.substring(0, start)
        val after = current.text.substring(end)
        val separator = if (before.isNotEmpty() && !before.last().isWhitespace()) " " else ""
        val inserted = separator + spoken.trim()
        onTextChange(TextFieldValue(before + inserted + after, TextRange(before.length + inserted.length)))
    }

    /** Replaces the whole text (used by AI and templates); the previous text is kept for Undo. */
    fun replaceAll(newText: String) {
        onTextChange(TextFieldValue(newText, TextRange(newText.length)))
    }

    private suspend fun save(body: String) {
        val id = noteId.value ?: return
        notes.updateBody(id, body)
        if (pendingSave.value == body) saveState = SaveState.SAVED
    }

    /** Saves right now, e.g. before exporting or sending. */
    suspend fun saveNow() {
        save(text.text)
    }

    fun rename(title: String) = viewModelScope.launch { noteId.value?.let { notes.rename(it, title) } }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        saveNow()
        noteId.value?.let { documents.moveToRecycleBin(it) }
        onDone()
    }

    override fun onCleared() {
        speaker.stop()
        val id = noteId.value ?: return
        val body = text.text
        appScope.launch {
            notes.updateBody(id, body)
            notes.discardIfEmpty(id)
        }
    }

    private companion object {
        const val AUTOSAVE_DELAY_MS = 500L
        const val KEY_CREATED = "created_id"
    }
}
