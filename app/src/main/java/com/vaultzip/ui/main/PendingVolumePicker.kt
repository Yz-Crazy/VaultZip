package com.vaultzip.ui.main

import android.net.Uri
import com.vaultzip.ui.volume.VolumeCandidate

data class PendingVolumePicker(
    val treeUri: Uri,
    val candidates: List<VolumeCandidate>
)
