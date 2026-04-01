package com.vaultzip.domain

import com.vaultzip.archive.impl.VolumeDetector
import com.vaultzip.archive.model.ArchiveInput
import com.vaultzip.archive.model.ArchivePartSource
import com.vaultzip.archive.model.ArchiveSelection
import com.vaultzip.archive.model.VolumeResolveResult
import com.vaultzip.storage.ArchiveSiblingScanner
import javax.inject.Inject

class ResolveArchiveSelectionUseCase @Inject constructor(
    private val siblingScanner: ArchiveSiblingScanner,
    private val volumeDetector: VolumeDetector
) {

    suspend operator fun invoke(selection: ArchiveSelection): VolumeResolveResult {
        val displayName = when (selection) {
            is ArchiveSelection.SingleUri -> selection.displayName
            is ArchiveSelection.MultipleUris -> selection.displayName
            is ArchiveSelection.LocalFile -> selection.displayName
            is ArchiveSelection.TreeDocument -> selection.displayName
        }

        val directInput = when (selection) {
            is ArchiveSelection.SingleUri -> ArchiveInput.ContentUri(selection.uri)
            is ArchiveSelection.MultipleUris -> ArchiveInput.ContentUri(selection.parts.first().uri)
            is ArchiveSelection.LocalFile -> ArchiveInput.LocalFile(selection.absolutePath)
            is ArchiveSelection.TreeDocument -> ArchiveInput.ContentUri(selection.fileUri)
        }

        val siblings = siblingScanner.scanSiblings(selection)
        if (siblings.isEmpty()) {
            return VolumeResolveResult.Single(
                input = directInput,
                displayName = displayName
            )
        }

        val volumeSet = volumeDetector.detect(
            selectedName = displayName,
            siblings = ensureSelectedIncluded(selection, siblings)
        ) ?: return VolumeResolveResult.Single(
            input = directInput,
            displayName = displayName
        )

        return if (volumeSet.isComplete) {
            VolumeResolveResult.MultiVolume(
                input = ArchiveInput.VolumeGroup(volumeSet.parts.map { it.source }),
                volumeSet = volumeSet,
                displayName = displayName
            )
        } else {
            VolumeResolveResult.MissingParts(
                partialSet = volumeSet,
                displayName = displayName
            )
        }
    }

    private fun ensureSelectedIncluded(
        selection: ArchiveSelection,
        siblings: List<ArchivePartSource>
    ): List<ArchivePartSource> {
        val selectedName = when (selection) {
            is ArchiveSelection.SingleUri -> selection.displayName
            is ArchiveSelection.MultipleUris -> selection.displayName
            is ArchiveSelection.LocalFile -> selection.displayName
            is ArchiveSelection.TreeDocument -> selection.displayName
        }

        if (siblings.any { it.name == selectedName }) return siblings

        val selected = when (selection) {
            is ArchiveSelection.SingleUri -> listOf(
                ArchivePartSource.UriPart(
                    name = selection.displayName,
                    uri = selection.uri
                )
            )

            is ArchiveSelection.MultipleUris -> selection.parts

            is ArchiveSelection.LocalFile -> listOf(
                ArchivePartSource.LocalPart(
                    name = selection.displayName,
                    absolutePath = selection.absolutePath
                )
            )

            is ArchiveSelection.TreeDocument -> listOf(
                ArchivePartSource.UriPart(
                    name = selection.displayName,
                    uri = selection.fileUri
                )
            )
        }

        return siblings + selected.filterNot { selectedPart ->
            siblings.any { sibling -> sibling.name == selectedPart.name }
        }
    }
}
