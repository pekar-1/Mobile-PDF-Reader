package com.example.pdf_reader.ui.library

import android.app.Application
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pdf_reader.R
import com.example.pdf_reader.data.LibraryRepository
import com.example.pdf_reader.data.db.DocumentWithTags
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RemovalRequest(
    val hash: String,
    val displayName: String,
    // File can't be opened anymore.
    val unavailable: Boolean,
)

data class OpenDocument(
    val uri: Uri,
    val hash: String,
    val displayName: String,
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = LibraryRepository.get(application)

    private fun cannotOpenMessage(): String =
        getApplication<Application>().getString(R.string.library_error_cannot_open_file)

    val query = MutableStateFlow("")
    private val _opening = MutableStateFlow(false)
    val opening: StateFlow<Boolean> = _opening.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()

    private val _openEvents = MutableSharedFlow<OpenDocument>(extraBufferCapacity = 1)
    val openEvents = _openEvents.asSharedFlow()

    private val _removal = MutableStateFlow<RemovalRequest?>(null)

    val removal: StateFlow<RemovalRequest?> = _removal.asStateFlow()

    val documents: StateFlow<List<DocumentWithTags>> = combine(
        repo.observeDocuments(),
        query,
    ) { all, rawQuery ->
        val needle = rawQuery.trim()
        if (needle.isEmpty()) all
        else all.filter { item ->
            item.document.displayName.contains(needle, ignoreCase = true) ||
                item.tags.any { it.name.contains(needle, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onPicked(uri: Uri) {
        viewModelScope.launch {
            _opening.value = true
            try {
                val document = repo.rememberPicked(uri)
                emitOpen(document.uri, document.hash, document.displayName)
            } catch (e: Exception) {
                _messages.emit(e.message?.takeIf { it.isNotBlank() } ?: cannotOpenMessage())
            } finally {
                _opening.value = false
            }
        }
    }

    fun openExisting(item: DocumentWithTags) {
        val uri = item.document.uri.toUri()
        if (!repo.canRead(uri)) {
            _removal.value = RemovalRequest(item.document.hash, item.document.displayName, unavailable = true)
            return
        }
        viewModelScope.launch {
            repo.touchOpened(item.document.hash)
            emitOpen(item.document.uri, item.document.hash, item.document.displayName)
        }
    }

    fun togglePinned(hash: String, pinned: Boolean) {
        viewModelScope.launch { repo.setPinned(hash, pinned) }
    }

    fun addTag(hash: String, tag: String, colorArgb: Int) {
        viewModelScope.launch { repo.addTag(hash, tag, colorArgb) }
    }

    fun removeTag(hash: String, tag: String) {
        viewModelScope.launch { repo.removeTag(hash, tag) }
    }

    fun requestRemove(item: DocumentWithTags) {
        _removal.value = RemovalRequest(item.document.hash, item.document.displayName, unavailable = false)
    }

    fun dismissRemove() {
        _removal.value = null
    }

    fun confirmRemove() {
        val request = _removal.value ?: return
        _removal.value = null
        viewModelScope.launch { repo.removeDocument(request.hash) }
    }

    private suspend fun emitOpen(uriString: String, hash: String, displayName: String) {
        _openEvents.emit(OpenDocument(uriString.toUri(), hash, displayName))
    }
}
