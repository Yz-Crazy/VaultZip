package com.vaultzip.ui.compress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultzip.compress.model.CompressRequest
import com.vaultzip.compress.model.CompressSource
import com.vaultzip.domain.CreateZipArchiveUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class CompressViewModel @Inject constructor(
    private val createZipArchiveUseCase: CreateZipArchiveUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CompressUiState(statusMessage = "选择文件后即可创建 ZIP 压缩包")
    )
    val uiState: StateFlow<CompressUiState> = _uiState

    fun onSourcesSelected(sources: List<CompressSource>) {
        val current = _uiState.value
        _uiState.value = current.copy(
            selectedSources = sources,
            statusMessage = if (sources.isEmpty()) {
                "选择文件后即可创建 ZIP 压缩包"
            } else if (current.outputUri != null) {
                "文件和输出位置都已准备好，可以开始压缩"
            } else {
                "已选择 ${sources.size} 个文件，接下来请选择输出位置"
            },
            errorMessage = null,
            processedBytes = 0L,
            totalBytes = null,
            currentEntryName = null
        )
    }

    fun onOutputSelected(uri: android.net.Uri, displayName: String) {
        _uiState.value = _uiState.value.copy(
            outputUri = uri,
            outputDisplayName = displayName,
            statusMessage = if (_uiState.value.selectedSources.isEmpty()) {
                "输出文件已准备好，请先选择要压缩的文件"
            } else {
                "输出文件已准备好，可以开始压缩"
            },
            errorMessage = null
        )
    }

    fun clearSelection() {
        _uiState.value = CompressUiState(statusMessage = "选择文件后即可创建 ZIP 压缩包")
    }

    fun suggestedArchiveName(): String {
        val sources = _uiState.value.selectedSources
        if (sources.isEmpty()) return "vaultzip-${System.currentTimeMillis()}.zip"
        if (sources.size == 1) {
            val name = sources.first().displayName.ifBlank { "archive" }
            return name.substringBeforeLast('.', name) + ".zip"
        }
        return "vaultzip-${sources.size}-files.zip"
    }

    fun createArchive() {
        val state = _uiState.value
        if (state.loading) return
        if (state.selectedSources.isEmpty()) {
            _uiState.value = state.copy(errorMessage = "请先选择要压缩的文件")
            return
        }
        val outputUri = state.outputUri
        if (outputUri == null) {
            _uiState.value = state.copy(errorMessage = "请先选择输出位置")
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(
                loading = true,
                errorMessage = null,
                statusMessage = "正在创建 ZIP 压缩包",
                processedBytes = 0L,
                totalBytes = state.selectedSources.sumOf { it.sizeBytes ?: 0L }.takeIf { total ->
                    state.selectedSources.all { it.sizeBytes != null } && total > 0L
                },
                currentEntryName = null
            )
            try {
                val result = createZipArchiveUseCase(
                    request = CompressRequest(
                        sources = state.selectedSources,
                        outputUri = outputUri,
                        archiveName = state.outputDisplayName.ifBlank { suggestedArchiveName() }
                    ),
                    onProgress = { processedBytes, totalBytes, currentEntryName ->
                        _uiState.value = _uiState.value.copy(
                            processedBytes = processedBytes,
                            totalBytes = totalBytes,
                            currentEntryName = currentEntryName
                        )
                    }
                )
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    statusMessage = "压缩完成：${result.archiveName}，共 ${result.entryCount} 项",
                    errorMessage = null,
                    processedBytes = result.totalBytes,
                    totalBytes = result.totalBytes.takeIf { it > 0L },
                    currentEntryName = null
                )
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    errorMessage = t.message ?: "压缩失败",
                    statusMessage = "压缩未完成",
                    currentEntryName = null
                )
            }
        }
    }
}
