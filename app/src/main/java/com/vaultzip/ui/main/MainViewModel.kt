package com.vaultzip.ui.main

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultzip.archive.model.ArchiveEntry
import com.vaultzip.archive.model.ArchiveError
import com.vaultzip.archive.model.ArchiveInput
import com.vaultzip.archive.model.ArchivePartSource
import com.vaultzip.archive.model.ArchiveSelection
import com.vaultzip.archive.model.ExtractRequest
import com.vaultzip.archive.model.ExtractSingleEntryRequest
import com.vaultzip.archive.model.OutputTarget
import com.vaultzip.archive.model.OverwritePolicy
import com.vaultzip.archive.model.VolumeResolveResult
import com.vaultzip.domain.DetectArchiveFormatUseCase
import com.vaultzip.domain.ExtractArchiveUseCase
import com.vaultzip.domain.ExtractSingleEntryUseCase
import com.vaultzip.domain.ListArchiveEntriesUseCase
import com.vaultzip.domain.ResolveArchiveSelectionUseCase
import com.vaultzip.domain.ScanArchiveCandidatesUseCase
import com.vaultzip.preview.PreviewCoordinator
import com.vaultzip.session.PasswordSessionStore
import com.vaultzip.ui.password.PasswordPromptRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val detectArchiveFormatUseCase: DetectArchiveFormatUseCase,
    private val listArchiveEntriesUseCase: ListArchiveEntriesUseCase,
    private val extractArchiveUseCase: ExtractArchiveUseCase,
    private val extractSingleEntryUseCase: ExtractSingleEntryUseCase,
    private val previewCoordinator: PreviewCoordinator,
    private val passwordSessionStore: PasswordSessionStore,
    private val scanArchiveCandidatesUseCase: ScanArchiveCandidatesUseCase,
    private val resolveArchiveSelectionUseCase: ResolveArchiveSelectionUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState

    private val _events = MutableSharedFlow<MainUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<MainUiEvent> = _events

    private var pendingAction: PendingArchiveAction? = null
    private var archiveSessionKey: String? = null

    fun onArchiveSelected(uri: Uri, displayName: String) {
        clearPasswordSession()
        archiveSessionKey = buildArchiveSessionKey(uri)

        viewModelScope.launch {
            setLoading(true)
            val selection = ArchiveSelection.SingleUri(uri = uri, displayName = displayName)
            try {
                val result = resolveArchiveSelectionUseCase(selection)
                handleSelectionResult(uri, null, displayName, result)
            } catch (t: Throwable) {
                setLoading(false)
                _uiState.value = _uiState.value.copy(
                    errorMessage = t.message ?: "分卷解析失败"
                )
            }
        }
    }

    fun onArchivePartsSelected(parts: List<ArchivePartSource.UriPart>, displayName: String) {
        clearPasswordSession()
        archiveSessionKey = buildArchiveSessionKey(parts.first().uri)

        viewModelScope.launch {
            setLoading(true)
            val selection = ArchiveSelection.MultipleUris(parts = parts, displayName = displayName)
            try {
                val result = resolveArchiveSelectionUseCase(selection)
                handleSelectionResult(parts.first().uri, null, displayName, result)
            } catch (t: Throwable) {
                setLoading(false)
                _uiState.value = _uiState.value.copy(
                    errorMessage = t.message ?: "分卷解析失败"
                )
            }
        }
    }

    fun onDirectorySelected(treeUri: Uri) {
        clearPasswordSession()
        Log.d(TAG, "Start scanning tree uri: $treeUri")

        viewModelScope.launch {
            setLoading(true)
            try {
                val candidates = scanArchiveCandidatesUseCase(treeUri)
                setLoading(false)
                if (candidates.isEmpty()) {
                    Log.d(TAG, "No archive candidates found for tree uri: $treeUri")
                    _uiState.value = _uiState.value.copy(
                        selectedTreeUri = treeUri,
                        logMessage = "目录扫描完成，但未发现可用候选文件",
                        errorMessage = "该目录下未发现压缩包或分卷文件",
                        pendingVolumePicker = null
                    )
                    return@launch
                }

                Log.d(TAG, "Found ${candidates.size} archive candidates for tree uri: $treeUri")
                _uiState.value = _uiState.value.copy(
                    selectedTreeUri = treeUri,
                    logMessage = "扫描到 ${candidates.size} 个候选文件",
                    errorMessage = null,
                    pendingVolumePicker = PendingVolumePicker(
                        treeUri = treeUri,
                        candidates = candidates
                    )
                )
            } catch (t: Throwable) {
                Log.e(TAG, "Scan directory failed for tree uri: $treeUri", t)
                setLoading(false)
                _uiState.value = _uiState.value.copy(
                    errorMessage = t.message ?: "扫描目录失败",
                    pendingVolumePicker = null
                )
            }
        }
    }

    fun onVolumePickerPresented() {
        _uiState.value = _uiState.value.copy(pendingVolumePicker = null)
    }

    fun onVolumeCandidateSelected(treeUri: Uri, fileUri: Uri, displayName: String) {
        clearPasswordSession()
        archiveSessionKey = buildArchiveSessionKey(fileUri)
        _uiState.value = _uiState.value.copy(pendingVolumePicker = null)

        viewModelScope.launch {
            setLoading(true)
            val selection = ArchiveSelection.TreeDocument(
                fileUri = fileUri,
                treeUri = treeUri,
                displayName = displayName
            )
            try {
                val result = resolveArchiveSelectionUseCase(selection)
                handleSelectionResult(fileUri, treeUri, displayName, result)
            } catch (t: Throwable) {
                setLoading(false)
                _uiState.value = _uiState.value.copy(
                    errorMessage = t.message ?: "入口卷解析失败"
                )
            }
        }
    }

    fun detectFormat() {
        executeWithPasswordRetry(PendingArchiveAction.DetectFormat)
    }

    fun listEntries() {
        executeWithPasswordRetry(PendingArchiveAction.ListEntries)
    }

    fun extractAll() {
        executeWithPasswordRetry(PendingArchiveAction.ExtractAll)
    }

    fun previewEntry(entry: ArchiveEntry) {
        executeWithPasswordRetry(PendingArchiveAction.PreviewEntry(entry))
    }

    fun extractEntry(entry: ArchiveEntry) {
        executeWithPasswordRetry(PendingArchiveAction.ExtractEntry(entry))
    }

    fun onPasswordConfirmed(password: String) {
        val key = archiveSessionKey ?: return
        passwordSessionStore.put(key, password.toCharArray())
        val action = pendingAction ?: return
        execute(action)
    }

    fun onPasswordCancelled() {
        pendingAction = null
        _events.tryEmit(MainUiEvent.ShowToast("已取消输入密码"))
    }

    fun clearSelection() {
        clearPasswordSession()
        archiveSessionKey = null
        pendingAction = null
        _uiState.value = MainUiState()
    }

    fun currentArchiveInputOrNull(): ArchiveInput? = _uiState.value.resolvedInput

    private fun executeWithPasswordRetry(action: PendingArchiveAction) {
        pendingAction = action
        execute(action)
    }

    private fun execute(action: PendingArchiveAction) {
        when (action) {
            PendingArchiveAction.DetectFormat -> doDetectFormat()
            PendingArchiveAction.ListEntries -> doListEntries()
            PendingArchiveAction.ExtractAll -> doExtractAll()
            is PendingArchiveAction.PreviewEntry -> doPreviewEntry(action.entry)
            is PendingArchiveAction.ExtractEntry -> doExtractEntry(action.entry)
        }
    }

    private fun doDetectFormat() {
        val input = currentArchiveInputOrNull() ?: return
        viewModelScope.launch {
            setLoading(true)
            try {
                val format = detectArchiveFormatUseCase(input)
                setLoading(false)
                _uiState.value = _uiState.value.copy(
                    detectedFormat = format,
                    logMessage = "识别格式成功：${format.name}",
                    errorMessage = null
                )
                pendingAction = null
            } catch (t: Throwable) {
                handleArchiveFailure(t)
            }
        }
    }

    private fun doListEntries() {
        val input = currentArchiveInputOrNull() ?: return
        viewModelScope.launch {
            setLoading(true)
            try {
                val entries = listArchiveEntriesUseCase(input, currentPassword())
                setLoading(false)
                _uiState.value = _uiState.value.copy(
                    entries = entries,
                    logMessage = "列目录成功，共 ${entries.size} 项",
                    errorMessage = null
                )
                pendingAction = null
            } catch (t: Throwable) {
                handleArchiveFailure(t)
            }
        }
    }

    private fun doExtractAll() {
        val input = currentArchiveInputOrNull() ?: return
        if (shouldPromptPasswordForExtractAll()) {
            requestPassword(false)
            return
        }
        val outputTarget = defaultOutputTarget(input)

        viewModelScope.launch {
            setLoading(true)
            try {
                val result = extractArchiveUseCase(
                    request = ExtractRequest(
                        input = input,
                        outputDir = outputTarget,
                        password = currentPassword(),
                        overwritePolicy = OverwritePolicy.REPLACE
                    ),
                    onProgress = { processedBytes, totalBytes ->
                        updateProgress(processedBytes, totalBytes, null)
                    }
                )
                setLoading(false)
                _uiState.value = _uiState.value.copy(
                    logMessage = "整包解压完成：${result.outputPath}",
                    errorMessage = null
                )
                pendingAction = null
            } catch (t: Throwable) {
                handleArchiveFailure(t)
            }
        }
    }

    private fun doPreviewEntry(entry: ArchiveEntry) {
        val input = currentArchiveInputOrNull() ?: return
        if (entry.encrypted && currentPassword() == null) {
            requestPassword(false)
            return
        }
        viewModelScope.launch {
            setLoading(true)
            updateProgress(0L, entry.uncompressedSize, entry.name)
            try {
                val preview = previewCoordinator.prepareFromArchiveEntry(
                    archiveInput = input,
                    entry = entry,
                    password = currentPassword(),
                    onProgress = { processedBytes, totalBytes ->
                        updateProgress(processedBytes, totalBytes, entry.name)
                    }
                )
                setLoading(false)
                pendingAction = null
                _events.tryEmit(MainUiEvent.OpenPreview(preview))
            } catch (t: Throwable) {
                handleArchiveFailure(t)
            }
        }
    }

    private fun doExtractEntry(entry: ArchiveEntry) {
        val input = currentArchiveInputOrNull() ?: return
        if (entry.encrypted && currentPassword() == null) {
            requestPassword(false)
            return
        }
        val outputTarget = defaultOutputTarget(input)

        viewModelScope.launch {
            setLoading(true)
            try {
                val result = extractSingleEntryUseCase(
                    request = ExtractSingleEntryRequest(
                        input = input,
                        entryPath = entry.path,
                        outputDir = outputTarget,
                        password = currentPassword()
                    ),
                    onProgress = { processedBytes, totalBytes ->
                        updateProgress(processedBytes, totalBytes, entry.name)
                    }
                )
                setLoading(false)
                pendingAction = null
                _events.tryEmit(MainUiEvent.ShowToast("已解出到：${result.extractedFilePath}"))
            } catch (t: Throwable) {
                handleArchiveFailure(t)
            }
        }
    }

    private fun shouldPromptPasswordForExtractAll(): Boolean {
        return currentPassword() == null && _uiState.value.entries.any { it.encrypted }
    }

    private fun defaultOutputTarget(input: ArchiveInput): OutputTarget {
        uiState.value.selectedTreeUri?.let {
            return OutputTarget.SafTree(it.toString())
        }
        return when (input) {
            is ArchiveInput.LocalFile -> OutputTarget.LocalDir(
                File(input.absolutePath).parentFile?.absolutePath ?: context.filesDir.absolutePath
            )
            is ArchiveInput.ContentUri -> OutputTarget.LocalDir(context.getExternalFilesDir(null)?.absolutePath ?: context.filesDir.absolutePath)
            is ArchiveInput.VolumeGroup -> {
                val localPart = input.parts.firstOrNull { it is ArchivePartSource.LocalPart }
                if (localPart is ArchivePartSource.LocalPart) {
                    OutputTarget.LocalDir(
                        File(localPart.absolutePath).parentFile?.absolutePath ?: context.filesDir.absolutePath
                    )
                } else {
                    uiState.value.selectedTreeUri?.let { OutputTarget.SafTree(it.toString()) }
                        ?: OutputTarget.LocalDir(context.getExternalFilesDir(null)?.absolutePath ?: context.filesDir.absolutePath)
                }
            }
        }
    }

    private fun handleArchiveFailure(throwable: Throwable) {
        setLoading(false)
        when (throwable) {
            is ArchiveError.EncryptedArchive -> requestPassword(false)
            is ArchiveError.WrongPassword -> {
                clearPasswordSession()
                requestPassword(true)
            }
            else -> {
                pendingAction = null
                _uiState.value = _uiState.value.copy(
                    errorMessage = throwable.toUserMessage()
                )
            }
        }
    }

    private fun requestPassword(showWrongPasswordHint: Boolean) {
        _events.tryEmit(
            MainUiEvent.ShowPasswordPrompt(
                PasswordPromptRequest(showWrongPasswordHint = showWrongPasswordHint)
            )
        )
    }

    private fun currentPassword(): CharArray? {
        val key = archiveSessionKey ?: return null
        return passwordSessionStore.get(key)
    }

    private fun clearPasswordSession() {
        archiveSessionKey?.let(passwordSessionStore::clear)
    }

    private fun buildArchiveSessionKey(uri: Uri): String {
        return "archive_${uri}_${System.currentTimeMillis()}"
    }

    private fun setLoading(value: Boolean) {
        _uiState.value = if (value) {
            _uiState.value.copy(
                loading = true,
                errorMessage = null,
                processedBytes = 0L,
                totalBytes = null,
                currentEntryName = null
            )
        } else {
            _uiState.value.copy(
                loading = false,
                processedBytes = 0L,
                totalBytes = null,
                currentEntryName = null
            )
        }
    }

    private fun updateProgress(
        processedBytes: Long,
        totalBytes: Long?,
        currentEntryName: String?
    ) {
        _uiState.value = _uiState.value.copy(
            processedBytes = processedBytes,
            totalBytes = totalBytes,
            currentEntryName = currentEntryName
        )
    }

    private fun Throwable.toUserMessage(): String {
        return when (this) {
            is ArchiveError.UnsupportedFormat -> "暂不支持该压缩格式"
            is ArchiveError.MissingVolume -> "分卷不完整，请补齐后重试"
            is ArchiveError.WrongPassword -> "密码错误"
            is ArchiveError.EncryptedArchive -> "该压缩包已加密，请输入密码"
            is ArchiveError.CorruptedArchive -> "压缩包已损坏或无法读取"
            is ArchiveError.OutputWriteDenied -> "输出目录不可写"
            is ArchiveError.EntryTooLargeForPreview -> "文件过大，暂不支持直接预览"
            is ArchiveError.Unknown -> rawMessage ?: "操作失败"
            else -> message ?: "操作失败"
        }
    }

    private fun handleSelectionResult(
        selectedUri: Uri,
        treeUri: Uri?,
        displayName: String,
        result: VolumeResolveResult
    ) {
        when (result) {
            is VolumeResolveResult.Single -> {
                _uiState.value = MainUiState(
                    selectedUri = selectedUri,
                    selectedTreeUri = treeUri,
                    selectedName = displayName,
                    resolvedInput = result.input,
                    logMessage = "已选择：$displayName"
                )
            }

            is VolumeResolveResult.MultiVolume -> {
                _uiState.value = MainUiState(
                    selectedUri = selectedUri,
                    selectedTreeUri = treeUri,
                    selectedName = displayName,
                    resolvedInput = result.input,
                    volumeSet = result.volumeSet,
                    logMessage = "已识别分卷：${result.volumeSet.format.name}，共 ${result.volumeSet.parts.size} 卷"
                )
                _events.tryEmit(
                    MainUiEvent.ShowMultiVolumeDetected(
                        formatName = result.volumeSet.format.name,
                        partCount = result.volumeSet.parts.size
                    )
                )
            }

            is VolumeResolveResult.MissingParts -> {
                _uiState.value = MainUiState(
                    selectedUri = selectedUri,
                    selectedTreeUri = treeUri,
                    selectedName = displayName,
                    resolvedInput = null,
                    volumeSet = result.partialSet,
                    errorMessage = "分卷不完整，缺少 ${result.partialSet.missingParts.size} 卷"
                )
                _events.tryEmit(MainUiEvent.ShowMissingParts(result.partialSet.missingParts))
            }
        }
        setLoading(false)
    }

    override fun onCleared() {
        clearPasswordSession()
        super.onCleared()
    }

    companion object {
        private const val TAG = "MainViewModel"
    }
}
