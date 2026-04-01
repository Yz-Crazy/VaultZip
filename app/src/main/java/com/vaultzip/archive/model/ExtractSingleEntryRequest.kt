package com.vaultzip.archive.model

data class ExtractSingleEntryRequest(
    val input: ArchiveInput,
    val entryPath: String,
    val outputDir: OutputTarget,
    val password: CharArray?
)
