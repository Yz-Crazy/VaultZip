package com.vaultzip.archive.data

import com.vaultzip.archive.model.*

interface ArchiveRepository {
    suspend fun detectFormat(input: ArchiveInput): ArchiveFormat

    suspend fun listEntries(
        input: ArchiveInput,
        password: CharArray? = null
    ): List<ArchiveEntry>

    suspend fun extract(
        request: ExtractRequest,
        onProgress: ((processedBytes: Long, totalBytes: Long?) -> Unit)? = null
    ): ExtractResult

    suspend fun extractEntry(
        request: ExtractSingleEntryRequest,
        onProgress: ((processedBytes: Long, totalBytes: Long?) -> Unit)? = null
    ): ExtractSingleEntryResult
}
