package com.vaultzip.archive.model

data class ArchiveEntry(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val compressedSize: Long?,
    val uncompressedSize: Long?,
    val modifiedAtMillis: Long?,
    val encrypted: Boolean,
    val previewableType: PreviewableType
)
