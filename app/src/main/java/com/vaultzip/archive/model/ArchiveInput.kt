package com.vaultzip.archive.model

import android.net.Uri

sealed interface ArchiveInput {
    data class LocalFile(val absolutePath: String) : ArchiveInput
    data class ContentUri(val uri: Uri) : ArchiveInput
    data class VolumeGroup(val parts: List<ArchivePartSource>) : ArchiveInput
}
