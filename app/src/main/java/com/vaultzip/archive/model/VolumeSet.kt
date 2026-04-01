package com.vaultzip.archive.model

data class VolumeSet(
    val rootName: String,
    val format: ArchiveFormat,
    val parts: List<VolumePart>,
    val isComplete: Boolean,
    val missingParts: List<String>
)

data class VolumePart(
    val index: Int,
    val fileName: String,
    val source: ArchivePartSource
)
