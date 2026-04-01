package com.vaultzip.compress.model

import android.net.Uri

data class CompressRequest(
    val sources: List<CompressSource>,
    val outputUri: Uri,
    val archiveName: String
)
