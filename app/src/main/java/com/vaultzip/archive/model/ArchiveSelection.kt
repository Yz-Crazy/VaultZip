package com.vaultzip.archive.model

import android.net.Uri

sealed interface ArchiveSelection {
    data class SingleUri(
        val uri: Uri,
        val displayName: String
    ) : ArchiveSelection

    data class MultipleUris(
        val parts: List<ArchivePartSource.UriPart>,
        val displayName: String
    ) : ArchiveSelection

    data class LocalFile(
        val absolutePath: String,
        val displayName: String
    ) : ArchiveSelection

    data class TreeDocument(
        val fileUri: Uri,
        val treeUri: Uri,
        val displayName: String
    ) : ArchiveSelection
}
