package com.vaultzip.ui.main

import android.net.Uri
import com.vaultzip.archive.model.ArchiveEntry
import com.vaultzip.archive.model.ArchiveFormat
import com.vaultzip.archive.model.ArchiveInput
import com.vaultzip.archive.model.VolumeSet

data class MainUiState(
    val selectedUri: Uri? = null,
    val selectedTreeUri: Uri? = null,
    val selectedName: String = "",
    val resolvedInput: ArchiveInput? = null,
    val detectedFormat: ArchiveFormat? = null,
    val volumeSet: VolumeSet? = null,
    val entries: List<ArchiveEntry> = emptyList(),
    val loading: Boolean = false,
    val logMessage: String = "",
    val errorMessage: String? = null,
    val processedBytes: Long = 0L,
    val totalBytes: Long? = null,
    val currentEntryName: String? = null,
    val pendingVolumePicker: PendingVolumePicker? = null
)
