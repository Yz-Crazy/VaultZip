package com.vaultzip.domain

import com.vaultzip.archive.data.ArchiveRepository
import com.vaultzip.archive.model.ArchiveFormat
import com.vaultzip.archive.model.ArchiveInput
import javax.inject.Inject

class DetectArchiveFormatUseCase @Inject constructor(
    private val repository: ArchiveRepository
) {
    suspend operator fun invoke(input: ArchiveInput): ArchiveFormat {
        return repository.detectFormat(input)
    }
}
