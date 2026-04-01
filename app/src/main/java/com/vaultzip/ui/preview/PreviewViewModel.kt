package com.vaultzip.ui.preview

import androidx.lifecycle.ViewModel
import com.vaultzip.archive.model.PreviewableType
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class PreviewViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(PreviewUiState())
    val uiState: StateFlow<PreviewUiState> = _uiState

    fun bind(
        localPath: String,
        fileName: String,
        type: PreviewableType,
        deleteOnClose: Boolean
    ) {
        _uiState.value = PreviewUiState(
            localPath = localPath,
            fileName = fileName,
            type = type,
            deleteOnClose = deleteOnClose,
            ready = true
        )
    }

    fun bindError(message: String) {
        _uiState.value = PreviewUiState(error = message, ready = false)
    }

    fun cleanupIfNeeded() {
        val state = _uiState.value
        if (!state.deleteOnClose) return
        runCatching { File(state.localPath).delete() }
    }

    override fun onCleared() {
        cleanupIfNeeded()
        super.onCleared()
    }
}
