package com.example.pdf_reader.ui.reader

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.pdf_reader.data.LibraryRepository
import com.example.pdf_reader.data.ThumbnailStore
import com.example.pdf_reader.pdfium.PdfDocumentManager
import com.example.pdf_reader.ui.pdf.search.SearchState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream

internal sealed interface ReaderState {
    data object Loading : ReaderState
    data class Open(val manager: PdfDocumentManager, val search: SearchState) : ReaderState
    data class Failed(val error: Throwable) : ReaderState
}

// Keeps the open document across rotation.
internal class ReaderViewModel(
    application: Application,
    private val uri: Uri,
    private val hash: String,
) : AndroidViewModel(application) {

    private val repository = LibraryRepository.get(application)

    private val _state = MutableStateFlow<ReaderState>(ReaderState.Loading)
    val state: StateFlow<ReaderState> = _state.asStateFlow()

    private val _pageIndex = MutableStateFlow(0)
    val pageIndex: StateFlow<Int> = _pageIndex.asStateFlow()

    init {
        viewModelScope.launch { open() }
    }

    fun onPageChanged(page: Int) {
        _pageIndex.value = page
    }

    private suspend fun open() {
        val manager = try {
            PdfDocumentManager.open(getApplication(), uri)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = ReaderState.Failed(e)
            return
        }
        val count = manager.pageCount.value
        val stored = try {
            repository.get(hash)
        } catch (e: CancellationException) {
            manager.close() // Cleared before the state held it.
            throw e
        }
        _pageIndex.value = stored?.lastPageIndex?.coerceIn(0, (count - 1).coerceAtLeast(0)) ?: 0

        val search = SearchState(manager, viewModelScope)
        viewModelScope.launch { search.followDocumentChanges() }
        _state.value = ReaderState.Open(manager, search)

        viewModelScope.launch {
            _pageIndex.drop(1).debounce(SAVE_DEBOUNCE_MS).collect { repository.updateLastPage(hash, it) }
        }
        repository.updatePageCount(hash, count)
        if (count > 0) saveThumbnail(manager, count)
    }

    private suspend fun saveThumbnail(manager: PdfDocumentManager, count: Int) {
        try {
            val image = manager.renderPage(0, ThumbnailStore.renderWidthPx)
            val file = ThumbnailStore.file(getApplication(), hash)
            withContext(Dispatchers.IO) {
                FileOutputStream(file).use { image.asAndroidBitmap().compress(Bitmap.CompressFormat.JPEG, 82, it) }
            }
            repository.updatePreview(hash, count)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Thumbnail failed", e)
        }
    }

    override fun onCleared() {
        (_state.value as? ReaderState.Open)?.let {
            repository.saveLastPageAsync(hash, _pageIndex.value)
            it.manager.close()
        }
    }

    companion object {
        private const val TAG = "ReaderViewModel"
        private const val SAVE_DEBOUNCE_MS = 400L

        fun factory(uri: Uri, hash: String): ViewModelProvider.Factory = viewModelFactory {
            initializer { ReaderViewModel(this[APPLICATION_KEY]!!, uri, hash) }
        }
    }
}
