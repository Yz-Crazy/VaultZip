package com.vaultzip.archive.bridge

import kotlinx.serialization.Serializable

@Serializable
data class NativeArchiveEntryDto(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val compressedSize: Long? = null,
    val uncompressedSize: Long? = null,
    val modifiedAtMillis: Long? = null,
    val encrypted: Boolean = false
)

data class NativeExtractResultDto(
    val extractedCount: Int,
    val failedEntries: List<String>,
    val outputPath: String?
)

data class NativeExtractSingleEntryResultDto(
    val extractedFilePath: String
)
