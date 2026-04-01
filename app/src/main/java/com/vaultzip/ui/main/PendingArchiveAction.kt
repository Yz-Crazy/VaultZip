package com.vaultzip.ui.main

import com.vaultzip.archive.model.ArchiveEntry

sealed interface PendingArchiveAction {
    data object DetectFormat : PendingArchiveAction
    data object ListEntries : PendingArchiveAction
    data object ExtractAll : PendingArchiveAction
    data class PreviewEntry(val entry: ArchiveEntry) : PendingArchiveAction
    data class ExtractEntry(val entry: ArchiveEntry) : PendingArchiveAction
}
