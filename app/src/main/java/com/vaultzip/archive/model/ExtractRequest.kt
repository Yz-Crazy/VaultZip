package com.vaultzip.archive.model

data class ExtractRequest(
    val input: ArchiveInput,
    val outputDir: OutputTarget,
    val password: CharArray?,
    val overwritePolicy: OverwritePolicy,
    val selectedEntries: List<String>? = null
)
