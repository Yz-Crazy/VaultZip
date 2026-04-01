package com.vaultzip.archive.model

sealed interface VolumeResolveResult {
    data class Single(
        val input: ArchiveInput,
        val displayName: String
    ) : VolumeResolveResult

    data class MultiVolume(
        val input: ArchiveInput.VolumeGroup,
        val volumeSet: VolumeSet,
        val displayName: String
    ) : VolumeResolveResult

    data class MissingParts(
        val partialSet: VolumeSet,
        val displayName: String
    ) : VolumeResolveResult
}
