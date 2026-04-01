package com.vaultzip.domain

import com.vaultzip.archive.data.ArchiveRepository
import com.vaultzip.archive.model.ArchiveEntry
import com.vaultzip.archive.model.ArchiveInput
import javax.inject.Inject

class ListArchiveEntriesUseCase @Inject constructor(
    private val repository: ArchiveRepository
) {
    suspend operator fun invoke(
        input: ArchiveInput,
        password: CharArray? = null
    ): List<ArchiveEntry> {
        return repository.listEntries(input, password)
    }
}
