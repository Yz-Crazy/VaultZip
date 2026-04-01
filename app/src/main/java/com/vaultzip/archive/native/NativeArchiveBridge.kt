package com.vaultzip.archive.bridge

import com.vaultzip.archive.model.*
import java.io.File

data class ResolvedArchiveSource(
    val primaryPath: String,
    val partPaths: List<String> = listOf(primaryPath)
)

interface NativeArchiveBridge {
    suspend fun detectFormat(source: ResolvedArchiveSource): ArchiveFormat?

    suspend fun listEntries(
        source: ResolvedArchiveSource,
        password: CharArray?
    ): List<ArchiveEntry>

    suspend fun extract(
        source: ResolvedArchiveSource,
        request: ExtractRequest,
        localOutputDir: File,
        onProgress: ((processedBytes: Long, totalBytes: Long?) -> Unit)?
    ): ExtractResult

    suspend fun extractEntry(
        source: ResolvedArchiveSource,
        request: ExtractSingleEntryRequest,
        localOutputDir: File,
        onProgress: ((processedBytes: Long, totalBytes: Long?) -> Unit)?
    ): ExtractSingleEntryResult
}
