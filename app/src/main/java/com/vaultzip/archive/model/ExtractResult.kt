package com.vaultzip.archive.model

data class ExtractResult(
    val extractedCount: Int,
    val failedEntries: List<String>,
    val outputPath: String?
)
