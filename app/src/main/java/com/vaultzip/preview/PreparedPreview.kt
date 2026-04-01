package com.vaultzip.preview

import com.vaultzip.archive.model.PreviewableType

data class PreparedPreview(
    val localPath: String,
    val fileName: String,
    val type: PreviewableType,
    val deleteOnClose: Boolean
)
