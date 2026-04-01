package com.vaultzip.archive.model

import android.net.Uri

sealed interface ArchivePartSource {
    val name: String

    data class LocalPart(
        override val name: String,
        val absolutePath: String
    ) : ArchivePartSource

    data class UriPart(
        override val name: String,
        val uri: Uri
    ) : ArchivePartSource
}
