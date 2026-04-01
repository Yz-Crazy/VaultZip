package com.vaultzip.domain

import com.vaultzip.compress.CompressRepository
import com.vaultzip.compress.model.CompressRequest
import com.vaultzip.compress.model.CompressResult
import javax.inject.Inject

class CreateZipArchiveUseCase @Inject constructor(
    private val repository: CompressRepository
) {
    suspend operator fun invoke(
        request: CompressRequest,
        onProgress: ((processedBytes: Long, totalBytes: Long?, currentEntryName: String?) -> Unit)? = null
    ): CompressResult {
        return repository.createZipArchive(request, onProgress)
    }
}
