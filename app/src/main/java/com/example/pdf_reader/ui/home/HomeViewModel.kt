package com.example.pdf_reader.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pdf_reader.domain.model.RecentFile
import com.example.pdf_reader.domain.repository.IFileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    fileRepository: IFileRepository,
) : ViewModel() {

    val recentFiles: StateFlow<List<RecentFile>> = fileRepository
        .observeRecentFiles()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )
}
