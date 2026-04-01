package com.vaultzip.compress.model

import android.net.Uri

data class CompressSource(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long?
)
