package com.vaultzip.domain

import com.vaultzip.archive.data.ArchiveRepository
import com.vaultzip.archive.model.ExtractRequest
import com.vaultzip.archive.model.ExtractResult
import javax.inject.Inject

class ExtractArchiveUseCase @Inject constructor(
    private val repository: ArchiveRepository
) {
    suspend operator fun invoke(
        request: ExtractRequest,
        onProgress: ((Long, Long?) -> Unit)? = null
    ): ExtractResult {
        return repository.extract(request, onProgress)
    }
}
