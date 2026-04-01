package com.vaultzip.storage

import android.net.Uri
import com.vaultzip.ui.volume.VolumeCandidate

interface ArchiveCandidateScanner {
    suspend fun scan(treeUri: Uri): List<VolumeCandidate>
}
