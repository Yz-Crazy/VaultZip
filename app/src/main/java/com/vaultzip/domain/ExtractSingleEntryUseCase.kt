package com.vaultzip.domain

import com.vaultzip.archive.data.ArchiveRepository
import com.vaultzip.archive.model.ExtractSingleEntryRequest
import com.vaultzip.archive.model.ExtractSingleEntryResult
import javax.inject.Inject

class ExtractSingleEntryUseCase @Inject constructor(
    private val repository: ArchiveRepository
) {
    suspend operator fun invoke(
        request: ExtractSingleEntryRequest,
        onProgress: ((Long, Long?) -> Unit)? = null
    ): ExtractSingleEntryResult {
        return repository.extractEntry(request, onProgress)
    }
}
