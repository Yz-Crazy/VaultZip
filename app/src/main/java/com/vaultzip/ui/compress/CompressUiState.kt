package com.vaultzip.ui.compress

import android.net.Uri
import com.vaultzip.compress.model.CompressSource

data class CompressUiState(
    val selectedSources: List<CompressSource> = emptyList(),
    val outputUri: Uri? = null,
    val outputDisplayName: String = "",
    val loading: Boolean = false,
    val statusMessage: String = "",
    val errorMessage: String? = null,
    val processedBytes: Long = 0L,
    val totalBytes: Long? = null,
    val currentEntryName: String? = null
)
