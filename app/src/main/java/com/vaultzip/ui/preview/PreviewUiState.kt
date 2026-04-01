package com.vaultzip.ui.preview

import com.vaultzip.archive.model.PreviewableType

data class PreviewUiState(
    val localPath: String = "",
    val fileName: String = "",
    val type: PreviewableType? = null,
    val deleteOnClose: Boolean = false,
    val ready: Boolean = false,
    val error: String? = null
)
