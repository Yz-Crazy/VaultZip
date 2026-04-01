package com.vaultzip.compress.model

import android.net.Uri

data class CompressResult(
    val outputUri: Uri,
    val archiveName: String,
    val entryCount: Int,
    val totalBytes: Long
)
