package com.vaultzip.compress

import com.vaultzip.compress.model.CompressRequest
import com.vaultzip.compress.model.CompressResult

interface CompressRepository {
    suspend fun createZipArchive(
        request: CompressRequest,
        onProgress: ((processedBytes: Long, totalBytes: Long?, currentEntryName: String?) -> Unit)? = null
    ): CompressResult
}
