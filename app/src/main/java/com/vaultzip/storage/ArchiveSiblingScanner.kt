package com.vaultzip.storage

import com.vaultzip.archive.model.ArchivePartSource
import com.vaultzip.archive.model.ArchiveSelection

interface ArchiveSiblingScanner {
    suspend fun scanSiblings(selection: ArchiveSelection): List<ArchivePartSource>
}
