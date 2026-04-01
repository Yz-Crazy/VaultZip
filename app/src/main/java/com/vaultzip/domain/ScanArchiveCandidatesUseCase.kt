package com.vaultzip.domain

import android.net.Uri
import com.vaultzip.storage.ArchiveCandidateScanner
import com.vaultzip.ui.volume.VolumeCandidate
import javax.inject.Inject

class ScanArchiveCandidatesUseCase @Inject constructor(
    private val scanner: ArchiveCandidateScanner
) {
    suspend operator fun invoke(treeUri: Uri): List<VolumeCandidate> {
        return scanner.scan(treeUri)
    }
}
