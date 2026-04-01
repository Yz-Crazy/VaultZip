package com.vaultzip.archive.model

sealed class ArchiveError : Throwable() {
    data object UnsupportedFormat : ArchiveError()
    data object MissingVolume : ArchiveError()
    data object WrongPassword : ArchiveError()
    data object EncryptedArchive : ArchiveError()
    data object CorruptedArchive : ArchiveError()
    data object OutputWriteDenied : ArchiveError()
    data object EntryTooLargeForPreview : ArchiveError()
    data class Unknown(val rawMessage: String? = null) : ArchiveError()
}
